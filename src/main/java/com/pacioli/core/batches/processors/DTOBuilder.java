package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pacioli.core.DTO.*;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Piece;
import com.pacioli.core.utils.NormalizeCurrencyCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
@Slf4j
public class DTOBuilder {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NormalizeCurrencyCode normalizeCurrencyCode;
    @Autowired
    private CurrencyConverter currencyConverter;

    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-d"),
            DateTimeFormatter.ofPattern("yyyy-M-dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy-dd-MM")
    );

    public PieceDTO buildPieceDTO(Piece piece, JsonNode aiResponse) throws JsonProcessingException {
        try {
            String responseText = aiResponse.asText();
            JsonNode parsedJson = objectMapper.readTree(responseText);

            log.info("🔍 Parsed JSON structure - Root keys: {}", parsedJson.fieldNames());

            JsonNode ecrituresNode = findEcrituresNode(parsedJson);

            if (ecrituresNode == null || !ecrituresNode.isArray() || ecrituresNode.size() == 0) {
                log.error("❌ No valid ecritures array found in AI response");
                log.debug("❌ Available keys in parsed JSON: {}", parsedJson.fieldNames());
                throw new IllegalArgumentException("No valid ecritures array found in AI response");
            }

            log.info("✅ Found {} ecritures entries", ecrituresNode.size());

            JsonNode firstEntry = ecrituresNode.get(0);
            processPieceCurrencyAndAmount(piece, ecrituresNode, firstEntry);

            JsonNode convertedEcritures = currencyConverter.convertCurrencyIfNeeded(ecrituresNode, piece, firstEntry);

            return buildPieceDTOFromData(piece, convertedEcritures, firstEntry);

        } catch (Exception e) {
            log.error("💥 Error building PieceDTO: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build PieceDTO from AI response", e);
        }
    }

    private void processPieceCurrencyAndAmount(Piece piece, JsonNode ecrituresNode, JsonNode firstEntry) {
        double originalAmount = calculateLargestAmount(ecrituresNode);
        piece.setAiAmount(originalAmount);

        String invoiceCurrencyCode = extractAndNormalizeCurrency(firstEntry);
        piece.setAiCurrency(invoiceCurrencyCode);

        log.info("💰 Set AI data for piece {}: amount={}, currency={}",
                piece.getId(), originalAmount, invoiceCurrencyCode);
    }

    private String extractAndNormalizeCurrency(JsonNode entry) {
        // ✅ FIX: Check if Devise field exists and is not null
        if (entry.has("Devise") && !entry.get("Devise").isNull() && !entry.get("Devise").asText().isEmpty()) {
            String rawCurrency = entry.get("Devise").asText();
            String normalized = normalizeCurrencyCode.normalizeCurrencyCode(rawCurrency);
            log.info("💰 Extracted currency: {} -> {}", rawCurrency, normalized);
            return normalized;
        }
        log.info("⚠️ No currency information found in AI response, using default USD");
        return "USD";
    }

    private JsonNode findEcrituresNode(JsonNode parsedJson) {
        // First check for normal format
        if (parsedJson.has("ecritures")) return parsedJson.get("ecritures");
        if (parsedJson.has("Ecritures")) {
            JsonNode ecrituresNode = parsedJson.get("Ecritures");

            // Check if it's bank statement format (nested arrays with entries)
            if (ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                JsonNode firstItem = ecrituresNode.get(0);
                if (firstItem.isArray() && firstItem.size() > 0) {
                    JsonNode entriesNode = firstItem.get(0);
                    if (entriesNode.has("entries")) {
                        log.info("🏦 Detected bank statement format with entries array");
                        return entriesNode.get("entries");
                    }
                }
            }

            // If not bank format, return as-is (normal format)
            log.info("📄 Detected normal Ecritures format");
            return ecrituresNode;
        }
        return null;
    }

    private PieceDTO buildPieceDTOFromData(Piece piece, JsonNode convertedEcritures, JsonNode firstEntry) {
        PieceDTO pieceDTO = new PieceDTO();
        pieceDTO.setId(piece.getId());
        pieceDTO.setFilename(piece.getFilename());
        pieceDTO.setType(piece.getType());
        pieceDTO.setUploadDate(piece.getUploadDate());
        pieceDTO.setAmount(calculateLargestAmount(convertedEcritures));
        pieceDTO.setFactureData(buildFactureData(firstEntry));
        pieceDTO.setEcritures(buildEcritures(convertedEcritures));
        pieceDTO.setDossierId(piece.getDossier().getId());
        pieceDTO.setDossierName(piece.getDossier().getName());
        pieceDTO.setIsDuplicate(piece.getIsDuplicate());
        pieceDTO.setIsForced(piece.getIsForced());

        // Set AI and currency information
        pieceDTO.setAiCurrency(piece.getAiCurrency());
        pieceDTO.setAiAmount(piece.getAiAmount());
        pieceDTO.setOriginalCurrency(piece.getAiCurrency());
        pieceDTO.setDossierCurrency(getDossierCurrencyCode(piece.getDossier()));

        if (piece.getExchangeRate() != null) {
            pieceDTO.setExchangeRate(piece.getExchangeRate());
            pieceDTO.setConvertedCurrency(piece.getConvertedCurrency());
            pieceDTO.setExchangeRateDate(piece.getExchangeRateDate());
        }

        if (piece.getOriginalPiece() != null) {
            pieceDTO.setOriginalPieceId(piece.getOriginalPiece().getId());
            pieceDTO.setOriginalPieceName(piece.getOriginalPiece().getOriginalFileName());
        }

        return pieceDTO;
    }

    private String getDossierCurrencyCode(Dossier dossier) {
        return dossier.getCurrency() != null ?
                normalizeCurrencyCode.normalizeCurrencyCode(dossier.getCurrency().getCode()) : "MAD";
    }

    // ✅ ADDED: Method to calculate largest amount with proper parsing
    private Double calculateLargestAmount(JsonNode ecritures) {
        double maxAmount = 0.0;
        for (JsonNode entry : ecritures) {
            // ✅ ADDED: Use parseDouble method which handles comma separators
            double debit = parseDouble(entry.get("DebitAmt").asText());
            double credit = parseDouble(entry.get("CreditAmt").asText());
            maxAmount = Math.max(maxAmount, Math.max(debit, credit));
        }
        return maxAmount;
    }

    public JsonNode createConvertedResponseNode(PieceDTO pieceDTO, JsonNode originalResponse) {
        try {
            String responseText = originalResponse.asText();
            JsonNode parsedOriginal = objectMapper.readTree(responseText);

            ObjectNode convertedRoot = objectMapper.createObjectNode();
            ArrayNode ecrituresArray = objectMapper.createArrayNode();

            if (pieceDTO.getEcritures() != null && !pieceDTO.getEcritures().isEmpty()) {
                EcrituresDTO2 ecriture = pieceDTO.getEcritures().get(0);

                for (LineDTO line : ecriture.getLines()) {
                    ObjectNode entryNode = buildConvertedEntryNode(pieceDTO, ecriture, line);
                    ecrituresArray.add(entryNode);
                }
            }

            convertedRoot.set("ecritures", ecrituresArray);
            return objectMapper.valueToTree(objectMapper.writeValueAsString(convertedRoot));

        } catch (Exception e) {
            log.error("💥 Error creating converted response node: {}", e.getMessage(), e);
            return originalResponse;
        }
    }

    private ObjectNode buildConvertedEntryNode(PieceDTO pieceDTO, EcrituresDTO2 ecriture, LineDTO line) {
        ObjectNode entryNode = objectMapper.createObjectNode();

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

        // Rest of the method remains the same...
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

    private FactureDataDTO buildFactureData(JsonNode entry) {
        FactureDataDTO factureData = new FactureDataDTO();

        // Set invoice number - handle missing FactureNum for bank statements
        if (entry.has("FactureNum") && !entry.get("FactureNum").isNull()) {
            factureData.setInvoiceNumber(entry.get("FactureNum").asText());
            log.info("📄 Normal invoice with FactureNum: {}", factureData.getInvoiceNumber());
        } else {
            // For bank statements, try to extract from EcritLib or generate default
            String ecritLib = entry.has("EcritLib") ? entry.get("EcritLib").asText() : "";
            if (ecritLib.contains("Facture")) {
                // Try to extract invoice number from EcritLib
                String invoiceNum = extractInvoiceNumberFromEcritLib(ecritLib);
                factureData.setInvoiceNumber(invoiceNum);
                log.info("🏦 Extracted invoice number from EcritLib: {}", invoiceNum);
            } else {
                factureData.setInvoiceNumber("BANK-" + System.currentTimeMillis());
                log.info("🏦 Generated bank invoice number: {}", factureData.getInvoiceNumber());
            }
        }

        // Set invoice date
        try {
            if (entry.has("Date")) {
                String dateStr = entry.get("Date").asText();
                LocalDate localDate = parseDate(dateStr);
                factureData.setInvoiceDate(java.sql.Date.valueOf(localDate));
                log.info("📅 Set invoice date: {}", factureData.getInvoiceDate());
            }
        } catch (Exception e) {
            log.error("Failed to set invoice date in buildFactureData: {}", e.getMessage());
        }

        // Rest of the method remains the same...
        // Process TVA rate
        Double tvaRate = extractTVARate(entry);
        factureData.setTaxRate(tvaRate);

        // Set total amounts
        setTotalAmounts(factureData, entry, tvaRate);

        // Set currency information
        setCurrencyInformation(factureData, entry);

        // Set conversion information
        setConversionInformation(factureData, entry);

        log.info("✅ Built FactureData DTO: invoiceNumber={}, invoiceDate={}, currency={}",
                factureData.getInvoiceNumber(), factureData.getInvoiceDate(), factureData.getDevise());

        return factureData;
    }

    private String extractInvoiceNumberFromEcritLib(String ecritLib) {
        try {
            // Look for patterns like "Facture FT25034690" or "Facture 12345"
            if (ecritLib.contains("Facture")) {
                String[] parts = ecritLib.split("Facture");
                if (parts.length > 1) {
                    String invoicePart = parts[1].trim();
                    // Extract alphanumeric invoice number
                    String invoiceNum = invoicePart.replaceAll("[^a-zA-Z0-9]", " ").trim().split(" ")[0];
                    return invoiceNum.isEmpty() ? "BANK-" + System.currentTimeMillis() : invoiceNum;
                }
            }
        } catch (Exception e) {
            log.trace("Error extracting invoice number from EcritLib: {}", e.getMessage());
        }
        return "BANK-" + System.currentTimeMillis();
    }

    private Double extractTVARate(JsonNode entry) {
        try {
            JsonNode tvaNode = entry.get("TVARate");
            if (tvaNode != null && !tvaNode.isNull()) {
                if (tvaNode.isNumber()) {
                    return tvaNode.asDouble();
                } else {
                    String tvaText = tvaNode.asText().trim();
                    if (!tvaText.isEmpty()) {
                        String numberStr = tvaText.replaceAll("[^0-9.]", "");
                        if (!numberStr.isEmpty()) {
                            try {
                                return Double.parseDouble(numberStr);
                            } catch (NumberFormatException ignored) {
                                // If parsing fails, return null
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.trace("Error processing TVA rate: {}", e.getMessage());
        }
        return null;
    }

    private void setTotalAmounts(FactureDataDTO factureData, JsonNode entry, Double tvaRate) {
        // Set total TTC
        if (entry.has("TotalTTC")) {
            factureData.setTotalTTC(parseDoubleSafely(entry, "TotalTTC"));
        } else {
            // ✅ FIX: Use safe parsing for debit/credit amounts
            Double debit = entry.has("DebitAmt") ? parseDoubleSafely(entry, "DebitAmt") : 0.0;
            Double credit = entry.has("CreditAmt") ? parseDoubleSafely(entry, "CreditAmt") : 0.0;
            factureData.setTotalTTC(Math.max(debit, credit));
        }

        // Set total HT
        if (entry.has("TotalHT")) {
            factureData.setTotalHT(parseDoubleSafely(entry, "TotalHT"));
        } else if (factureData.getTotalTTC() != null && tvaRate != null) {
            factureData.setTotalHT(factureData.getTotalTTC() / (1 + (tvaRate / 100)));
        }

        // Set total TVA
        if (factureData.getTotalTTC() != null && factureData.getTotalHT() != null && factureData.getTotalTVA() == null) {
            factureData.setTotalTVA(factureData.getTotalTTC() - factureData.getTotalHT());
        }
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

            // Calculate converted amounts
            if (factureData.getTotalTTC() != null) {
                factureData.setConvertedTotalTTC(factureData.getTotalTTC() * rate);
            }
            if (factureData.getTotalHT() != null) {
                factureData.setConvertedTotalHT(factureData.getTotalHT() * rate);
            }
            if (factureData.getTotalTVA() != null) {
                factureData.setConvertedTotalTVA(factureData.getTotalTVA() * rate);
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

    private List<EcrituresDTO2> buildEcritures(JsonNode ecrituresNode) {
        List<EcrituresDTO2> ecritures = new ArrayList<>();

        EcrituresDTO2 ecriture = new EcrituresDTO2();
        ecriture.setUniqueEntryNumber(UUID.randomUUID().toString());

        String dateStr = ecrituresNode.get(0).get("Date").asText();
        String formattedDate = formatDateToStandard(dateStr);

        ecriture.setEntryDate(formattedDate);
        ecriture.setJournal(buildJournal(ecrituresNode.get(0)));

        // ✅ FIX: Ensure lines are built and set
        List<LineDTO> lines = buildLines(ecrituresNode);
        if (lines == null || lines.isEmpty()) {
            log.warn("⚠️ No lines built from ecrituresNode: {}", ecrituresNode);
        }
        ecriture.setLines(lines);

        ecritures.add(ecriture);
        log.info("✅ Built ecriture with {} lines", lines != null ? lines.size() : 0);
        return ecritures;
    }

    private List<LineDTO> buildLines(JsonNode ecrituresNode) {
        List<LineDTO> lines = new ArrayList<>();

        if (ecrituresNode == null || !ecrituresNode.isArray()) {
            log.error("❌ Invalid ecrituresNode in buildLines");
            return lines;
        }

        for (JsonNode entry : ecrituresNode) {
            try {
                LineDTO line = new LineDTO();
                line.setLabel(entry.get("EcritLib").asText());

                // ✅ FIX: Use safe parsing for all numeric values
                // Handle debit amounts
                if (entry.has("OriginalDebitAmt")) {
                    line.setOriginalDebit(parseDoubleSafely(entry, "OriginalDebitAmt"));
                    line.setDebit(parseDoubleSafely(entry, "OriginalDebitAmt"));
                    line.setConvertedDebit(parseDoubleSafely(entry, "DebitAmt"));
                } else {
                    line.setDebit(parseDoubleSafely(entry, "DebitAmt"));
                }

                // Handle credit amounts
                if (entry.has("OriginalCreditAmt")) {
                    line.setOriginalCredit(parseDoubleSafely(entry, "OriginalCreditAmt"));
                    line.setCredit(parseDoubleSafely(entry, "OriginalCreditAmt"));
                    line.setConvertedCredit(parseDoubleSafely(entry, "CreditAmt"));
                } else {
                    line.setCredit(parseDoubleSafely(entry, "CreditAmt"));
                }

                // Set currency information
                if (entry.has("OriginalDevise")) {
                    line.setOriginalCurrency(entry.get("OriginalDevise").asText());
                }
                if (entry.has("Devise")) {
                    line.setConvertedCurrency(entry.get("Devise").asText());
                }

                // Set account information
                AccountDTO account = new AccountDTO();
                account.setAccount(entry.get("CompteNum").asText());
                account.setLabel(entry.get("CompteLib").asText());
                line.setAccount(account);

                lines.add(line);
                log.debug("✅ Built line: {} - Debit: {}, Credit: {}",
                        line.getLabel(), line.getDebit(), line.getCredit());

            } catch (Exception e) {
                log.error("❌ Error building line from entry {}: {}", entry, e.getMessage());
            }
        }

        log.info("✅ Built {} lines from ecritures", lines.size());
        return lines;
    }

    private JournalDTO buildJournal(JsonNode entry) {
        JournalDTO journal = new JournalDTO();
        journal.setName(entry.get("JournalCode").asText());
        journal.setType(entry.get("JournalLib").asText());
        return journal;
    }

    // ==================== UTILITY METHODS ====================

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

    private String formatDateToStandard(String dateStr) {
        try {
            LocalDate date = parseDate(dateStr);
            return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            log.trace("❌ Date formatting failed for: {}", dateStr);
            return LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
    }

    // parseDouble method with comma support and null safety
    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }

        try {
            // ✅ ADDED: Replace comma with dot for European decimal format
            String normalizedValue = value.replace(',', '.');
            return Double.parseDouble(normalizedValue);
        } catch (NumberFormatException e) {
            log.trace("Error parsing double value: {}", value);
            return 0.0;
        }
    }

    // Helper method for safe double parsing
    private double parseDoubleSafely(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            try {
                String value = node.get(fieldName).asText();
                // Handle comma decimal separator
                value = value.replace(',', '.');
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                log.trace("Error parsing {} value: {}", fieldName, node.get(fieldName).asText());
                return 0.0;
            }
        }
        log.trace("Field {} not found or is null", fieldName);
        return 0.0;
    }
}