package com.pacioli.core.batches.DTO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pacioli.core.DTO.EcrituresDTO2;
import com.pacioli.core.DTO.LineDTO;
import com.pacioli.core.DTO.PieceDTO;
import com.pacioli.core.batches.processors.CurrencyProcessor;
import com.pacioli.core.batches.processors.EcritureBuilder;
import com.pacioli.core.batches.processors.FactureDataBuilder;
import com.pacioli.core.models.ExchangeRate;
import com.pacioli.core.models.Piece;
import com.pacioli.core.services.ExchangeRateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
public class DTOBuilder {

    @Autowired
    private CurrencyProcessor currencyProcessor;

    @Autowired
    private FactureDataBuilder factureDataBuilder;

    @Autowired
    private EcritureBuilder ecritureBuilder;

    @Autowired
    private BaseDTOBuilder baseDTOBuilder;

    @Autowired
    private ExchangeRateService exchangeRateService;

    public PieceDTO buildPieceDTO(Piece piece, JsonNode aiResponse) throws JsonProcessingException {
        try {
            String responseText = aiResponse.asText();
            JsonNode parsedJson = baseDTOBuilder.objectMapper.readTree(responseText);

            log.info("🔍 Parsed JSON structure - Root keys: {}", parsedJson.fieldNames());

            JsonNode ecrituresNode = baseDTOBuilder.findEcrituresNode(parsedJson);

            if (ecrituresNode == null || !ecrituresNode.isArray() || ecrituresNode.size() == 0) {
                log.error("❌ No valid ecritures array found in AI response");
                throw new IllegalArgumentException("No valid ecritures array found in AI response");
            }

            log.info("✅ Found {} ecritures entries", ecrituresNode.size());

            JsonNode firstEntry = ecrituresNode.get(0);

            // ✅ USE EXISTING CONVERSION DATA FROM PIECE ENTITY
            // The conversion has already been calculated and stored in the piece entity
            // Now we just need to apply it to the ecritures

            JsonNode convertedEcritures = applyExistingConversion(ecrituresNode, piece, firstEntry);

            return buildPieceDTOFromData(piece, convertedEcritures, firstEntry);

        } catch (Exception e) {
            log.error("💥 Error building PieceDTO: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build PieceDTO from AI response", e);
        }
    }

    /**
     * Apply existing conversion data from piece entity to ecritures
     */
    private JsonNode applyExistingConversion(JsonNode ecrituresNode, Piece piece, JsonNode firstEntry) {
        // If no conversion was applied, return original data
        if (piece.getExchangeRate() == null || piece.getExchangeRate() == 1.0 ||
                piece.getAiCurrency() == null || piece.getAiCurrency().equals(piece.getConvertedCurrency())) {
            log.info("💱 No conversion to apply, using original ecritures");
            return ecrituresNode;
        }

        log.info("💱 Applying existing conversion to ecritures: {} → {}, rate: {}",
                piece.getAiCurrency(), piece.getConvertedCurrency(), piece.getExchangeRate());

        ArrayNode convertedEcritures = baseDTOBuilder.objectMapper.createArrayNode();
        double exchangeRate = piece.getExchangeRate();
        String sourceCurrency = piece.getAiCurrency();
        String targetCurrency = piece.getConvertedCurrency();

        for (JsonNode entry : ecrituresNode) {
            ObjectNode convertedEntry = baseDTOBuilder.objectMapper.createObjectNode();

            // Copy all fields
            entry.fields().forEachRemaining(field -> convertedEntry.set(field.getKey(), field.getValue()));

            // Get original amounts
            double debitAmt = baseDTOBuilder.parseDoubleSafely(entry, "DebitAmt");
            double creditAmt = baseDTOBuilder.parseDoubleSafely(entry, "CreditAmt");

            // Apply conversion
            double convertedDebitAmt = debitAmt * exchangeRate;
            double convertedCreditAmt = creditAmt * exchangeRate;

            // Store original and converted values
            convertedEntry.put("OriginalDebitAmt", debitAmt);
            convertedEntry.put("OriginalCreditAmt", creditAmt);
            convertedEntry.put("OriginalDevise", sourceCurrency);
            convertedEntry.put("DebitAmt", Math.round(convertedDebitAmt * 100.0) / 100.0);
            convertedEntry.put("CreditAmt", Math.round(convertedCreditAmt * 100.0) / 100.0);
            convertedEntry.put("Devise", targetCurrency);
            convertedEntry.put("ExchangeRate", exchangeRate);

            if (piece.getExchangeRateDate() != null) {
                convertedEntry.put("ExchangeRateDate", piece.getExchangeRateDate().toString());
            }


            // Calculate USD equivalents if needed
            addUSDEquivalents(convertedEntry, debitAmt, creditAmt, sourceCurrency, piece.getExchangeRateDate());

            convertedEcritures.add(convertedEntry);
        }

        return convertedEcritures;
    }

