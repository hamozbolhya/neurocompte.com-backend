package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.ExchangeRate;
import com.pacioli.core.models.Piece;
import com.pacioli.core.services.ExchangeRateService;
import com.pacioli.core.utils.NormalizeCurrencyCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class CurrencyConverter {

    @Autowired private ExchangeRateService exchangeRateService;
    @Autowired private NormalizeCurrencyCode normalizeCurrencyCode;
    @Autowired private ObjectMapper objectMapper;

    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-d"),
            DateTimeFormatter.ofPattern("yyyy-M-dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy-dd-MM")
    );

    public JsonNode convertCurrencyIfNeeded(JsonNode ecrituresNode, Piece piece, JsonNode firstEntry) {
        String invoiceCurrencyCode = piece.getAiCurrency();
        String dossierCurrencyCode = getDossierCurrencyCode(piece.getDossier());

        if (!invoiceCurrencyCode.equals(dossierCurrencyCode)) {
            log.info("💱 Currency conversion needed: {} to {}", invoiceCurrencyCode, dossierCurrencyCode);

            String invoiceDateStr = firstEntry.get("Date").asText();
            LocalDate invoiceDate = parseDate(invoiceDateStr);

            return convertCurrencyValues(ecrituresNode, invoiceDate, invoiceCurrencyCode, dossierCurrencyCode, piece);
        }

        log.info("💱 No currency conversion needed");
        return ecrituresNode;
    }

    private JsonNode convertCurrencyValues(JsonNode ecrituresNode, LocalDate invoiceDate,
                                           String invoiceCurrencyCode, String dossierCurrencyCode, Piece piece) {
        double exchangeRate = calculateExchangeRate(invoiceDate, invoiceCurrencyCode, dossierCurrencyCode);
        LocalDate effectiveExchangeDate = determineEffectiveDate(invoiceDate);

        piece.setExchangeRate(exchangeRate);
        piece.setConvertedCurrency(dossierCurrencyCode);
        piece.setExchangeRateDate(effectiveExchangeDate);

        return convertEcrituresCurrency(ecrituresNode, invoiceCurrencyCode, dossierCurrencyCode, exchangeRate, effectiveExchangeDate);
    }

    private JsonNode convertEcrituresCurrency(JsonNode ecrituresNode, String invoiceCurrencyCode,
                                              String dossierCurrencyCode, double exchangeRate, LocalDate effectiveDate) {
        ArrayNode convertedEcritures = objectMapper.createArrayNode();

        for (JsonNode entry : ecrituresNode) {
            ObjectNode convertedEntry = objectMapper.createObjectNode();
            entry.fields().forEachRemaining(field -> convertedEntry.set(field.getKey(), field.getValue()));

            double debitAmt = entry.get("DebitAmt").asDouble();
            double creditAmt = entry.get("CreditAmt").asDouble();

            double convertedDebitAmt = debitAmt * exchangeRate;
            double convertedCreditAmt = creditAmt * exchangeRate;

            // Store original and converted values
            convertedEntry.put("OriginalDebitAmt", debitAmt);
            convertedEntry.put("OriginalCreditAmt", creditAmt);
            convertedEntry.put("OriginalDevise", invoiceCurrencyCode);
            convertedEntry.put("DebitAmt", Math.round(convertedDebitAmt * 100.0) / 100.0);
            convertedEntry.put("CreditAmt", Math.round(convertedCreditAmt * 100.0) / 100.0);
            convertedEntry.put("Devise", dossierCurrencyCode);
            convertedEntry.put("ExchangeRate", exchangeRate);
            convertedEntry.put("ExchangeRateDate", effectiveDate.toString());

            // Calculate USD equivalents if needed
            addUSDEquivalents(convertedEntry, debitAmt, creditAmt, invoiceCurrencyCode, effectiveDate);

            convertedEcritures.add(convertedEntry);
        }

        return convertedEcritures;
    }

    private void addUSDEquivalents(ObjectNode entry, double debitAmt, double creditAmt,
                                   String invoiceCurrencyCode, LocalDate effectiveDate) {
        if ("USD".equals(invoiceCurrencyCode)) {
            entry.put("UsdDebitAmt", Math.round(debitAmt * 100.0) / 100.0);
            entry.put("UsdCreditAmt", Math.round(creditAmt * 100.0) / 100.0);
        } else {
            try {
                ExchangeRate usdRate = exchangeRateService.getExchangeRate(invoiceCurrencyCode, effectiveDate);
                if (usdRate != null) {
                    double usdDebitAmt = debitAmt / usdRate.getRate();
                    double usdCreditAmt = creditAmt / usdRate.getRate();
                    entry.put("UsdDebitAmt", Math.round(usdDebitAmt * 100.0) / 100.0);
                    entry.put("UsdCreditAmt", Math.round(usdCreditAmt * 100.0) / 100.0);
                }
            } catch (Exception e) {
                log.warn("Could not calculate USD equivalents: {}", e.getMessage());
            }
        }
    }

    // ==================== EXCHANGE RATE CALCULATION ====================

    private double calculateExchangeRate(LocalDate invoiceDate, String invoiceCurrencyCode, String dossierCurrencyCode) {
        if (invoiceCurrencyCode.equals(dossierCurrencyCode)) {
            return 1.0;
        }

        LocalDate effectiveDate = determineEffectiveDate(invoiceDate);

        ExchangeRate invoiceRate = getExchangeRateWithFallback(invoiceCurrencyCode, effectiveDate);
        ExchangeRate dossierRate = getExchangeRateWithFallback(dossierCurrencyCode, effectiveDate);

        return calculateConversionRate(invoiceCurrencyCode, dossierCurrencyCode, invoiceRate, dossierRate);
    }

    private ExchangeRate getExchangeRateWithFallback(String currencyCode, LocalDate date) {
        try {
            ExchangeRate rate = exchangeRateService.getExchangeRate(currencyCode, date);
            log.info("💱 Got exchange rate for {}: {}", currencyCode, rate);
            return rate;
        } catch (Exception e) {
            log.warn("⚠️ Using fallback exchange rate for {}: {}", currencyCode, e.getMessage());
            return createFallbackExchangeRate(currencyCode, date);
        }
    }

    private ExchangeRate createFallbackExchangeRate(String currencyCode, LocalDate date) {
        ExchangeRate rate = new ExchangeRate();
        rate.setCurrencyCode(currencyCode);
        rate.setDate(date);
        rate.setBaseCurrency("USD");

        Map<String, Double> fallbackRates = Map.of(
                "USD", 1.0,
                "EUR", 1.1,
                "MAD", 10.0,
                "GBP", 1.3
        );

        rate.setRate(fallbackRates.getOrDefault(currencyCode, 1.0));
        return rate;
    }

    private double calculateConversionRate(String invoiceCurrency, String dossierCurrency,
                                           ExchangeRate invoiceRate, ExchangeRate dossierRate) {
        if ("USD".equals(invoiceCurrency)) {
            return dossierRate.getRate();
        }
        if ("USD".equals(dossierCurrency)) {
            return 1.0 / invoiceRate.getRate();
        }
        return dossierRate.getRate() / invoiceRate.getRate();
    }

    private LocalDate determineEffectiveDate(LocalDate invoiceDate) {
        LocalDate today = LocalDate.now();
        LocalDate jan1st2024 = LocalDate.of(2024, 1, 1);

        if (invoiceDate.isBefore(jan1st2024)) {
            return jan1st2024;
        }
        if (invoiceDate.isEqual(today) || invoiceDate.isAfter(today)) {
            return today.minusDays(1);
        }
        return invoiceDate;
    }

    // ==================== UTILITY METHODS ====================

    private String getDossierCurrencyCode(Dossier dossier) {
        return dossier.getCurrency() != null ?
                normalizeCurrencyCode.normalizeCurrencyCode(dossier.getCurrency().getCode()) : "MAD";
    }

    private LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // Continue to next formatter
            }
        }
        log.warn("❌ Could not parse date: {}. Using current date.", dateStr);
        return LocalDate.now();
    }
}