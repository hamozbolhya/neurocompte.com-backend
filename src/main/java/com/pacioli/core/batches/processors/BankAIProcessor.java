package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.pacioli.core.DTO.AI.BankStatementGetResponse;
import com.pacioli.core.batches.processors.converters.CurrencyConversionService;
import com.pacioli.core.batches.processors.converters.CurrencyDataExtractionService;
import com.pacioli.core.batches.processors.detection.DuplicationDetectionService;
import com.pacioli.core.batches.processors.normalizers.AIResponseNormalizer;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Piece;
import com.pacioli.core.services.AI.services.BankApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
public class BankAIProcessor extends BaseAIProcessor {

    @Autowired
    private BankApiService bankApiService;

    @Autowired
    private AIResponseNormalizer responseNormalizer;

    @Autowired
    private CurrencyConversionService currencyConversionService;

    @Autowired
    private CurrencyDataExtractionService currencyDataExtractionService;

    @Autowired
    private DuplicationDetectionService duplicationDetectionService;

    public void processPieceWithRetry(Piece piece, int attempt) throws InterruptedException {
        if (attempt > batchConfig.getMaxRetries()) {
            rejectPiece(piece, "Failed after " + batchConfig.getMaxRetries() + " AI attempts");
            return;
        }

        if (duplicationDetectionService.isDuplicate(piece)) {
            log.info("🚫 Skipping duplicate piece: {}", piece.getId());
            updatePieceStatus(piece, PieceStatus.DUPLICATE);
            return;
        }

        updatePieceStatus(piece, PieceStatus.PROCESSING);

        try {
            log.info("🏦 Processing bank piece: {}", piece.getFilename());
            JsonNode aiResponse = callBankService(piece);

            // Normalize the response
            JsonNode normalizedResponse = responseNormalizer.normalizeAIResponse(aiResponse, true);
            log.info("🏦 Normalized response keys: {}", normalizedResponse.fieldNames());

            // Check if the normalized response is valid
            if (!pieceValidator.isValidBankAIResponse(normalizedResponse)) {
                log.warn("❌ Invalid normalized bank response, retrying...");
                handleInvalidResponse(piece, attempt, normalizedResponse.toString());
                return;
            }

            // Extract the ecritures from normalized response for processing
            JsonNode ecrituresNode = normalizedResponse.get("ecritures");
            if (ecrituresNode == null || !ecrituresNode.isArray() || ecrituresNode.size() == 0) {
                log.warn("❌ No ecritures in normalized bank response, retrying...");
                handleInvalidResponse(piece, attempt, normalizedResponse.toString());
                return;
            }

            log.info("✅ Valid bank AI response with {} ecritures", ecrituresNode.size());

            // Process the data - pass the normalized response which contains ecritures
            extractAndSaveAIData(piece, normalizedResponse);

            // ✅ PASS THE NORMALIZED RESPONSE to the parent method, not the original aiResponse
            processValidAIResponse(piece, normalizedResponse);

        } catch (Exception e) {
            log.error("❌ Error processing bank piece {}: {}", piece.getId(), e.getMessage());
            handleProcessingError(piece, attempt, e);
        }
    }

    private void extractAndSaveAIData(Piece piece, JsonNode aiResponse) throws JsonProcessingException {
        try {
            log.info("🔍 Starting extractAndSaveAIData for piece {}", piece.getId());

            // ❌ PROBLEM: Don't call asText() on already parsed JSON!
            // String responseText = aiResponse.asText();
            // JsonNode parsedJson = objectMapper.readTree(responseText);

            // ✅ FIX: Use the aiResponse directly (it's already parsed JSON)
            JsonNode ecrituresNode = findEcrituresNodeForAI(aiResponse);
            log.info("🔍 Found ecritures node: {}", ecrituresNode != null);

            if (ecrituresNode != null && ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                log.info("🏦 Processing {} bank entries", ecrituresNode.size());
                JsonNode firstEntry = ecrituresNode.get(0);

                extractAmountAndCurrency(piece, ecrituresNode, firstEntry);

                // ✅ CRITICAL FIX: Set the final amount on the piece
                setFinalPieceAmount(piece);

                pieceRepository.save(piece);
                log.info("✅ Saved bank piece with AI Amount: {}, Final Amount: {}, Currency: {}",
                        piece.getAiAmount(), piece.getAmount(), piece.getAiCurrency());

            } else {
                log.warn("⚠️ No ecritures found in AI response");
                applyFallbackCurrency(piece);
            }
        } catch (Exception e) {
            log.error("❌ Failed to extract bank AI data: {}", e.getMessage(), e);
            applyFallbackCurrency(piece);
        }
    }

    private void setFinalPieceAmount(Piece piece) {
        if (piece.getAiAmount() != null && piece.getAiAmount() > 0) {
            if (piece.getExchangeRate() != null && piece.getExchangeRate() > 0) {
                // Use converted amount
                Double convertedAmount = piece.getAiAmount() * piece.getExchangeRate();
                piece.setAmount(convertedAmount);
                log.info("💰 Set converted bank amount: {} (Original: {} × Rate: {})",
                        convertedAmount, piece.getAiAmount(), piece.getExchangeRate());
            } else {
                // No conversion, use AI amount directly
                piece.setAmount(piece.getAiAmount());
                log.info("💰 Set direct bank amount: {}", piece.getAiAmount());
            }
        } else {
            piece.setAmount(0.0);
            log.warn("⚠️ No valid amount found for bank piece, setting to 0");
        }
    }

