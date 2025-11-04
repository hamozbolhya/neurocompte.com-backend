package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacioli.core.DTO.EcrituresDTO2;
import com.pacioli.core.DTO.PieceDTO;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.PieceRepository;
import com.pacioli.core.services.PieceService;
import com.pacioli.core.batches.clients.AIServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.pacioli.core.services.AI.services.BankApiService;
import com.pacioli.core.DTO.AI.BankStatementGetResponse;

@Component
@Slf4j
public class AIResponseProcessor {
    private static final int MAX_RETRIES = 4;
    private static final long RETRY_DELAY_MS = 30000;

    @Autowired
    private PieceRepository pieceRepository;
    @Autowired
    private PieceService pieceService;
    @Autowired
    private AIServiceClient aiServiceClient;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PieceValidator pieceValidator;
    @Autowired
    private DTOBuilder dtoBuilder;
    @Autowired
    private CurrencyConverter currencyConverter;
    @Autowired
    private BankApiService bankApiService;


    public void processPieceWithRetry(Piece piece, int attempt) throws InterruptedException {
        if (attempt > MAX_RETRIES) {
            rejectPiece(piece, "Failed after " + MAX_RETRIES + " AI attempts");
            return;
        }

        updatePieceStatus(piece, PieceStatus.PROCESSING);

        try {
            // ✅ NEW: Check if this is a bank statement and call appropriate service
            boolean isBankStatement = "Relevés bancaires".equals(piece.getType());
            log.info("📊 Detected piece type: {} -> Is bank statement: {}", piece.getType(), isBankStatement);

            JsonNode aiResponse;

            if (isBankStatement) {
                log.info("🏦 Calling BANK SERVICE GET API for file: {}", piece.getFilename());
                aiResponse = callBankService(piece);

                // Use bank-specific validator
                if (!pieceValidator.isValidBankAIResponse(aiResponse)) {
                    handleInvalidResponse(piece, attempt, aiResponse.toString());
                    return;
                }
            } else {
                log.info("📄 Calling NORMAL AI SERVICE for file: {}", piece.getFilename());
                aiResponse = aiServiceClient.callAIService(piece.getFilename());

                // Use normal validator
                if (!pieceValidator.isValidAIResponse(aiResponse)) {
                    handleInvalidResponse(piece, attempt, aiResponse.toString());
                    return;
                }
            }

            JsonNode outputText = aiResponse.get("outputText");
            processValidAIResponse(piece, outputText);

        } catch (Exception e) {
            handleProcessingError(piece, attempt, e);
        }
    }

    private JsonNode callBankService(Piece piece) {
        try {
            // Extract fileId from filename (remove extension)
            String filename = piece.getFilename();
            String fileId = filename.substring(0, filename.lastIndexOf('.'));

            log.info("🏦 Getting bank statement result for fileId: {}", fileId);

            // Call the bank API GET service
            BankStatementGetResponse bankResponse = bankApiService.getBankStatementResult(fileId);

            if (!bankResponse.isSuccess()) {
                throw new RuntimeException("Bank API GET failed: " + bankResponse.getMessage());
            }

            // Parse the JSON response from bank API
            String jsonResponse = bankResponse.getJsonResponse();
            log.info("✅ Bank API response received, length: {}", jsonResponse.length());
            log.debug("🏦 Raw bank response: {}", jsonResponse);

            // The bank API returns the exact format we need, just parse it
            JsonNode parsedResponse = objectMapper.readTree(jsonResponse);
            log.info("🏦 Parsed bank response structure: {}", parsedResponse.getNodeType());

            return parsedResponse;

        } catch (Exception e) {
            log.error("❌ Bank API GET call failed: {}", e.getMessage());
            throw new RuntimeException("Bank service call failed: " + e.getMessage(), e);
        }
    }


    private void handleInvalidResponse(Piece piece, int attempt, String jsonResponse) throws InterruptedException {
        if (attempt < MAX_RETRIES) {
            log.warn("🔄 Retrying piece {} due to invalid AI response (attempt {}/{})",
                    piece.getId(), attempt, MAX_RETRIES);
            Thread.sleep(RETRY_DELAY_MS);
            processPieceWithRetry(piece, attempt + 1);
        } else {
            log.error("❌ File rejected - invalid AI response after all attempts: {}", jsonResponse);
            rejectPiece(piece, "Invalid AI response after all attempts");
        }
    }

    private void handleProcessingError(Piece piece, int attempt, Exception e) throws InterruptedException {
        if (attempt < MAX_RETRIES) {
            log.warn("🔄 Retrying piece {} after error (attempt {}/{}): {}",
                    piece.getId(), attempt, MAX_RETRIES, e.getMessage());
            Thread.sleep(RETRY_DELAY_MS);
            processPieceWithRetry(piece, attempt + 1);
        } else {
            rejectPiece(piece, "Failed after all attempts: " + e.getMessage());
        }
    }

