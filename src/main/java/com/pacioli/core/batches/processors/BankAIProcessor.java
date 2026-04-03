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

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

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

        Optional<Piece> originalPiece = duplicationDetectionService.findOriginalPiece(piece);
        if (originalPiece.isPresent()) {
            // #region agent log
            try (FileWriter fw = new FileWriter("/Users/hamzaboulahia/perso/neurocompte.com-backend/.cursor/debug-f12bb6.log", true)) {
                fw.write("{\"sessionId\":\"f12bb6\",\"runId\":\"forced-check-1\",\"hypothesisId\":\"H5\",\"location\":\"BankAIProcessor.processPieceWithRetry\",\"message\":\"Batch marked duplicate path\",\"data\":{\"pieceId\":"
                        + piece.getId() + ",\"isForced\":" + piece.getIsForced() + ",\"originalId\":" + originalPiece.get().getId()
                        + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            } catch (IOException ignored) {}
            // #endregion
            log.info("🚫 Skipping duplicate bank piece: {} (original: {})", piece.getId(), originalPiece.get().getId());
            duplicationDetectionService.markAsDuplicate(piece, originalPiece.get());
            return;
        }

        updatePieceStatus(piece, PieceStatus.PROCESSING);

        try {
            log.debug("🏦 Processing bank piece: {}", piece.getFilename());
            JsonNode aiResponse = callBankService(piece);

            // Normalize the response
            JsonNode normalizedResponse = responseNormalizer.normalizeAIResponse(aiResponse, true);

            // Check if the normalized response is valid
            if (!pieceValidator.isValidBankAIResponse(normalizedResponse)) {
                log.warn("❌ Bank piece {} — validation failed. {}", piece.getId(), describeNormalizedShape(normalizedResponse));
                handleInvalidResponse(piece, attempt,
                        "bank AI response failed validation; " + describeNormalizedShape(normalizedResponse));
                return;
            }

            // Extract the ecritures from normalized response for processing
            JsonNode ecrituresNode = normalizedResponse.get("ecritures");
            if (ecrituresNode == null || !ecrituresNode.isArray() || ecrituresNode.size() == 0) {
                log.warn("❌ Bank piece {} — no ecritures after normalization. {}", piece.getId(), describeNormalizedShape(normalizedResponse));
                handleInvalidResponse(piece, attempt,
                        "no ecritures array or empty after normalization; " + describeNormalizedShape(normalizedResponse));
                return;
            }

            log.debug("✅ Bank piece {} — {} ecritures validated", piece.getId(), ecrituresNode.size());

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
            JsonNode ecrituresNode = findEcrituresNodeForAI(aiResponse);

            if (ecrituresNode != null && ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                log.debug("🏦 Piece {} — extracting from {} bank ecriture groups/lines", piece.getId(), ecrituresNode.size());
                JsonNode firstEntry = ecrituresNode.get(0);

                extractAmountAndCurrency(piece, ecrituresNode, firstEntry);

                // ✅ CRITICAL FIX: Set the final amount on the piece
                setFinalPieceAmount(piece);

                pieceRepository.save(piece);
                log.debug("Piece {} — saved AI amount={}, final amount={}, currency={}",
                        piece.getId(), piece.getAiAmount(), piece.getAmount(), piece.getAiCurrency());

            } else {
                log.warn("⚠️ No ecritures found in AI response");
                applyFallbackCurrency(piece);
            }
        } catch (Exception e) {
            log.error("❌ Failed to extract bank AI data for piece {}: {}", piece.getId(), e.getMessage());
            log.debug("extractAndSaveAIData failure", e);
            applyFallbackCurrency(piece);
        }
    }

    private void setFinalPieceAmount(Piece piece) {
        if (piece.getAiAmount() != null && piece.getAiAmount() > 0) {
            if (piece.getExchangeRate() != null && piece.getExchangeRate() > 0) {
                // Use converted amount
                Double convertedAmount = piece.getAiAmount() * piece.getExchangeRate();
                piece.setAmount(convertedAmount);
                log.debug("💰 Set converted bank amount: {} (Original: {} × Rate: {})",
                        convertedAmount, piece.getAiAmount(), piece.getExchangeRate());
            } else {
                // No conversion, use AI amount directly
                piece.setAmount(piece.getAiAmount());
                log.debug("💰 Set direct bank amount: {}", piece.getAiAmount());
            }
        } else {
            piece.setAmount(0.0);
            log.warn("⚠️ No valid amount found for bank piece, setting to 0");
        }
    }

    private void extractAmountAndCurrency(Piece piece, JsonNode ecrituresNode, JsonNode firstEntry) {
        try {
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
            log.debug("💰 Total bank statement amount for piece {}: {}", piece.getId(), totalAmount);

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
            log.debug("💰 Extracted bank currency: {}", bankCurrency);

            String dossierCurrency = getDossierCurrencyCode(piece.getDossier());
            log.debug("💰 Dossier currency: {}", dossierCurrency);

            String transactionDateStr = extractStringSafely(firstEntry, "Date", null);
            LocalDate transactionDate = parseDate(transactionDateStr != null ? transactionDateStr : piece.getUploadDate().toString());
            log.debug("📅 Transaction date: {}", transactionDate);

            currencyDataExtractionService.calculateAndApplyExchangeRate(piece, bankCurrency, dossierCurrency, transactionDate);

        } catch (Exception e) {
            log.error("❌ Error in extractAmountAndCurrency for piece {}: {}", piece.getId(), e.getMessage());
            log.debug("extractAmountAndCurrency stack trace", e);
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

            log.debug("🏦 Fetching bank statement for: {}", fileId);
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
    protected void handleInvalidResponse(Piece piece, int attempt, String rejectionDetail) throws InterruptedException {
        if (attempt < batchConfig.getMaxRetries()) {
            log.warn("🔄 Retrying bank piece {} (attempt {}/{}): {}",
                    piece.getId(), attempt, batchConfig.getMaxRetries(), rejectionDetail);
            Thread.sleep(batchConfig.getRetryDelayMs()); // This will now be 5 minutes
            processPieceWithRetry(piece, attempt + 1);
        } else {
            log.error("❌ Bank file rejected piece {} after all attempts — {}", piece.getId(), rejectionDetail);
            rejectPiece(piece, "Invalid AI response after all attempts: " + rejectionDetail);
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