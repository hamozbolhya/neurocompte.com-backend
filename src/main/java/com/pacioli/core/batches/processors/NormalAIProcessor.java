package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.pacioli.core.batches.clients.AIServiceClient;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.ExchangeRate;
import com.pacioli.core.models.Piece;
import com.pacioli.core.services.ExchangeRateService;
import com.pacioli.core.utils.NormalizeCurrencyCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
public class NormalAIProcessor extends BaseAIProcessor {

    @Autowired
    private AIServiceClient aiServiceClient;

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Autowired
    private NormalizeCurrencyCode normalizeCurrencyCode;

    public void processPieceWithRetry(Piece piece, int attempt) throws InterruptedException {
        if (attempt > batchConfig.getMaxRetries()) {
            rejectPiece(piece, "Failed after " + batchConfig.getMaxRetries() + " AI attempts");
            return;
        }

        updatePieceStatus(piece, PieceStatus.PROCESSING);

        try {
            log.info("📄 Calling NORMAL AI SERVICE for file: {}", piece.getFilename());
            JsonNode aiResponse = aiServiceClient.callAIService(piece.getFilename());

            if (!pieceValidator.isValidAIResponse(aiResponse)) {
                handleInvalidResponse(piece, attempt, aiResponse.toString());
                return;
            }

            JsonNode outputText = aiResponse.get("outputText");

            // ✅ ADDED: Extract and save AI data with currency conversion BEFORE processing
            extractAndSaveAIDataWithConversion(piece, outputText);

            processValidAIResponse(piece, outputText);

        } catch (Exception e) {
            handleProcessingError(piece, attempt, e);
        }
    }