    private void processValidAIResponse(Piece piece, JsonNode aiResponse) throws JsonProcessingException {
        try {
            // ✅ STEP 1: Extract and save AI data to Piece entity FIRST
            extractAndSaveAIData(piece, aiResponse);

            // ✅ STEP 2: Reload piece to ensure we have latest data
            Piece refreshedPiece = pieceRepository.findById(piece.getId())
                    .orElseThrow(() -> new RuntimeException("Piece not found after AI data extraction"));

            // ✅ STEP 3: Process DTO and save ecritures
            PieceDTO pieceDTO = dtoBuilder.buildPieceDTO(refreshedPiece, aiResponse);

            // ✅ DEBUG: Check if lines are populated
            if (pieceDTO.getEcritures() != null && !pieceDTO.getEcritures().isEmpty()) {
                EcrituresDTO2 firstEcriture = pieceDTO.getEcritures().get(0);
                log.info("🔍 DTO Built - Ecriture has {} lines",
                        firstEcriture.getLines() != null ? firstEcriture.getLines().size() : 0);
            } else {
                log.warn("⚠️ No ecritures in built DTO");
            }

            JsonNode convertedResponse = dtoBuilder.createConvertedResponseNode(pieceDTO, aiResponse);

            // ✅ STEP 4: Save to database
            pieceService.saveEcrituresAndFacture(
                    refreshedPiece.getId(),
                    refreshedPiece.getDossier().getId(),
                    objectMapper.writeValueAsString(pieceDTO),
                    convertedResponse
            );

            // ✅ STEP 5: Update status to PROCESSED
            updatePieceStatus(refreshedPiece, PieceStatus.PROCESSED);

            log.info("✅ Successfully processed piece {} with AI data", refreshedPiece.getId());

        } catch (Exception e) {
            log.error("❌ Error in processValidAIResponse for piece {}: {}", piece.getId(), e.getMessage(), e);
            throw e;
        }
    }


    private void extractAndSaveAIData(Piece piece, JsonNode aiResponse) throws JsonProcessingException {
        try {
            String responseText = aiResponse.asText();
            JsonNode parsedJson = objectMapper.readTree(responseText);

            // ✅ FIX: Use the same findEcrituresNode logic as DTOBuilder
            JsonNode ecrituresNode = findEcrituresNodeForAI(parsedJson);

            if (ecrituresNode != null && ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                JsonNode firstEntry = ecrituresNode.get(0);

                // Extract AI amount
                double originalAmount = calculateLargestAmount(ecrituresNode);
                piece.setAiAmount(originalAmount);

                // ✅ FIX: Extract AI currency properly from the entry
                String invoiceCurrencyCode = extractAndNormalizeCurrency(firstEntry);
                piece.setAiCurrency(invoiceCurrencyCode);

                // ✅ CRITICAL: Save the piece with AI data
                pieceRepository.save(piece);

                log.info("✅ Saved AI data to piece {}: amount={}, currency={}",
                        piece.getId(), originalAmount, invoiceCurrencyCode);
            } else {
                log.warn("⚠️ No valid ecritures found, using defaults");
                piece.setAiAmount(0.0);
                piece.setAiCurrency("USD");
                pieceRepository.save(piece);
            }
        } catch (Exception e) {
            log.error("❌ Failed to extract AI data: {}", e.getMessage());
            // Set defaults instead of throwing exception
            piece.setAiAmount(0.0);
            piece.setAiCurrency("USD");
            pieceRepository.save(piece);
            log.info("✅ Set default AI data for piece {} due to extraction error", piece.getId());
        }
    }


    private JsonNode findEcrituresNodeForAI(JsonNode parsedJson) {
        // First check for normal format
        if (parsedJson.has("ecritures")) return parsedJson.get("ecritures");
        if (parsedJson.has("Ecritures")) {
            JsonNode ecrituresNode = parsedJson.get("Ecritures");

            // Check if it's bank statement format (nested arrays with entries)
            if (ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                JsonNode firstItem = ecrituresNode.get(0);
                if (firstItem.isArray() && firstItem.size() > 0) {
                    JsonNode entriesNode = firstItem.get(0);
                    if (entriesNode.has("entries")) {
                        log.info("🏦 Detected bank statement format with entries array");
                        return entriesNode.get("entries");
                    }
                }
            }

            // If not bank format, return as-is (normal format)
            log.info("📄 Detected normal Ecritures format");
            return ecrituresNode;
        }
        return null;
    }

    private Double calculateLargestAmount(JsonNode ecritures) {
        double maxAmount = 0.0;
        for (JsonNode entry : ecritures) {
            // ✅ FIX: Check if fields exist before accessing them
            double debit = parseDoubleSafely(entry, "DebitAmt");
            double credit = parseDoubleSafely(entry, "CreditAmt");
            maxAmount = Math.max(maxAmount, Math.max(debit, credit));
        }
        return maxAmount;
    }

    // Helper method for safe double parsing
    private double parseDoubleSafely(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            try {
                String value = node.get(fieldName).asText();
                // Handle comma decimal separator
                value = value.replace(',', '.');
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                log.trace("Error parsing {} value: {}", fieldName, node.get(fieldName).asText());
                return 0.0;
            }
        }
        return 0.0;
    }

    private String extractAndNormalizeCurrency(JsonNode entry) {
        // ✅ FIX: Check if Devise field exists and is not null
        if (entry.has("Devise") && !entry.get("Devise").isNull() && !entry.get("Devise").asText().isEmpty()) {
            String rawCurrency = entry.get("Devise").asText();
            // Use your normalization logic here - if you have NormalizeCurrencyCode, use it
            // For now, just return uppercase
            String normalizedCurrency = rawCurrency.toUpperCase();
            log.info("💰 Extracted currency from AI response: {} -> {}", rawCurrency, normalizedCurrency);
            return normalizedCurrency;
        }
        log.info("⚠️ No currency information found in AI response, using default USD");
        return "USD";
    }

    private void updatePieceStatus(Piece piece, PieceStatus status) {
        // Convert PieceStatus enum to String
        pieceService.updatePieceStatus(piece.getId(), status.name());
    }

    private void rejectPiece(Piece piece, String reason) {
        log.error("❌ Rejecting piece {}: {}", piece.getId(), reason);
        pieceService.updatePieceStatus(piece.getId(), PieceStatus.REJECTED.name());
    }
}