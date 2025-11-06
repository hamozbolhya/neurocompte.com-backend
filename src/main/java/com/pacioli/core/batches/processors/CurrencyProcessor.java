package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacioli.core.batches.DTO.BaseDTOBuilder;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Piece;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CurrencyProcessor extends BaseDTOBuilder {

    @Autowired
    private CurrencyConverter currencyConverter;

    public void processPieceCurrencyAndAmount(Piece piece, JsonNode ecrituresNode, JsonNode firstEntry) {
        try {
            double originalAmount = calculateLargestAmount(ecrituresNode);
            piece.setAiAmount(originalAmount);

            String invoiceCurrencyCode = extractAndNormalizeCurrency(firstEntry);
            piece.setAiCurrency(invoiceCurrencyCode);

            log.info("💰 Set AI data for piece {}: amount={}, currency={}",
                    piece.getId(), originalAmount, invoiceCurrencyCode);
        } catch (Exception e) {
            log.error("❌ Error processing currency and amount for piece {}: {}", piece.getId(), e.getMessage());
            // Set safe defaults
            piece.setAiAmount(0.0);
            piece.setAiCurrency("USD");
        }
    }

    public JsonNode convertCurrencyIfNeeded(JsonNode ecrituresNode, Piece piece, JsonNode firstEntry) {
        try {
            return currencyConverter.convertCurrencyIfNeeded(ecrituresNode, piece, firstEntry);
        } catch (Exception e) {
            log.error("❌ Currency conversion failed for piece {}, using original data: {}",
                    piece.getId(), e.getMessage());
            return ecrituresNode; // Return original data if conversion fails
        }
    }

    private Double calculateLargestAmount(JsonNode ecritures) {
        double maxAmount = 0.0;
        for (JsonNode entry : ecritures) {
            double debit = parseDoubleSafely(entry, "DebitAmt");
            double credit = parseDoubleSafely(entry, "CreditAmt");
            maxAmount = Math.max(maxAmount, Math.max(debit, credit));
        }
        return maxAmount;
    }

    private String extractAndNormalizeCurrency(JsonNode entry) {
        String rawCurrency = extractStringSafely(entry, "Devise", "USD");
        if ("USD".equals(rawCurrency)) {
            log.info("⚠️ No currency information found in AI response, using default USD");
            return "USD";
        }

        String normalized = normalizeCurrencyCode.normalizeCurrencyCode(rawCurrency);
        log.info("💰 Extracted currency: {} -> {}", rawCurrency, normalized);
        return normalized;
    }

    public String getDossierCurrencyCode(Dossier dossier) {
        return dossier.getCurrency() != null ?
                normalizeCurrencyCode.normalizeCurrencyCode(dossier.getCurrency().getCode()) : "MAD";
    }
}