    // ✅ ADD THIS METHOD: Complete currency conversion logic from old code
    private void extractAndSaveAIDataWithConversion(Piece piece, JsonNode aiResponse) throws JsonProcessingException {
        try {
            String responseText = aiResponse.asText();
            JsonNode parsedJson = objectMapper.readTree(responseText);

            JsonNode ecrituresNode = findEcrituresNodeForAI(parsedJson);

            if (ecrituresNode != null && ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                log.info("💰 Processing {} ecritures entries for currency extraction", ecrituresNode.size());

                JsonNode firstEntry = ecrituresNode.get(0);

                // Extract AI amount
                double originalAmount = calculateLargestAmount(ecrituresNode);
                piece.setAiAmount(originalAmount);

                // Extract AI currency
                String invoiceCurrencyCode = extractAndNormalizeCurrency(firstEntry);

                if (invoiceCurrencyCode != null) {
                    log.info("💰 AI provided currency: {}", invoiceCurrencyCode);
                    piece.setAiCurrency(invoiceCurrencyCode);

                    // Get the dossier currency
                    String dossierCurrencyCode = getDossierCurrencyCode(piece.getDossier());
                    log.info("💰 Dossier currency: {}", dossierCurrencyCode);

                    // ✅ EXTRACT INVOICE DATE FROM AI RESPONSE
                    String invoiceDateStr = extractStringSafely(firstEntry, "Date", null);
                    LocalDate invoiceDate = parseDate(invoiceDateStr != null ? invoiceDateStr : piece.getUploadDate().toString());
                    log.info("📅 Invoice date extracted from AI response: {} (from string: {})", invoiceDate, invoiceDateStr);

                    // Check if currency conversion is needed
                    if (!invoiceCurrencyCode.equals(dossierCurrencyCode)) {
                        log.info("💱 Currency conversion needed: {} to {}", invoiceCurrencyCode, dossierCurrencyCode);

                        // ✅ CALCULATE EXCHANGE RATE USING INVOICE DATE (from old code)
                        double exchangeRate = calculateExchangeRate(invoiceDate, invoiceCurrencyCode, dossierCurrencyCode);
                        LocalDate effectiveExchangeDate = determineEffectiveDate(invoiceDate);

                        // Set values in the piece entity
                        piece.setExchangeRate(exchangeRate);
                        piece.setConvertedCurrency(dossierCurrencyCode);
                        piece.setExchangeRateDate(effectiveExchangeDate);

                        log.info("💱 Applied currency conversion - rate: {}, target: {}, date: {}",
                                exchangeRate, dossierCurrencyCode, effectiveExchangeDate);
                    } else {
                        // Same currency, no conversion needed
                        piece.setConvertedCurrency(dossierCurrencyCode);
                        piece.setExchangeRate(1.0);
                        log.info("💱 No currency conversion needed - AI currency matches dossier: {}", dossierCurrencyCode);
                    }
                } else {
                    // AI didn't provide currency - use dossier currency
                    String dossierCurrencyCode = getDossierCurrencyCode(piece.getDossier());
                    log.warn("⚠️ AI returned no currency for piece {}, using dossier currency: {}", piece.getId(), dossierCurrencyCode);
                    piece.setAiCurrency(null);
                    piece.setConvertedCurrency(dossierCurrencyCode);
                    piece.setExchangeRate(1.0);
                }

                // Save the piece with AI data
                pieceRepository.save(piece);
                log.info("✅ Saved AI data to piece {}: AI Amount={}, AI Currency={}, Converted Currency={}, Exchange Rate={}",
                        piece.getId(), originalAmount, piece.getAiCurrency(), piece.getConvertedCurrency(), piece.getExchangeRate());

            } else {
                log.warn("⚠️ No valid ecritures found, using dossier currency");
                String dossierCurrencyCode = getDossierCurrencyCode(piece.getDossier());
                piece.setAiAmount(0.0);
                piece.setAiCurrency(null);
                piece.setConvertedCurrency(dossierCurrencyCode);
                piece.setExchangeRate(1.0);
                pieceRepository.save(piece);
            }
        } catch (Exception e) {
            log.error("❌ Failed to extract AI data: {}", e.getMessage(), e);
            // Use dossier currency as safe fallback
            String dossierCurrencyCode = getDossierCurrencyCode(piece.getDossier());
            piece.setAiAmount(0.0);
            piece.setAiCurrency(null);
            piece.setConvertedCurrency(dossierCurrencyCode);
            piece.setExchangeRate(1.0);
            pieceRepository.save(piece);
            log.info("✅ Set dossier currency as fallback for piece {} due to extraction error", piece.getId());
        }
    }

    // ✅ ADD THESE METHODS FROM OLD CODE:

