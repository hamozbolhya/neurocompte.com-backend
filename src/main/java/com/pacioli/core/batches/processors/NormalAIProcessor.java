package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pacioli.core.batches.clients.AIServiceClient;
import com.pacioli.core.batches.processors.converters.CurrencyDataExtractionService;
import com.pacioli.core.batches.processors.detection.DuplicationDetectionService;
import com.pacioli.core.batches.processors.normalizers.AIResponseNormalizer;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Piece;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Component
public class NormalAIProcessor extends BaseAIProcessor {

    @Autowired
    private AIServiceClient aiServiceClient;

    @Autowired
    private AIResponseNormalizer responseNormalizer;

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
                fw.write("{\"sessionId\":\"f12bb6\",\"runId\":\"forced-check-1\",\"hypothesisId\":\"H5\",\"location\":\"NormalAIProcessor.processPieceWithRetry\",\"message\":\"Batch marked duplicate path\",\"data\":{\"pieceId\":"
                        + piece.getId() + ",\"isForced\":" + piece.getIsForced() + ",\"originalId\":" + originalPiece.get().getId()
                        + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            } catch (IOException ignored) {}
            // #endregion
            log.info("🚫 Skipping duplicate piece: {} (original: {})", piece.getId(), originalPiece.get().getId());
            duplicationDetectionService.markAsDuplicate(piece, originalPiece.get());
            return;
        }

        updatePieceStatus(piece, PieceStatus.PROCESSING);


        try {
            log.debug("📄 Processing normal piece: {}", piece.getFilename());
            JsonNode aiResponse = aiServiceClient.callAIService(piece.getFilename());

            // Handle markdown in raw AI response if needed
            if (aiResponse.has("outputText")) {
                String outputText = aiResponse.get("outputText").asText();
                if (outputText.contains("```json")) {
                    // Clean the outputText in the JSON node
                    ((ObjectNode) aiResponse).put("outputText", cleanMarkdownCodeFences(outputText));
                }
            }

            // Normalize the response
            JsonNode normalizedResponse = responseNormalizer.normalizeAIResponse(aiResponse, false);

            // Check if the normalized response is valid
            if (!pieceValidator.isValidAIResponse(normalizedResponse)) {
                log.warn("❌ Piece {} — validation failed. {}", piece.getId(), describeNormalizedShape(normalizedResponse));
                handleInvalidResponse(piece, attempt,
                        "invoice AI response failed validation; " + describeNormalizedShape(normalizedResponse));
                return;
            }

            // Extract the ecritures from normalized response for processing
            JsonNode ecrituresNode = normalizedResponse.get("ecritures");
            if (ecrituresNode == null || !ecrituresNode.isArray() || ecrituresNode.size() == 0) {
                log.warn("❌ Piece {} — no ecritures after normalization. {}", piece.getId(), describeNormalizedShape(normalizedResponse));
                handleInvalidResponse(piece, attempt,
                        "no ecritures array or empty after normalization; " + describeNormalizedShape(normalizedResponse));
                return;
            }

            log.debug("✅ Piece {} — {} ecritures validated", piece.getId(), ecrituresNode.size());

            // Process the data - pass the normalized response which contains ecritures
            extractAndSaveAIData(piece, normalizedResponse);

            // ✅ PASS THE NORMALIZED RESPONSE to the parent method, not the original aiResponse
            processValidAIResponse(piece, normalizedResponse);

        } catch (Exception e) {
//            log.error("❌ Error processing piece {}: {}", piece.getId(), e.getMessage());
            handleProcessingError(piece, attempt, e);
        }
    }

    private void extractAndSaveAIData(@NonNull Piece piece, JsonNode normalizedResponse) throws JsonProcessingException {
        try {
            JsonNode ecrituresNode = normalizedResponse.get("ecritures");

            if (ecrituresNode != null && ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                log.debug("💰 Processing {} invoice entries", ecrituresNode.size());
                JsonNode firstEntry = ecrituresNode.get(0);

                extractAmountAndCurrency(piece, ecrituresNode, firstEntry);
                pieceRepository.save(piece);

            } else {
                applyFallbackCurrency(piece);
            }
        } catch (Exception e) {
            log.error("❌ Failed to extract invoice AI data: {}", e.getMessage(), e);
            applyFallbackCurrency(piece);
        }
    }

    private void extractAmountAndCurrency(@NonNull Piece piece, JsonNode ecrituresNode, JsonNode firstEntry) {
        // Extract amount
        double originalAmount = calculateLargestAmount(ecrituresNode);
        piece.setAiAmount(originalAmount);

        // Extract currency
        String invoiceCurrency = extractAndNormalizeCurrency(firstEntry);
        piece.setAiCurrency(invoiceCurrency);

        // Get dossier currency
        String dossierCurrency = getDossierCurrencyCode(piece.getDossier());

        // Extract invoice date
        String invoiceDateStr = extractStringSafely(firstEntry, "Date", null);
        LocalDate invoiceDate = parseDate(invoiceDateStr != null ? invoiceDateStr : piece.getUploadDate().toString());

        // Apply currency conversion using dedicated service
        currencyDataExtractionService.calculateAndApplyExchangeRate(piece, invoiceCurrency, dossierCurrency, invoiceDate);

        log.debug("💰 Extracted invoice data - Amount: {}, Currency: {}, Converted: {}, Rate: {}",
                originalAmount, invoiceCurrency, piece.getConvertedCurrency(), piece.getExchangeRate());
    }

    private void applyFallbackCurrency(@NonNull Piece piece) {
        log.warn("⚠️ No valid invoice entries, using dossier currency");
        String dossierCurrency = getDossierCurrencyCode(piece.getDossier());
        piece.setAiAmount(0.0);
        piece.setAiCurrency(null);
        currencyDataExtractionService.applyDefaultCurrency(piece, dossierCurrency);
        pieceRepository.save(piece);
    }


    @Override
    protected void handleInvalidResponse(Piece piece, int attempt, String rejectionDetail) throws InterruptedException {
        if (attempt < batchConfig.getMaxRetries()) {
            log.warn("🔄 Retrying piece {} (attempt {}/{}): {}",
                    piece.getId(), attempt, batchConfig.getMaxRetries(), rejectionDetail);

            // For normal pieces, use shorter delay or immediate retry
            Thread.sleep(30000); // 30 seconds instead of 5 minutes
            processPieceWithRetry(piece, attempt + 1);
        } else {
            log.error("❌ File rejected piece {} after all attempts — {}", piece.getId(), rejectionDetail);
            rejectPiece(piece, "Invalid AI response after all attempts: " + rejectionDetail);
        }
    }

    @Override
    protected void handleProcessingError(Piece piece, int attempt, Exception e) throws InterruptedException {
        if (attempt < batchConfig.getMaxRetries()) {
            log.warn("🔄 Retrying piece {} after error (attempt {}/{}): {}",
                    piece.getId(), attempt, batchConfig.getMaxRetries(), e.getMessage());

            // For normal pieces, use shorter delay or immediate retry
            Thread.sleep(30000); // 30 seconds instead of 5 minutes
            processPieceWithRetry(piece, attempt + 1);
        } else {
            rejectPiece(piece, "Failed after all attempts: " + e.getMessage());
        }
    }
}