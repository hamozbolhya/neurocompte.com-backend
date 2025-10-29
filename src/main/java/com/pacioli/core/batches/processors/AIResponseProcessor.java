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

@Component
@Slf4j
public class AIResponseProcessor {
    private static final int MAX_RETRIES = 4;
    private static final long RETRY_DELAY_MS = 30000;

    @Autowired private PieceRepository pieceRepository;
    @Autowired private PieceService pieceService;
    @Autowired private AIServiceClient aiServiceClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PieceValidator pieceValidator;
    @Autowired private DTOBuilder dtoBuilder;
    @Autowired private CurrencyConverter currencyConverter;

    public void processPieceWithRetry(Piece piece, int attempt) throws InterruptedException {
        if (attempt > MAX_RETRIES) {
            rejectPiece(piece, "Failed after " + MAX_RETRIES + " AI attempts");
            return;
        }

        updatePieceStatus(piece, PieceStatus.PROCESSING);

        try {
            JsonNode aiResponse = aiServiceClient.callAIService(piece.getFilename());
            JsonNode outputText = aiResponse.get("outputText");

            if (!pieceValidator.isValidAIResponse(aiResponse)) {
                handleInvalidResponse(piece, attempt, aiResponse.toString());
                return;
            }

            processValidAIResponse(piece, outputText);

        } catch (Exception e) {
            handleProcessingError(piece, attempt, e);
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
            JsonNode ecrituresNode = findEcrituresNode(parsedJson);

            if (ecrituresNode != null && ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                JsonNode firstEntry = ecrituresNode.get(0);

                // Extract AI amount
                double originalAmount = calculateLargestAmount(ecrituresNode);
                piece.setAiAmount(originalAmount);

                // Extract AI currency
                String invoiceCurrencyCode = extractAndNormalizeCurrency(firstEntry);
                piece.setAiCurrency(invoiceCurrencyCode);

                // ✅ CRITICAL: Save the piece with AI data
                pieceRepository.save(piece);

                log.info("✅ Saved AI data to piece {}: amount={}, currency={}",
                        piece.getId(), originalAmount, invoiceCurrencyCode);
            }
        } catch (Exception e) {
            log.error("❌ Failed to extract AI data: {}", e.getMessage());
            throw e;
        }
    }

    private JsonNode findEcrituresNode(JsonNode parsedJson) {
        if (parsedJson.has("ecritures")) return parsedJson.get("ecritures");
        if (parsedJson.has("Ecritures")) return parsedJson.get("Ecritures");
        return null;
    }

    private Double calculateLargestAmount(JsonNode ecritures) {
        double maxAmount = 0.0;
        for (JsonNode entry : ecritures) {
            double debit = entry.get("DebitAmt").asDouble();
            double credit = entry.get("CreditAmt").asDouble();
            maxAmount = Math.max(maxAmount, Math.max(debit, credit));
        }
        return maxAmount;
    }

    private String extractAndNormalizeCurrency(JsonNode entry) {
        if (entry.has("Devise") && !entry.get("Devise").isNull() && !entry.get("Devise").asText().isEmpty()) {
            String rawCurrency = entry.get("Devise").asText();
            // Use your normalization logic here
            return rawCurrency.toUpperCase(); // Simple normalization
        }
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