    /**
     * Calculate exchange rate between two currencies using historical rates
     */
    private double calculateExchangeRate(LocalDate invoiceDate, String invoiceCurrencyCode, String dossierCurrencyCode) {
        try {
            // If currencies are the same, no conversion needed
            if (invoiceCurrencyCode.equals(dossierCurrencyCode)) {
                log.info("💱 Same currency for invoice and dossier ({}), no conversion needed", invoiceCurrencyCode);
                return 1.0;
            }

            // Apply date rules to get the effective date for exchange rate lookup
            LocalDate effectiveDate = determineEffectiveDate(invoiceDate);
            log.info("📅 Using effective date for exchange rate: {} (original invoice date: {})", effectiveDate, invoiceDate);

            // Get exchange rates for effective date
            ExchangeRate invoiceCurrencyRate = null;
            ExchangeRate dossierCurrencyRate = null;

            try {
                invoiceCurrencyRate = exchangeRateService.getExchangeRate(invoiceCurrencyCode, effectiveDate);
                log.info("💱 Got exchange rate for {}: {}", invoiceCurrencyCode, invoiceCurrencyRate != null ? invoiceCurrencyRate.getRate() : "null");
            } catch (Exception e) {
                log.error("Failed to get exchange rate for {} on date {}: {}", invoiceCurrencyCode, effectiveDate, e.getMessage());
                // Use a default fallback rate
                invoiceCurrencyRate = createFallbackExchangeRate(invoiceCurrencyCode, effectiveDate);
                log.info("💱 Using fallback exchange rate for {}: {}", invoiceCurrencyCode, invoiceCurrencyRate.getRate());
            }

            try {
                dossierCurrencyRate = exchangeRateService.getExchangeRate(dossierCurrencyCode, effectiveDate);
                log.info("💱 Got exchange rate for {}: {}", dossierCurrencyCode, dossierCurrencyRate != null ? dossierCurrencyRate.getRate() : "null");
            } catch (Exception e) {
                log.error("Failed to get exchange rate for {} on date {}: {}", dossierCurrencyCode, effectiveDate, e.getMessage());
                // Use a default fallback rate
                dossierCurrencyRate = createFallbackExchangeRate(dossierCurrencyCode, effectiveDate);
                log.info("💱 Using fallback exchange rate for {}: {}", dossierCurrencyCode, dossierCurrencyRate.getRate());
            }

            // Make sure both rates are not null before calculating conversion
            if (invoiceCurrencyRate == null) {
                invoiceCurrencyRate = createFallbackExchangeRate(invoiceCurrencyCode, effectiveDate);
                log.warn("💱 Using emergency fallback rate for invoice currency: {}", invoiceCurrencyCode);
            }

            if (dossierCurrencyRate == null) {
                dossierCurrencyRate = createFallbackExchangeRate(dossierCurrencyCode, effectiveDate);
                log.warn("💱 Using emergency fallback rate for dossier currency: {}", dossierCurrencyCode);
            }

            // Apply currency conversion rules
            double rate = calculateConversionRate(invoiceCurrencyCode, dossierCurrencyCode, invoiceCurrencyRate, dossierCurrencyRate);
            log.info("💱 Final exchange rate: 1 {} = {} {}", invoiceCurrencyCode, rate, dossierCurrencyCode);
            return rate;
        } catch (Exception e) {
            log.error("💥 Error calculating exchange rate: {}", e.getMessage(), e);
            // Return a default conversion rate as fallback
            return getEmergencyFallbackRate(invoiceCurrencyCode, dossierCurrencyCode);
        }
    }

    /**
     * Determine effective date for exchange rate lookup
     */
    private LocalDate determineEffectiveDate(LocalDate invoiceDate) {
        LocalDate today = LocalDate.now();
        LocalDate jan1st2024 = LocalDate.of(2024, 1, 1);

        // Rule 1: If invoice date is before 2024, use Jan 1, 2024
        if (invoiceDate.isBefore(jan1st2024)) {
            log.info("📅 Invoice date {} is before 2024, using Jan 1, 2024 for exchange rate", invoiceDate);
            return jan1st2024;
        }

        // Rule 2: If invoice date is after or equal to today, use yesterday
        if (invoiceDate.isEqual(today) || invoiceDate.isAfter(today)) {
            log.info("📅 Invoice date {} is today or in the future, using yesterday's date ({}) for exchange rate", invoiceDate, today.minusDays(1));
            return today.minusDays(1);
        }

        // Rule 3: Otherwise use the invoice date
        log.info("📅 Using actual invoice date {} for exchange rate", invoiceDate);
        return invoiceDate;
    }

    /**
     * Calculate conversion rate between two currencies
     */
    private double calculateConversionRate(String invoiceCurrencyCode, String dossierCurrencyCode,
                                           ExchangeRate invoiceCurrencyRate, ExchangeRate dossierCurrencyRate) {
        // Safety check
        if (invoiceCurrencyRate == null || dossierCurrencyRate == null) {
            log.error("💥 Null exchange rates in calculateConversionRate despite fallbacks");
            return getEmergencyFallbackRate(invoiceCurrencyCode, dossierCurrencyCode);
        }

        // Case 1: If invoice currency is USD and dossier currency is not USD
        if ("USD".equals(invoiceCurrencyCode)) {
            log.info("💱 Case 1: Invoice currency is USD, using direct USD→{} rate", dossierCurrencyCode);
            return dossierCurrencyRate.getRate();
        }

        // Case 2: If dossier currency is USD and invoice currency is not USD
        if ("USD".equals(dossierCurrencyCode)) {
            log.info("💱 Case 2: Dossier currency is USD, using inverse of USD→{} rate", invoiceCurrencyCode);
            return 1.0 / invoiceCurrencyRate.getRate();
        }

        // Case 3: Neither currency is USD
        log.info("💱 Case 3: Neither currency is USD, calculating cross rate");
        double rate = dossierCurrencyRate.getRate() / invoiceCurrencyRate.getRate();
        log.info("💱 Cross rate calculation: {} / {} = {}", dossierCurrencyRate.getRate(), invoiceCurrencyRate.getRate(), rate);
        return rate;
    }