    private void extractAmountAndCurrency(Piece piece, JsonNode ecrituresNode, JsonNode firstEntry) {
        try {
            log.info("🔍 Starting extractAmountAndCurrency for bank statement");

            // Calculate total amount from ALL transactions
            double totalAmount = 0.0;
            for (JsonNode node : ecrituresNode) {
                if (node.has("entries") && node.get("entries").isArray()) {
                    JsonNode entries = node.get("entries");
                    for (JsonNode entry : entries) {
                        double debit = parseDoubleSafely(entry, "DebitAmt");
                        double credit = parseDoubleSafely(entry, "CreditAmt");
                        totalAmount += Math.max(debit, credit);
                    }
                } else {
                    double debit = parseDoubleSafely(node, "DebitAmt");
                    double credit = parseDoubleSafely(node, "CreditAmt");
                    totalAmount += Math.max(debit, credit);
                }
            }

            piece.setAiAmount(totalAmount);
            log.info("💰 Total bank statement amount: {}", totalAmount);

            // Extract currency from first valid entry
            String bankCurrency = null;
            for (JsonNode node : ecrituresNode) {
                if (node.has("entries") && node.get("entries").isArray() &&
                        node.get("entries").size() > 0) {
                    bankCurrency = extractAndNormalizeCurrency(node.get("entries").get(0));
                    break;
                } else if (node.has("Devise")) {
                    bankCurrency = extractAndNormalizeCurrency(node);
                    break;
                }
            }

            piece.setAiCurrency(bankCurrency);
            log.info("💰 Extracted bank currency: {}", bankCurrency);

            // ... rest of the method remains the same
            String dossierCurrency = getDossierCurrencyCode(piece.getDossier());
            log.info("💰 Dossier currency: {}", dossierCurrency);

            String transactionDateStr = extractStringSafely(firstEntry, "Date", null);
            LocalDate transactionDate = parseDate(transactionDateStr != null ? transactionDateStr : piece.getUploadDate().toString());
            log.info("📅 Transaction date: {}", transactionDate);

            currencyDataExtractionService.calculateAndApplyExchangeRate(piece, bankCurrency, dossierCurrency, transactionDate);

        } catch (Exception e) {
            log.error("❌ Error in extractAmountAndCurrency: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void applyFallbackCurrency(Piece piece) {
        log.warn("⚠️ No valid bank entries, using dossier currency");
        String dossierCurrency = getDossierCurrencyCode(piece.getDossier());
        piece.setAiAmount(0.0);
        piece.setAiCurrency(null);
        currencyDataExtractionService.applyDefaultCurrency(piece, dossierCurrency);
        pieceRepository.save(piece);
    }

    private JsonNode callBankService(Piece piece) {
        try {
            String filename = piece.getFilename();
            String fileId = filename.substring(0, filename.lastIndexOf('.'));

            log.info("🏦 Fetching bank statement for: {}", fileId);
            BankStatementGetResponse bankResponse = bankApiService.getBankStatementResult(fileId);

            if (!bankResponse.isSuccess()) {
                throw new RuntimeException("Bank API failed: " + bankResponse.getMessage());
            }

            String jsonResponse = bankResponse.getJsonResponse();

            // ✅ NEW: Clean markdown code fences if present
            if (jsonResponse != null && jsonResponse.contains("```json")) {
                jsonResponse = cleanMarkdownCodeFences(jsonResponse);
            }

            return objectMapper.readTree(jsonResponse);

        } catch (Exception e) {
            log.error("❌ Bank API call failed: {}", e.getMessage());
            throw new RuntimeException("Bank service call failed: " + e.getMessage(), e);
        }
    }

    @Override
    protected void handleInvalidResponse(Piece piece, int attempt, String jsonResponse) throws InterruptedException {
        if (attempt < batchConfig.getMaxRetries()) {
            log.warn("🔄 Retrying bank piece {} due to invalid AI response (attempt {}/{})",
                    piece.getId(), attempt, batchConfig.getMaxRetries());
            Thread.sleep(batchConfig.getRetryDelayMs()); // This will now be 5 minutes
            processPieceWithRetry(piece, attempt + 1);
        } else {
            log.error("❌ Bank file rejected - invalid AI response after all attempts: {}", jsonResponse);
            rejectPiece(piece, "Invalid AI response after all attempts");
        }
    }

    @Override
    protected void handleProcessingError(Piece piece, int attempt, Exception e) throws InterruptedException {
        if (attempt < batchConfig.getMaxRetries()) {
            log.warn("🔄 Retrying bank piece {} after error (attempt {}/{}): {}",
                    piece.getId(), attempt, batchConfig.getMaxRetries(), e.getMessage());
            Thread.sleep(batchConfig.getRetryDelayMs()); // This will now be 5 minutes
            processPieceWithRetry(piece, attempt + 1);
        } else {
            rejectPiece(piece, "Failed after all attempts: " + e.getMessage());
        }
    }
}