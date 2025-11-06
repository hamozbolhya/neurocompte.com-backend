package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pacioli.core.DTO.AI.BankStatementGetResponse;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.ExchangeRate;
import com.pacioli.core.models.Piece;
import com.pacioli.core.services.AI.services.BankApiService;
import com.pacioli.core.services.ExchangeRateService;
import com.pacioli.core.utils.NormalizeCurrencyCode;
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
            log.info("🏦 Calling BANK SERVICE GET API for file: {}", piece.getFilename());
            JsonNode aiResponse = callBankService(piece);

            if (!pieceValidator.isValidBankAIResponse(aiResponse)) {
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

    // ✅ ADD THIS METHOD: Complete currency conversion logic for bank processing
    private void extractAndSaveAIDataWithConversion(Piece piece, JsonNode aiResponse) throws JsonProcessingException {
        try {
            String responseText = aiResponse.asText();
            JsonNode parsedJson = objectMapper.readTree(responseText);

            JsonNode ecrituresNode = findEcrituresNodeForAI(parsedJson);

            if (ecrituresNode != null && ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                log.info("🏦 Processing {} bank statement entries for currency extraction", ecrituresNode.size());

                JsonNode firstEntry = ecrituresNode.get(0);

                // Extract AI amount
                double originalAmount = calculateLargestAmount(ecrituresNode);
                piece.setAiAmount(originalAmount);

                // Extract AI currency
                String invoiceCurrencyCode = extractAndNormalizeCurrency(firstEntry);

                if (invoiceCurrencyCode != null) {
                    log.info("🏦 Bank statement currency: {}", invoiceCurrencyCode);
                    piece.setAiCurrency(invoiceCurrencyCode);

                    // Get the dossier currency
                    String dossierCurrencyCode = getDossierCurrencyCode(piece.getDossier());
                    log.info("🏦 Dossier currency: {}", dossierCurrencyCode);

                    // ✅ EXTRACT TRANSACTION DATE FROM BANK STATEMENT
                    String transactionDateStr = extractStringSafely(firstEntry, "Date", null);
                    LocalDate transactionDate = parseDate(transactionDateStr != null ? transactionDateStr : piece.getUploadDate().toString());
                    log.info("🏦 Transaction date extracted from bank statement: {} (from string: {})", transactionDate, transactionDateStr);

                    // Check if currency conversion is needed
                    if (!invoiceCurrencyCode.equals(dossierCurrencyCode)) {
                        log.info("🏦 Currency conversion needed: {} to {}", invoiceCurrencyCode, dossierCurrencyCode);

                        // ✅ CALCULATE EXCHANGE RATE USING TRANSACTION DATE
                        double exchangeRate = calculateExchangeRate(transactionDate, invoiceCurrencyCode, dossierCurrencyCode);
                        LocalDate effectiveExchangeDate = determineEffectiveDate(transactionDate);

                        // Set values in the piece entity
                        piece.setExchangeRate(exchangeRate);
                        piece.setConvertedCurrency(dossierCurrencyCode);
                        piece.setExchangeRateDate(effectiveExchangeDate);

                        log.info("🏦 Applied currency conversion - rate: {}, target: {}, date: {}",
                                exchangeRate, dossierCurrencyCode, effectiveExchangeDate);
                    } else {
                        // Same currency, no conversion needed
                        piece.setConvertedCurrency(dossierCurrencyCode);
                        piece.setExchangeRate(1.0);
                        log.info("🏦 No currency conversion needed - Bank currency matches dossier: {}", dossierCurrencyCode);
                    }
                } else {
                    // AI didn't provide currency - use dossier currency
                    String dossierCurrencyCode = getDossierCurrencyCode(piece.getDossier());
                    log.warn("⚠️ Bank statement returned no currency for piece {}, using dossier currency: {}", piece.getId(), dossierCurrencyCode);
                    piece.setAiCurrency(null);
                    piece.setConvertedCurrency(dossierCurrencyCode);
                    piece.setExchangeRate(1.0);
                }

                // Save the piece with AI data
                pieceRepository.save(piece);
                log.info("🏦 Saved bank AI data to piece {}: AI Amount={}, AI Currency={}, Converted Currency={}, Exchange Rate={}",
                        piece.getId(), originalAmount, piece.getAiCurrency(), piece.getConvertedCurrency(), piece.getExchangeRate());

            } else {
                log.warn("⚠️ No valid bank entries found, using dossier currency");
                String dossierCurrencyCode = getDossierCurrencyCode(piece.getDossier());
                piece.setAiAmount(0.0);
                piece.setAiCurrency(null);
                piece.setConvertedCurrency(dossierCurrencyCode);
                piece.setExchangeRate(1.0);
                pieceRepository.save(piece);
            }
        } catch (Exception e) {
            log.error("❌ Failed to extract bank AI data: {}", e.getMessage(), e);
            // Use dossier currency as safe fallback
            String dossierCurrencyCode = getDossierCurrencyCode(piece.getDossier());
            piece.setAiAmount(0.0);
            piece.setAiCurrency(null);
            piece.setConvertedCurrency(dossierCurrencyCode);
            piece.setExchangeRate(1.0);
            pieceRepository.save(piece);
            log.info("🏦 Set dossier currency as fallback for bank piece {} due to extraction error", piece.getId());
        }
    }

    // ✅ ADD CURRENCY CONVERSION METHODS (same as NormalAIProcessor)

    /**
     * Calculate exchange rate between two currencies using historical rates
     */
    private double calculateExchangeRate(LocalDate transactionDate, String sourceCurrencyCode, String targetCurrencyCode) {
        try {
            // If currencies are the same, no conversion needed
            if (sourceCurrencyCode.equals(targetCurrencyCode)) {
                log.info("🏦 Same currency for bank and dossier ({}), no conversion needed", sourceCurrencyCode);
                return 1.0;
            }

            // Apply date rules to get the effective date for exchange rate lookup
            LocalDate effectiveDate = determineEffectiveDate(transactionDate);
            log.info("🏦 Using effective date for exchange rate: {} (original transaction date: {})", effectiveDate, transactionDate);

            // Get exchange rates for effective date
            ExchangeRate sourceCurrencyRate = null;
            ExchangeRate targetCurrencyRate = null;

            try {
                sourceCurrencyRate = exchangeRateService.getExchangeRate(sourceCurrencyCode, effectiveDate);
                log.info("🏦 Got exchange rate for {}: {}", sourceCurrencyCode, sourceCurrencyRate != null ? sourceCurrencyRate.getRate() : "null");
            } catch (Exception e) {
                log.error("Failed to get exchange rate for {} on date {}: {}", sourceCurrencyCode, effectiveDate, e.getMessage());
                // Use a default fallback rate
                sourceCurrencyRate = createFallbackExchangeRate(sourceCurrencyCode, effectiveDate);
                log.info("🏦 Using fallback exchange rate for {}: {}", sourceCurrencyCode, sourceCurrencyRate.getRate());
            }

            try {
                targetCurrencyRate = exchangeRateService.getExchangeRate(targetCurrencyCode, effectiveDate);
                log.info("🏦 Got exchange rate for {}: {}", targetCurrencyCode, targetCurrencyRate != null ? targetCurrencyRate.getRate() : "null");
            } catch (Exception e) {
                log.error("Failed to get exchange rate for {} on date {}: {}", targetCurrencyCode, effectiveDate, e.getMessage());
                // Use a default fallback rate
                targetCurrencyRate = createFallbackExchangeRate(targetCurrencyCode, effectiveDate);
                log.info("🏦 Using fallback exchange rate for {}: {}", targetCurrencyCode, targetCurrencyRate.getRate());
            }

            // Make sure both rates are not null before calculating conversion
            if (sourceCurrencyRate == null) {
                sourceCurrencyRate = createFallbackExchangeRate(sourceCurrencyCode, effectiveDate);
                log.warn("🏦 Using emergency fallback rate for source currency: {}", sourceCurrencyCode);
            }

            if (targetCurrencyRate == null) {
                targetCurrencyRate = createFallbackExchangeRate(targetCurrencyCode, effectiveDate);
                log.warn("🏦 Using emergency fallback rate for target currency: {}", targetCurrencyCode);
            }

            // Apply currency conversion rules
            double rate = calculateConversionRate(sourceCurrencyCode, targetCurrencyCode, sourceCurrencyRate, targetCurrencyRate);
            log.info("🏦 Final exchange rate: 1 {} = {} {}", sourceCurrencyCode, rate, targetCurrencyCode);
            return rate;
        } catch (Exception e) {
            log.error("💥 Error calculating bank exchange rate: {}", e.getMessage(), e);
            // Return a default conversion rate as fallback
            return getEmergencyFallbackRate(sourceCurrencyCode, targetCurrencyCode);
        }
    }

    /**
     * Determine effective date for exchange rate lookup
     */
    private LocalDate determineEffectiveDate(LocalDate transactionDate) {
        LocalDate today = LocalDate.now();
        LocalDate jan1st2024 = LocalDate.of(2024, 1, 1);

        // Rule 1: If transaction date is before 2024, use Jan 1, 2024
        if (transactionDate.isBefore(jan1st2024)) {
            log.info("🏦 Transaction date {} is before 2024, using Jan 1, 2024 for exchange rate", transactionDate);
            return jan1st2024;
        }

        // Rule 2: If transaction date is after or equal to today, use yesterday
        if (transactionDate.isEqual(today) || transactionDate.isAfter(today)) {
            log.info("🏦 Transaction date {} is today or in the future, using yesterday's date ({}) for exchange rate", transactionDate, today.minusDays(1));
            return today.minusDays(1);
        }

        // Rule 3: Otherwise use the transaction date
        log.info("🏦 Using actual transaction date {} for exchange rate", transactionDate);
        return transactionDate;
    }

    /**
     * Calculate conversion rate between two currencies
     */
    private double calculateConversionRate(String sourceCurrencyCode, String targetCurrencyCode,
                                           ExchangeRate sourceCurrencyRate, ExchangeRate targetCurrencyRate) {
        // Safety check
        if (sourceCurrencyRate == null || targetCurrencyRate == null) {
            log.error("💥 Null exchange rates in bank conversion calculation despite fallbacks");
            return getEmergencyFallbackRate(sourceCurrencyCode, targetCurrencyCode);
        }

        // Case 1: If source currency is USD and target currency is not USD
        if ("USD".equals(sourceCurrencyCode)) {
            log.info("🏦 Case 1: Source currency is USD, using direct USD→{} rate", targetCurrencyCode);
            return targetCurrencyRate.getRate();
        }

        // Case 2: If target currency is USD and source currency is not USD
        if ("USD".equals(targetCurrencyCode)) {
            log.info("🏦 Case 2: Target currency is USD, using inverse of USD→{} rate", sourceCurrencyCode);
            return 1.0 / sourceCurrencyRate.getRate();
        }

        // Case 3: Neither currency is USD
        log.info("🏦 Case 3: Neither currency is USD, calculating cross rate");
        double rate = targetCurrencyRate.getRate() / sourceCurrencyRate.getRate();
        log.info("🏦 Cross rate calculation: {} / {} = {}", targetCurrencyRate.getRate(), sourceCurrencyRate.getRate(), rate);
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
                rate.setRate(3.1); // Added TND fallback
                break;
            default:
                // For unknown currencies, default to 1:1 with USD
                rate.setRate(1.0);
                log.warn("🏦 No fallback rate known for currency {}, using 1.0", currencyCode);
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

    // Keep the existing callBankService method but ensure it extracts currency data properly
    private JsonNode callBankService(Piece piece) {
        try {
            String filename = piece.getFilename();
            String fileId = filename.substring(0, filename.lastIndexOf('.'));

            log.info("🏦 Getting bank statement result for fileId: {}", fileId);

            BankStatementGetResponse bankResponse = bankApiService.getBankStatementResult(fileId);

            if (!bankResponse.isSuccess()) {
                throw new RuntimeException("Bank API GET failed: " + bankResponse.getMessage());
            }

            String jsonResponse = bankResponse.getJsonResponse();
            log.info("✅ Bank API response received, length: {}", jsonResponse.length());

            JsonNode parsedResponse = objectMapper.readTree(jsonResponse);

            // Extract outputText which contains the actual ecritures
            JsonNode outputText = parsedResponse.get("outputText");
            if (outputText != null) {
                String ecrituresJson = outputText.asText();
                JsonNode ecrituresParsed = objectMapper.readTree(ecrituresJson);

                // Use enhanced ecritures extraction
                JsonNode ecrituresNode = extractBankEcritures(ecrituresParsed);

                if (ecrituresNode != null && ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                    log.info("✅ Successfully extracted {} ecritures entries from bank statement", ecrituresNode.size());

                    // ✅ ENSURE CURRENCY DATA IS PROPERLY STRUCTURED
                    JsonNode normalizedEcritures = ensureCurrencyData(ecrituresNode);

                    // Create normalized response that matches normal AI response format
                    ObjectNode normalizedResponse = objectMapper.createObjectNode();
                    normalizedResponse.set("ecritures", normalizedEcritures);
                    normalizedResponse.set("outputText", objectMapper.valueToTree(objectMapper.writeValueAsString(normalizedResponse)));
                    normalizedResponse.put("isBankStatement", true);

                    log.info("🏦 Created normalized response with {} entries for conversion", normalizedEcritures.size());
                    return normalizedResponse;
                } else {
                    log.error("❌ Could not extract ecritures from bank statement");
                    throw new RuntimeException("Invalid bank statement format - no ecritures found");
                }
            }

            return parsedResponse;

        } catch (Exception e) {
            log.error("❌ Bank API GET call failed: {}", e.getMessage());
            throw new RuntimeException("Bank service call failed: " + e.getMessage(), e);
        }
    }

    // ✅ ADD THIS METHOD to ensure currency data is properly structured
    private JsonNode ensureCurrencyData(JsonNode ecrituresNode) {
        ArrayNode normalizedEcritures = objectMapper.createArrayNode();

        for (JsonNode entry : ecrituresNode) {
            ObjectNode normalizedEntry = objectMapper.createObjectNode();

            // Copy all fields
            entry.fields().forEachRemaining(field -> normalizedEntry.set(field.getKey(), field.getValue()));

            // ✅ Ensure currency field uses the expected name "Devise"
            if (entry.has("Devise")) {
                String currency = entry.get("Devise").asText();
                if (currency != null && !currency.trim().isEmpty()) {
                    normalizedEntry.put("Devise", currency.trim());
                    log.debug("🏦 Set currency in normalized bank entry: {}", currency);
                }
            }

            // ✅ Ensure numeric fields are properly formatted
            if (entry.has("DebitAmt")) {
                String debitAmt = entry.get("DebitAmt").asText();
                if (debitAmt != null && !debitAmt.trim().isEmpty()) {
                    normalizedEntry.put("DebitAmt", debitAmt.trim());
                }
            }

            if (entry.has("CreditAmt")) {
                String creditAmt = entry.get("CreditAmt").asText();
                if (creditAmt != null && !creditAmt.trim().isEmpty()) {
                    normalizedEntry.put("CreditAmt", creditAmt.trim());
                }
            }

            normalizedEcritures.add(normalizedEntry);
        }

        return normalizedEcritures;
    }

    private JsonNode extractBankEcritures(JsonNode parsedJson) {
        log.info("🏦 Extracting bank ecritures from: {}", parsedJson.fieldNames());

        // The bank response has Ecritures at the root level with entries arrays
        if (parsedJson.has("Ecritures")) {
            JsonNode ecrituresNode = parsedJson.get("Ecritures");
            log.info("🏦 Found Ecritures node with {} transaction groups", ecrituresNode.size());

            if (ecrituresNode.isArray()) {
                ArrayNode allEntries = objectMapper.createArrayNode();

                // Combine all entries from all transaction groups
                for (JsonNode transactionGroup : ecrituresNode) {
                    if (transactionGroup.has("entries") && transactionGroup.get("entries").isArray()) {
                        JsonNode entries = transactionGroup.get("entries");
                        entries.forEach(allEntries::add);
                        log.debug("🏦 Added {} entries from transaction group", entries.size());
                    }
                }

                if (allEntries.size() > 0) {
                    log.info("🏦 Successfully extracted {} total entries", allEntries.size());
                    return allEntries;
                }
            }
        }

        log.error("❌ Could not extract ecritures from bank statement structure");
        return null;
    }

    @Override
    protected void handleInvalidResponse(Piece piece, int attempt, String jsonResponse) throws InterruptedException {
        if (attempt < batchConfig.getMaxRetries()) {
            log.warn("🏦 Retrying bank piece {} due to invalid AI response (attempt {}/{})",
                    piece.getId(), attempt, batchConfig.getMaxRetries());
            Thread.sleep(batchConfig.getRetryDelayMs());
            processPieceWithRetry(piece, attempt + 1);
        } else {
            log.error("❌ Bank file rejected - invalid AI response after all attempts: {}", jsonResponse);
            rejectPiece(piece, "Invalid AI response after all attempts");
        }
    }

    @Override
    protected void handleProcessingError(Piece piece, int attempt, Exception e) throws InterruptedException {
        if (attempt < batchConfig.getMaxRetries()) {
            log.warn("🏦 Retrying bank piece {} after error (attempt {}/{}): {}",
                    piece.getId(), attempt, batchConfig.getMaxRetries(), e.getMessage());
            Thread.sleep(batchConfig.getRetryDelayMs());
            processPieceWithRetry(piece, attempt + 1);
        } else {
            rejectPiece(piece, "Failed after all attempts: " + e.getMessage());
        }
    }
}