    /**
     * Create fallback exchange rate when database lookup fails
     */
    private ExchangeRate createFallbackExchangeRate(String currencyCode, LocalDate date) {
        ExchangeRate rate = new ExchangeRate();
        rate.setCurrencyCode(currencyCode);
        rate.setDate(date);
        rate.setBaseCurrency("USD");

        // Set fallback rates based on common currencies
        switch (currencyCode) {
            case "USD":
                rate.setRate(1.0);
                break;
            case "EUR":
                rate.setRate(1.1);
                break;
            case "MAD":
                rate.setRate(10.0);
                break;
            case "GBP":
                rate.setRate(1.3);
                break;
            case "TND":
                rate.setRate(3.1);
                break;
            default:
                // For unknown currencies, default to 1:1 with USD
                rate.setRate(1.0);
                log.warn("No fallback rate known for currency {}, using 1.0", currencyCode);
        }

        return rate;
    }

    /**
     * Emergency fallback for critical failures
     */
    private double getEmergencyFallbackRate(String sourceCurrency, String targetCurrency) {
        if ("MAD".equals(sourceCurrency) && "USD".equals(targetCurrency)) {
            return 0.1; // 1 MAD = 0.1 USD
        } else if ("USD".equals(sourceCurrency) && "MAD".equals(targetCurrency)) {
            return 10.0; // 1 USD = 10 MAD
        } else if ("TND".equals(sourceCurrency) && "USD".equals(targetCurrency)) {
            return 0.32; // 1 TND = 0.32 USD
        } else if ("USD".equals(sourceCurrency) && "TND".equals(targetCurrency)) {
            return 3.1; // 1 USD = 3.1 TND
        } else if ("EUR".equals(sourceCurrency) && "USD".equals(targetCurrency)) {
            return 1.1; // 1 EUR = 1.1 USD
        } else if ("USD".equals(sourceCurrency) && "EUR".equals(targetCurrency)) {
            return 0.91; // 1 USD = 0.91 EUR
        } else {
            return 1.0; // Default to 1:1 for unknown currency pairs
        }
    }

    @Override
    protected void handleInvalidResponse(Piece piece, int attempt, String jsonResponse) throws InterruptedException {
        if (attempt < batchConfig.getMaxRetries()) {
            log.warn("🔄 Retrying piece {} due to invalid AI response (attempt {}/{})",
                    piece.getId(), attempt, batchConfig.getMaxRetries());
            Thread.sleep(batchConfig.getRetryDelayMs());
            processPieceWithRetry(piece, attempt + 1);
        } else {
            log.error("❌ File rejected - invalid AI response after all attempts: {}", jsonResponse);
            rejectPiece(piece, "Invalid AI response after all attempts");
        }
    }

    @Override
    protected void handleProcessingError(Piece piece, int attempt, Exception e) throws InterruptedException {
        if (attempt < batchConfig.getMaxRetries()) {
            log.warn("🔄 Retrying piece {} after error (attempt {}/{}): {}",
                    piece.getId(), attempt, batchConfig.getMaxRetries(), e.getMessage());
            Thread.sleep(batchConfig.getRetryDelayMs());
            processPieceWithRetry(piece, attempt + 1);
        } else {
            rejectPiece(piece, "Failed after all attempts: " + e.getMessage());
        }
    }
}