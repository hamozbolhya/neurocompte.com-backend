package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacioli.core.DTO.FactureDataDTO;
import com.pacioli.core.batches.DTO.BaseDTOBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class FactureDataBuilder extends BaseDTOBuilder {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");
    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 10;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public FactureDataDTO buildFactureData(JsonNode entry) {
        FactureDataDTO factureData = new FactureDataDTO();

        try {
            // Set invoice number
            factureData.setInvoiceNumber(extractInvoiceNumber(entry));
            // ✅ IMPORTANT: Make sure this uses the entry parameter
            setInvoiceDate(factureData, entry);
            // Set invoice date
            setInvoiceDate(factureData, entry);

            // Set total amounts
            Double extractedTvaRate = extractTVARate(entry);
            setTotalAmounts(factureData, entry, extractedTvaRate);
            factureData.setTaxRate(resolveTaxRate(extractedTvaRate, factureData));

            // Set currency information
            setCurrencyInformation(factureData, entry);

            // Set conversion information
            setConversionInformation(factureData, entry);

            log.info("✅ Built FactureData DTO: invoiceNumber={}, currency={}",
                    factureData.getInvoiceNumber(), factureData.getDevise());

        } catch (Exception e) {
            log.error("❌ Error building FactureData: {}", e.getMessage());
            // Set minimal safe data
            factureData.setInvoiceNumber("ERROR-" + System.currentTimeMillis());
            factureData.setDevise("USD");
        }

        return factureData;
    }

    private String extractInvoiceNumber(JsonNode entry) {
        // First try to get from FactureNum field
        String factureNum = extractStringSafely(entry, "FactureNum", "");
        if (!factureNum.isEmpty()) {
            log.info("📄 Normal invoice with FactureNum: {}", factureNum);
            return factureNum;
        }

        // For bank statements, try to extract from EcritLib
        String ecritLib = extractStringSafely(entry, "EcritLib", "");
        if (ecritLib.contains("Facture")) {
            String invoiceNum = extractInvoiceNumberFromEcritLib(ecritLib);
            log.info("🏦 Extracted invoice number from EcritLib: {}", invoiceNum);
            return invoiceNum;
        }

        // Generate default
        String defaultInvoice = "BANK-" + System.currentTimeMillis();
        log.info("🏦 Generated bank invoice number: {}", defaultInvoice);
        return defaultInvoice;
    }

    private String extractInvoiceNumberFromEcritLib(String ecritLib) {
        try {
            if (ecritLib.contains("Facture")) {
                String[] parts = ecritLib.split("Facture");
                if (parts.length > 1) {
                    String invoicePart = parts[1].trim();
                    String invoiceNum = invoicePart.replaceAll("[^a-zA-Z0-9]", " ").trim().split(" ")[0];
                    return invoiceNum.isEmpty() ? "BANK-" + System.currentTimeMillis() : invoiceNum;
                }
            }
        } catch (Exception e) {
            log.trace("Error extracting invoice number from EcritLib: {}", e.getMessage());
        }
        return "BANK-" + System.currentTimeMillis();
    }

    private void setInvoiceDate(FactureDataDTO factureData, JsonNode entry) {
        try {
            String dateStr = extractStringSafely(entry, "Date", null);
            if (dateStr != null) {
                LocalDate localDate = parseDate(dateStr);
                factureData.setInvoiceDate(java.sql.Date.valueOf(localDate));
                log.info("📅 Set invoice date: {}", factureData.getInvoiceDate());
            }
        } catch (Exception e) {
            log.error("Failed to set invoice date: {}", e.getMessage());
        }
    }

    private Double extractTVARate(JsonNode entry) {
        try {
            JsonNode tvaNode = entry.get("TVARate");
            if (tvaNode != null && !tvaNode.isNull()) {
                if (tvaNode.isNumber()) {
                    return normalizeTaxRate(tvaNode.asDouble());
                } else {
                    return parseTaxRate(tvaNode.asText());
                }
            }
        } catch (Exception e) {
            log.trace("Error processing TVA rate: {}", e.getMessage());
        }
        return null;
    }

    private Double parseTaxRate(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }

        String normalized = rawValue.trim()
                .replace(',', '.')
                .replace("%", "");
        Matcher matcher = NUMBER_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }

        return normalizeTaxRate(Double.parseDouble(matcher.group()));
    }

    private Double normalizeTaxRate(Double value) {
        if (value == null) {
            return null;
        }

        double normalized = value;
        if (normalized > 0 && normalized <= 1) {
            normalized *= 100;
        }

        while (normalized > 100 && normalized / 100 <= 100) {
            normalized /= 100;
        }

        return normalized;
    }

    private void setTotalAmounts(FactureDataDTO factureData, JsonNode entry, Double tvaRate) {
        // Set total TTC
        if (entry.has("TotalTTC")) {
            factureData.setTotalTTC(parseDoubleSafely(entry, "TotalTTC"));
        } else {
            Double debit = parseDoubleSafely(entry, "DebitAmt");
            Double credit = parseDoubleSafely(entry, "CreditAmt");
            factureData.setTotalTTC(Math.max(debit, credit));
        }

        // Set total HT
        if (entry.has("TotalHT")) {
            factureData.setTotalHT(parseDoubleSafely(entry, "TotalHT"));
        }

        if (entry.has("TotalTVA")) {
            factureData.setTotalTVA(parseDoubleSafely(entry, "TotalTVA"));
        }

        if (factureData.getTotalTTC() != null && tvaRate != null) {
            BigDecimal totalTTC = toBigDecimal(factureData.getTotalTTC());
            BigDecimal totalTVA = calculateTvaFromTtc(totalTTC, toBigDecimal(tvaRate));
            BigDecimal totalHT = calculateHtFromTtc(totalTTC, toBigDecimal(tvaRate));
            factureData.setTotalTVA(toMoneyDouble(totalTVA));
            factureData.setTotalHT(toMoneyDouble(totalHT));
        } else if (factureData.getTotalHT() == null && factureData.getTotalTTC() != null && factureData.getTotalTVA() != null) {
            BigDecimal totalHT = toBigDecimal(factureData.getTotalTTC()).subtract(toBigDecimal(factureData.getTotalTVA()));
            factureData.setTotalHT(toMoneyDouble(totalHT));
        }

        // Set total TVA
        if (factureData.getTotalTTC() != null && factureData.getTotalHT() != null && factureData.getTotalTVA() == null) {
            BigDecimal totalTVA = toBigDecimal(factureData.getTotalTTC()).subtract(toBigDecimal(factureData.getTotalHT()));
            factureData.setTotalTVA(toMoneyDouble(totalTVA));
        }
    }

    private Double resolveTaxRate(Double extractedRate, FactureDataDTO factureData) {
        Double computedRate = computeTaxRateFromAmounts(factureData);
        if (computedRate == null) {
            return extractedRate;
        }

        if (extractedRate == null ||
                (extractedRate == 0 && computedRate > 0) ||
                (extractedRate > 30 && computedRate <= 30) ||
                (Math.abs(extractedRate - computedRate) > 1 && computedRate <= 30)) {
            log.warn("⚠️ Corrected suspicious TVA rate from {} to {} using invoice totals",
                    extractedRate, computedRate);
            return computedRate;
        }

        return extractedRate;
    }

    private Double computeTaxRateFromAmounts(FactureDataDTO factureData) {
        if (factureData.getTotalTVA() != null && factureData.getTotalTTC() != null && factureData.getTotalTTC() != 0) {
            return normalizeTaxRate(toBigDecimal(factureData.getTotalTVA())
                    .multiply(ONE_HUNDRED)
                    .divide(toBigDecimal(factureData.getTotalTTC()), RATE_SCALE, RoundingMode.HALF_UP)
                    .doubleValue());
        }

        if (factureData.getTotalTTC() != null && factureData.getTotalHT() != null && factureData.getTotalTTC() != 0) {
            BigDecimal totalTTC = toBigDecimal(factureData.getTotalTTC());
            BigDecimal totalHT = toBigDecimal(factureData.getTotalHT());
            if (totalHT.compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            return normalizeTaxRate(totalTTC
                    .multiply(ONE_HUNDRED)
                    .divide(totalHT, RATE_SCALE, RoundingMode.HALF_UP)
                    .subtract(ONE_HUNDRED)
                    .doubleValue());
        }

        return null;
    }

    private BigDecimal calculateHtFromTtc(BigDecimal totalTTC, BigDecimal taxRate) {
        BigDecimal divisor = BigDecimal.ONE.add(taxRate.divide(ONE_HUNDRED, RATE_SCALE, RoundingMode.HALF_UP));
        return totalTTC.divide(divisor, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTvaFromTtc(BigDecimal totalTTC, BigDecimal taxRate) {
        return totalTTC
                .multiply(taxRate)
                .divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Double value) {
        return BigDecimal.valueOf(value == null ? 0 : value);
    }

    private Double toMoneyDouble(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    private void setCurrencyInformation(FactureDataDTO factureData, JsonNode entry) {
        if (entry.has("Devise")) {
            String rawCurrency = entry.get("Devise").asText();
            String normalizedCurrency = normalizeCurrencyCode.normalizeCurrencyCode(rawCurrency);
            factureData.setDevise(normalizedCurrency);
            factureData.setOriginalCurrency(normalizedCurrency);
        }

        if (entry.has("OriginalDevise")) {
            String rawCurrency = entry.get("OriginalDevise").asText();
            factureData.setOriginalCurrency(normalizeCurrencyCode.normalizeCurrencyCode(rawCurrency));
        }

        if (entry.has("ConvertedDevise") || entry.has("Devise")) {
            String rawCurrency = entry.has("ConvertedDevise") ? entry.get("ConvertedDevise").asText() : entry.get("Devise").asText();
            factureData.setConvertedCurrency(normalizeCurrencyCode.normalizeCurrencyCode(rawCurrency));
        }
    }

    private void setConversionInformation(FactureDataDTO factureData, JsonNode entry) {
        if (entry.has("ExchangeRate")) {
            double rate = parseDoubleSafely(entry, "ExchangeRate");
            factureData.setExchangeRate(rate);

            if (factureData.getTotalTTC() != null) {
                factureData.setConvertedTotalTTC(toMoneyDouble(toBigDecimal(factureData.getTotalTTC()).multiply(toBigDecimal(rate))));
            }
            if (factureData.getTotalHT() != null) {
                factureData.setConvertedTotalHT(toMoneyDouble(toBigDecimal(factureData.getTotalHT()).multiply(toBigDecimal(rate))));
            }
            if (factureData.getTotalTVA() != null) {
                factureData.setConvertedTotalTVA(toMoneyDouble(toBigDecimal(factureData.getTotalTVA()).multiply(toBigDecimal(rate))));
            }
        }

        if (entry.has("ExchangeRateDate")) {
            try {
                factureData.setExchangeRateDate(LocalDate.parse(entry.get("ExchangeRateDate").asText()));
            } catch (Exception e) {
                log.trace("Error parsing exchange rate date: {}", e.getMessage());
            }
        }

        // Set USD equivalents
        if (entry.has("UsdTotalTTC")) {
            factureData.setUsdTotalTTC(parseDoubleSafely(entry, "UsdTotalTTC"));
        }
        if (entry.has("UsdTotalHT")) {
            factureData.setUsdTotalHT(parseDoubleSafely(entry, "UsdTotalHT"));
        }
        if (entry.has("UsdTotalTVA")) {
            factureData.setUsdTotalTVA(parseDoubleSafely(entry, "UsdTotalTVA"));
        }
    }
}
