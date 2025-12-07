package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.pacioli.core.batches.clients.AIServiceClient;
import com.pacioli.core.batches.processors.converters.CurrencyDataExtractionService;
import com.pacioli.core.batches.processors.detection.DuplicationDetectionService;
import com.pacioli.core.batches.processors.normalizers.AIResponseNormalizer;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Piece;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

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

    public void processPieceWithRetry(Piece piece, int attempt) {
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
            log.info("📄 Processing normal piece: {}", piece.getFilename());

            // Check if AI file likely exists before calling
            if (!shouldAttemptAICall(piece)) {
                log.warn("⚠️ Skipping AI call for piece {}, unlikely to succeed", piece.getId());
                handleNoAIResponse(piece, attempt);
                return;
            }

            JsonNode aiResponse = aiServiceClient.callAIService(piece.getFilename());

            // Normalize the response
            JsonNode normalizedResponse = responseNormalizer.normalizeAIResponse(aiResponse, false);
            log.info("📄 Normalized response keys: {}", normalizedResponse.fieldNames());

            // Check if the normalized response is valid
            if (!pieceValidator.isValidAIResponse(normalizedResponse)) {
                log.warn("❌ Invalid normalized response, retrying...");
                handleInvalidResponse(piece, attempt, normalizedResponse.toString());
                return;
            }

            // Extract the ecritures from normalized response for processing
            JsonNode ecrituresNode = normalizedResponse.get("ecritures");
            if (ecrituresNode == null || !ecrituresNode.isArray() || ecrituresNode.size() == 0) {
                log.warn("❌ No ecritures in normalized response, retrying...");
                handleInvalidResponse(piece, attempt, normalizedResponse.toString());
                return;
            }

            log.info("✅ Valid AI response with {} ecritures", ecrituresNode.size());

            // Process the data - pass the normalized response which contains ecritures
            extractAndSaveAIData(piece, normalizedResponse);

            // ✅ PASS THE NORMALIZED RESPONSE to the parent method, not the original aiResponse
            processValidAIResponse(piece, normalizedResponse);

        } catch (HttpClientErrorException.NotFound e) {
            log.error("❌ AI file not found (404) for piece {}: {}", piece.getId(), e.getMessage());
            handle404Error(piece, attempt, e);

        } catch (HttpClientErrorException e) {
            log.error("❌ HTTP error calling AI service for piece {}: {} - {}",
                    piece.getId(), e.getStatusCode(), e.getMessage());
            handleHttpError(piece, attempt, e);

        } catch (ResourceAccessException e) {
            log.error("❌ Connection error calling AI service for piece {}: {}",
                    piece.getId(), e.getMessage());
            handleConnectionError(piece, attempt, e);

        } catch (Exception e) {
            log.error("❌ Error processing piece {}: {}", piece.getId(), e.getMessage());
            handleProcessingError(piece, attempt, e);
        }
    }

    /**
     * Check if we should even attempt an AI call
     */
    private boolean shouldAttemptAICall(Piece piece) {
        // Check if filename looks valid (should be UUID.pdf)
        String filename = piece.getFilename();
        if (filename == null || !filename.matches(".*\\.pdf$")) {
            log.warn("⚠️ Invalid filename format for AI call: {}", filename);
            return false;
        }

        // Check if piece was uploaded recently (within last 24 hours)
        // If it's very old, AI service might have deleted it
        long uploadAge = System.currentTimeMillis() - piece.getUploadDate().getTime();
        long maxAge = TimeUnit.HOURS.toMillis(24);

        if (uploadAge > maxAge) {
            log.warn("⚠️ Piece {} is too old ({} hours), AI file may not exist",
                    piece.getId(), TimeUnit.MILLISECONDS.toHours(uploadAge));
            return false;
        }

        return true;
    }

    private void extractAndSaveAIData(Piece piece, JsonNode normalizedResponse) throws JsonProcessingException {
        try {
            JsonNode ecrituresNode = normalizedResponse.get("ecritures");

            if (ecrituresNode != null && ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                log.info("💰 Processing {} invoice entries", ecrituresNode.size());
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

    private void extractAmountAndCurrency(Piece piece, JsonNode ecrituresNode, JsonNode firstEntry) {
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

        log.info("💰 Extracted invoice data - Amount: {}, Currency: {}, Converted: {}, Rate: {}",
                originalAmount, invoiceCurrency, piece.getConvertedCurrency(), piece.getExchangeRate());
    }

    private void applyFallbackCurrency(Piece piece) {
        log.warn("⚠️ No valid invoice entries, using dossier currency");
        String dossierCurrency = getDossierCurrencyCode(piece.getDossier());
        piece.setAiAmount(0.0);
        piece.setAiCurrency(null);
        currencyDataExtractionService.applyDefaultCurrency(piece, dossierCurrency);
        pieceRepository.save(piece);
    }

    @Override
    protected void handleInvalidResponse(Piece piece, int attempt, String jsonResponse) {
        if (attempt < batchConfig.getMaxRetries()) {
            log.warn("🔄 Retrying piece {} due to invalid AI response (attempt {}/{})",
                    piece.getId(), attempt + 1, batchConfig.getMaxRetries());

            try {
                // Exponential backoff: 30s, 60s, 120s
                long delay = (long) (Math.pow(2, attempt - 1) * 30000);
                Thread.sleep(delay);
                processPieceWithRetry(piece, attempt + 1);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                rejectPiece(piece, "Processing interrupted");
            }
        } else {
            log.error("❌ File rejected - invalid AI response after all attempts");
            rejectPiece(piece, "Invalid AI response after all attempts");
        }
    }

    @Override
    protected void handleProcessingError(Piece piece, int attempt, Exception e) {
        if (attempt < batchConfig.getMaxRetries()) {
            log.warn("🔄 Retrying piece {} after error (attempt {}/{}): {}",
                    piece.getId(), attempt + 1, batchConfig.getMaxRetries(), e.getMessage());

            try {
                // Exponential backoff
                long delay = (long) (Math.pow(2, attempt - 1) * 30000);
                Thread.sleep(delay);
                processPieceWithRetry(piece, attempt + 1);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                rejectPiece(piece, "Processing interrupted");
            }
        } else {
            rejectPiece(piece, "Failed after all attempts: " + e.getMessage());
        }
    }

    /**
     * Handle 404 errors specifically
     */
    private void handle404Error(Piece piece, int attempt, HttpClientErrorException.NotFound e) {
        log.error("❌ AI file not found for piece {} (attempt {}/{})",
                piece.getId(), attempt, batchConfig.getMaxRetries());

        if (attempt < batchConfig.getMaxRetries()) {
            log.warn("🔄 Retrying piece {} after 404 (attempt {}/{})",
                    piece.getId(), attempt + 1, batchConfig.getMaxRetries());

            try {
                // Longer delay for 404s - maybe AI service is still processing
                long delay = (long) (Math.pow(2, attempt - 1) * 60000); // 1min, 2min, 4min
                Thread.sleep(delay);
                processPieceWithRetry(piece, attempt + 1);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                rejectPiece(piece, "Processing interrupted after 404");
            }
        } else {
            log.error("❌ Giving up on piece {} after {} attempts with 404s",
                    piece.getId(), batchConfig.getMaxRetries());
            // Mark as REJECTED instead of trying forever
            rejectPiece(piece, "AI file not found after all attempts");
        }
    }

    /**
     * Handle other HTTP errors (4xx, 5xx)
     */
    private void handleHttpError(Piece piece, int attempt, HttpClientErrorException e) {
        HttpStatus status = (HttpStatus) e.getStatusCode();
        log.error("❌ HTTP {} error for piece {}: {}", status, piece.getId(), e.getMessage());

        if (status.is5xxServerError() && attempt < batchConfig.getMaxRetries()) {
            // Retry on server errors
            log.warn("🔄 Retrying piece {} after server error (attempt {}/{})",
                    piece.getId(), attempt + 1, batchConfig.getMaxRetries());

            try {
                long delay = (long) (Math.pow(2, attempt - 1) * 60000);
                Thread.sleep(delay);
                processPieceWithRetry(piece, attempt + 1);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                rejectPiece(piece, "Processing interrupted after server error");
            }
        } else {
            // Don't retry on client errors (4xx)
            rejectPiece(piece, "HTTP " + status + " error: " + e.getMessage());
        }
    }

    /**
     * Handle connection errors (timeouts, network issues)
     */
    private void handleConnectionError(Piece piece, int attempt, ResourceAccessException e) {
        log.error("❌ Connection error for piece {}: {}", piece.getId(), e.getMessage());

        if (attempt < batchConfig.getMaxRetries()) {
            log.warn("🔄 Retrying piece {} after connection error (attempt {}/{})",
                    piece.getId(), attempt + 1, batchConfig.getMaxRetries());

            try {
                long delay = (long) (Math.pow(2, attempt - 1) * 30000);
                Thread.sleep(delay);
                processPieceWithRetry(piece, attempt + 1);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                rejectPiece(piece, "Processing interrupted after connection error");
            }
        } else {
            rejectPiece(piece, "Connection failed after all attempts: " + e.getMessage());
        }
    }

    /**
     * Handle cases where we shouldn't even call AI
     */
    private void handleNoAIResponse(Piece piece, int attempt) {
        log.warn("⚠️ Piece {} marked for manual processing - no AI response available", piece.getId());

        // Option 1: Mark for manual processing
        piece.setStatus(PieceStatus.UPLOADED); // Reset to uploaded for manual handling
        pieceRepository.save(piece);

        // Option 2: Or reject immediately
        // rejectPiece(piece, "No AI response available - requires manual processing");

        // Option 3: Set basic info and mark as processed with flag
        applyFallbackCurrency(piece);
        piece.setStatus(PieceStatus.PROCESSED);
        pieceRepository.save(piece);
        log.info("✅ Piece {} processed with fallback data (no AI response)", piece.getId());
    }
}