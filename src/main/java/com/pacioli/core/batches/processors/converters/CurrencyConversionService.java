package com.pacioli.core.batches.processors.converters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pacioli.core.models.Piece;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CurrencyConversionService {

    @Autowired
    private ObjectMapper objectMapper;

    public JsonNode convertEcrituresCurrency(JsonNode ecrituresNode, Piece piece) {
        if (shouldSkipConversion(piece)) {
            log.info("💱 No currency conversion needed for piece {}", piece.getId());
            return ecrituresNode;
        }

        log.info("💱 Converting ecritures for piece {}: {} → {}, rate: {}",
                piece.getId(), piece.getAiCurrency(), piece.getConvertedCurrency(), piece.getExchangeRate());

        return applyConversionToEcritures(ecrituresNode, piece);
    }

    private boolean shouldSkipConversion(Piece piece) {
        return piece.getAiCurrency() == null ||
                piece.getConvertedCurrency() == null ||
                piece.getAiCurrency().equals(piece.getConvertedCurrency()) ||
                piece.getExchangeRate() == null ||
                piece.getExchangeRate() == 1.0;
    }

    private JsonNode applyConversionToEcritures(JsonNode ecrituresNode, Piece piece) {
        ArrayNode convertedEcritures = objectMapper.createArrayNode();
        double exchangeRate = piece.getExchangeRate();
        String sourceCurrency = piece.getAiCurrency();
        String targetCurrency = piece.getConvertedCurrency();

        for (JsonNode entry : ecrituresNode) {
            ObjectNode convertedEntry = createConvertedEntry(entry, sourceCurrency, targetCurrency,
                    exchangeRate, piece);
            convertedEcritures.add(convertedEntry);
        }

        log.info("💱 Converted {} ecritures entries", convertedEcritures.size());
        return convertedEcritures;
    }

    private ObjectNode createConvertedEntry(JsonNode entry, String sourceCurrency, String targetCurrency,
                                            double exchangeRate, Piece piece) {
        ObjectNode convertedEntry = objectMapper.createObjectNode();

        // Copy all original fields
        entry.fields().forEachRemaining(field -> convertedEntry.set(field.getKey(), field.getValue()));

        // Check if this is a transaction group
        if (entry.has("isTransactionGroup") && entry.get("isTransactionGroup").asBoolean() &&
                entry.has("entries") && entry.get("entries").isArray()) {

            // Convert each entry in the transaction group
            ArrayNode convertedEntries = objectMapper.createArrayNode();
            JsonNode originalEntries = entry.get("entries");

            for (JsonNode originalEntry : originalEntries) {
                ObjectNode convertedIndividualEntry = convertIndividualEntry(
                        originalEntry, sourceCurrency, targetCurrency, exchangeRate, piece
                );
                convertedEntries.add(convertedIndividualEntry);
            }

            convertedEntry.set("entries", convertedEntries);

        } else {
            // Regular entry conversion
            return convertIndividualEntry(entry, sourceCurrency, targetCurrency, exchangeRate, piece);
        }

        return convertedEntry;
    }

    private ObjectNode convertIndividualEntry(JsonNode entry, String sourceCurrency, String targetCurrency,
                                              double exchangeRate, Piece piece) {
        ObjectNode convertedEntry = objectMapper.createObjectNode();

        // Copy all original fields
        entry.fields().forEachRemaining(field -> convertedEntry.set(field.getKey(), field.getValue()));

        // Get original amounts with null safety
        double debitAmt = parseDoubleSafely(entry.get("DebitAmt") != null ?
                entry.get("DebitAmt").asText() : "0");
        double creditAmt = parseDoubleSafely(entry.get("CreditAmt") != null ?
                entry.get("CreditAmt").asText() : "0");

        // Apply conversion
        double convertedDebitAmt = debitAmt * exchangeRate;
        double convertedCreditAmt = creditAmt * exchangeRate;

        // Store conversion metadata
        convertedEntry.put("OriginalDebitAmt", Math.round(debitAmt * 100.0) / 100.0);
        convertedEntry.put("OriginalCreditAmt", Math.round(creditAmt * 100.0) / 100.0);
        convertedEntry.put("OriginalDevise", sourceCurrency);
        convertedEntry.put("DebitAmt", Math.round(convertedDebitAmt * 100.0) / 100.0);
        convertedEntry.put("CreditAmt", Math.round(convertedCreditAmt * 100.0) / 100.0);
        convertedEntry.put("Devise", targetCurrency);
        convertedEntry.put("ExchangeRate", exchangeRate);

        if (piece.getExchangeRateDate() != null) {
            convertedEntry.put("ExchangeRateDate", piece.getExchangeRateDate().toString());
        }

        return convertedEntry;
    }


    private double parseDoubleSafely(String value) {
        if (value == null || value.trim().isEmpty()) return 0.0;
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            log.trace("Error parsing double: {}", value);
            return 0.0;
        }
    }
}