    private void addUSDEquivalents(ObjectNode entry, double debitAmt, double creditAmt,
                                   String sourceCurrency, LocalDate exchangeDate) {
        if ("USD".equals(sourceCurrency)) {
            entry.put("UsdDebitAmt", Math.round(debitAmt * 100.0) / 100.0);
            entry.put("UsdCreditAmt", Math.round(creditAmt * 100.0) / 100.0);
        } else {
            try {
                ExchangeRate usdRate = exchangeRateService.getExchangeRate(sourceCurrency, exchangeDate);
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


    private PieceDTO buildPieceDTOFromData(Piece piece, JsonNode convertedEcritures, JsonNode firstEntry) {
        PieceDTO pieceDTO = new PieceDTO();

        try {
            pieceDTO.setId(piece.getId());
            pieceDTO.setFilename(piece.getFilename());
            pieceDTO.setType(piece.getType());
            pieceDTO.setUploadDate(piece.getUploadDate());
            pieceDTO.setAmount(calculateLargestAmount(convertedEcritures));
            pieceDTO.setFactureData(factureDataBuilder.buildFactureData(firstEntry));
            pieceDTO.setEcritures(ecritureBuilder.buildEcritures(convertedEcritures));
            pieceDTO.setDossierId(piece.getDossier().getId());
            pieceDTO.setDossierName(piece.getDossier().getName());
            pieceDTO.setIsDuplicate(piece.getIsDuplicate());
            pieceDTO.setIsForced(piece.getIsForced());

            // Set AI and currency information
            pieceDTO.setAiCurrency(piece.getAiCurrency());
            pieceDTO.setAiAmount(piece.getAiAmount());
            pieceDTO.setOriginalCurrency(piece.getAiCurrency());
            pieceDTO.setDossierCurrency(currencyProcessor.getDossierCurrencyCode(piece.getDossier()));

            if (piece.getExchangeRate() != null) {
                pieceDTO.setExchangeRate(piece.getExchangeRate());
                pieceDTO.setConvertedCurrency(piece.getConvertedCurrency());
                pieceDTO.setExchangeRateDate(piece.getExchangeRateDate());
            }

            if (piece.getOriginalPiece() != null) {
                pieceDTO.setOriginalPieceId(piece.getOriginalPiece().getId());
                pieceDTO.setOriginalPieceName(piece.getOriginalPiece().getOriginalFileName());
            }

        } catch (Exception e) {
            log.error("❌ Error building PieceDTO from data: {}", e.getMessage());
            // Set minimal safe data
            pieceDTO.setId(piece.getId());
            pieceDTO.setFilename(piece.getFilename());
            pieceDTO.setDossierId(piece.getDossier().getId());
        }

        return pieceDTO;
    }

    public JsonNode createConvertedResponseNode(PieceDTO pieceDTO, JsonNode originalResponse) {
        try {
            String responseText = originalResponse.asText();
            JsonNode parsedOriginal = baseDTOBuilder.objectMapper.readTree(responseText);

            ObjectNode convertedRoot = baseDTOBuilder.objectMapper.createObjectNode();
            ArrayNode ecrituresArray = baseDTOBuilder.objectMapper.createArrayNode();

            if (pieceDTO.getEcritures() != null && !pieceDTO.getEcritures().isEmpty()) {
                EcrituresDTO2 ecriture = pieceDTO.getEcritures().get(0);

                for (LineDTO line : ecriture.getLines()) {
                    ObjectNode entryNode = buildConvertedEntryNode(pieceDTO, ecriture, line);
                    ecrituresArray.add(entryNode);
                }
            }

            convertedRoot.set("ecritures", ecrituresArray);
            return baseDTOBuilder.objectMapper.valueToTree(baseDTOBuilder.objectMapper.writeValueAsString(convertedRoot));

        } catch (Exception e) {
            log.error("💥 Error creating converted response node: {}", e.getMessage(), e);
            return originalResponse;
        }
    }

    private ObjectNode buildConvertedEntryNode(PieceDTO pieceDTO, EcrituresDTO2 ecriture, LineDTO line) {
        ObjectNode entryNode = baseDTOBuilder.objectMapper.createObjectNode();

        // Set basic fields
        entryNode.put("Date", ecriture.getEntryDate());
        entryNode.put("JournalCode", ecriture.getJournal().getName());
        entryNode.put("JournalLib", ecriture.getJournal().getType());

        // Handle missing FactureNum for bank statements
        String factureNum = pieceDTO.getFactureData() != null ?
                pieceDTO.getFactureData().getInvoiceNumber() : "BANK-STMT";
        entryNode.put("FactureNum", factureNum);

        entryNode.put("CompteNum", line.getAccount().getAccount());
        entryNode.put("CompteLib", line.getAccount().getLabel());
        entryNode.put("EcritLib", line.getLabel());

        // Set amount fields - check if conversion happened
        if (line.getOriginalDebit() != null && line.getConvertedDebit() != null && !line.getOriginalDebit().equals(line.getConvertedDebit())) {
            // Conversion happened
            entryNode.put("OriginalDebitAmt", line.getOriginalDebit());
            entryNode.put("DebitAmt", line.getConvertedDebit());
            entryNode.put("OriginalCreditAmt", line.getOriginalCredit());
            entryNode.put("CreditAmt", line.getConvertedCredit());
            entryNode.put("OriginalDevise", line.getOriginalCurrency());
            entryNode.put("Devise", line.getConvertedCurrency());
        } else {
            // No conversion
            entryNode.put("DebitAmt", line.getDebit());
            entryNode.put("CreditAmt", line.getCredit());
            entryNode.put("Devise", line.getOriginalCurrency() != null ? line.getOriginalCurrency() :
                    (line.getConvertedCurrency() != null ? line.getConvertedCurrency() : "USD"));
        }

        // Set exchange rate info if available
        if (line.getExchangeRate() != null) {
            entryNode.put("ExchangeRate", line.getExchangeRate());
        }
        if (line.getExchangeRateDate() != null) {
            entryNode.put("ExchangeRateDate", line.getExchangeRateDate().toString());
        }

        // Set USD amounts if available
        if (line.getUsdDebit() != null) {
            entryNode.put("UsdDebitAmt", line.getUsdDebit());
        }
        if (line.getUsdCredit() != null) {
            entryNode.put("UsdCreditAmt", line.getUsdCredit());
        }

        // Set TVA rate
        if (pieceDTO.getFactureData() != null && pieceDTO.getFactureData().getTaxRate() != null) {
            entryNode.put("TVARate", pieceDTO.getFactureData().getTaxRate().toString());
        } else {
            entryNode.put("TVARate", "");
        }

        return entryNode;
    }

    private Double calculateLargestAmount(JsonNode ecritures) {
        double maxAmount = 0.0;
        for (JsonNode entry : ecritures) {
            double debit = baseDTOBuilder.parseDoubleSafely(entry, "DebitAmt");
            double credit = baseDTOBuilder.parseDoubleSafely(entry, "CreditAmt");
            maxAmount = Math.max(maxAmount, Math.max(debit, credit));
        }
        return maxAmount;
    }
}
