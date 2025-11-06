package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacioli.core.DTO.EcrituresDTO2;
import com.pacioli.core.DTO.PieceDTO;
import com.pacioli.core.batches.DTO.DTOBuilder;
import com.pacioli.core.config.batch.BatchProcessingConfig;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.PieceRepository;
import com.pacioli.core.services.ExchangeRateService;
import com.pacioli.core.services.PieceService;
import com.pacioli.core.utils.NormalizeCurrencyCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public abstract class BaseAIProcessor {

    @Autowired
    protected BatchProcessingConfig batchConfig;

    @Autowired
    protected PieceRepository pieceRepository;

    @Autowired
    protected PieceService pieceService;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected PieceValidator pieceValidator;

    @Autowired
    protected DTOBuilder dtoBuilder;

    @Autowired
    protected NormalizeCurrencyCode normalizeCurrencyCode;

    @Autowired
    protected ExchangeRateService exchangeRateService; // ✅ ADD THIS

    protected static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-d"),
            DateTimeFormatter.ofPattern("yyyy-M-dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy-dd-MM")
    );

    // ✅ CHANGE FROM PRIVATE TO PROTECTED
    protected LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            log.warn("❌ Date string is null or empty, using current date");
            return LocalDate.now();
        }

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

    // ✅ CHANGE FROM PRIVATE TO PROTECTED
    protected JsonNode findEcrituresNodeForAI(JsonNode parsedJson) {
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

    // ✅ CHANGE FROM PRIVATE TO PROTECTED
    protected Double calculateLargestAmount(JsonNode ecritures) {
        double maxAmount = 0.0;
        int entryCount = 0;

        for (JsonNode entry : ecritures) {
            // ✅ FIX: Check if fields exist before accessing them
            double debit = parseDoubleSafely(entry, "DebitAmt");
            double credit = parseDoubleSafely(entry, "CreditAmt");
            double entryMax = Math.max(debit, credit);

            maxAmount = Math.max(maxAmount, entryMax);
            entryCount++;
        }

        log.info("💰 Calculated largest amount: {} from {} entries", maxAmount, entryCount);
        return maxAmount;
    }

    // ✅ CHANGE FROM PRIVATE TO PROTECTED
    protected String extractAndNormalizeCurrency(JsonNode entry) {
        String rawCurrency = extractStringSafely(entry, "Devise", null);

        // ✅ Return null if currency is empty, blank, or meaningless
        if (rawCurrency == null || rawCurrency.trim().isEmpty() ||
                rawCurrency.equalsIgnoreCase("NAN") ||
                rawCurrency.equalsIgnoreCase("NULL") ||
                rawCurrency.equalsIgnoreCase("undefined") ||
                rawCurrency.equalsIgnoreCase("N/A") ||
                rawCurrency.equalsIgnoreCase("None") ||
                rawCurrency.equalsIgnoreCase("Unknown")) {

            log.info("⚠️ Currency field is empty/invalid in AI response, returning null");
            return null;
        }

        String normalizedCurrency = normalizeCurrencyCode.normalizeCurrencyCode(rawCurrency);
        log.info("💰 Extracted currency from AI: {} -> {}", rawCurrency, normalizedCurrency);
        return normalizedCurrency;
    }

    // ✅ CHANGE FROM PRIVATE TO PROTECTED
    protected double parseDoubleSafely(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            log.trace("Field {} not found or is null", fieldName);
            return 0.0;
        }

        try {
            String value = node.get(fieldName).asText();
            if (value == null || value.trim().isEmpty()) {
                return 0.0;
            }
            // Handle comma decimal separator
            value = value.replace(',', '.');
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.trace("Error parsing {} value: {}", fieldName, node.get(fieldName).asText());
            return 0.0;
        }
    }

    // ✅ CHANGE FROM PRIVATE TO PROTECTED
    protected String extractStringSafely(JsonNode node, String fieldName, String defaultValue) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return defaultValue;
        }
        String value = node.get(fieldName).asText();
        return (value == null || value.trim().isEmpty()) ? defaultValue : value.trim();
    }

    // ✅ CHANGE FROM PRIVATE TO PROTECTED
    protected String getDossierCurrencyCode(Dossier dossier) {
        if (dossier == null) {
            throw new IllegalArgumentException("Dossier cannot be null when getting currency");
        }

        // The dossier MUST have a currency defined by the user
        if (dossier.getCurrency() == null || dossier.getCurrency().getCode() == null) {
            throw new IllegalStateException("Dossier " + dossier.getId() + " has no currency specified. " +
                    "Please set a currency for this dossier in the dossier settings.");
        }

        String dossierCurrency = normalizeCurrencyCode.normalizeCurrencyCode(dossier.getCurrency().getCode());
        log.info("💰 Dossier currency: {}", dossierCurrency);
        return dossierCurrency;
    }

    protected void updatePieceStatus(Piece piece, PieceStatus status) {
        pieceService.updatePieceStatus(piece.getId(), status.name());
    }

    protected void rejectPiece(Piece piece, String reason) {
        log.error("❌ Rejecting piece {}: {}", piece.getId(), reason);
        pieceService.updatePieceStatus(piece.getId(), PieceStatus.REJECTED.name());
    }

    protected abstract void handleInvalidResponse(Piece piece, int attempt, String jsonResponse) throws InterruptedException;

    protected abstract void handleProcessingError(Piece piece, int attempt, Exception e) throws InterruptedException;

    public void processValidAIResponse(Piece piece, JsonNode aiResponse) throws JsonProcessingException {
        try {
            // ✅ STEP 1: AI data should already be extracted and saved by child class
            // extractAndSaveAIData is now called in child classes before this method

            // ✅ STEP 2: Reload piece to ensure we have latest data
            Piece refreshedPiece = pieceRepository.findById(piece.getId()).orElseThrow(() -> new RuntimeException("Piece not found after AI data extraction"));

            // ✅ STEP 3: Process DTO and save ecritures
            PieceDTO pieceDTO = dtoBuilder.buildPieceDTO(refreshedPiece, aiResponse);

            // ✅ DEBUG: Check if lines are populated
            if (pieceDTO.getEcritures() != null && !pieceDTO.getEcritures().isEmpty()) {
                EcrituresDTO2 firstEcriture = pieceDTO.getEcritures().get(0);
                log.info("🔍 DTO Built - Ecriture has {} lines", firstEcriture.getLines() != null ? firstEcriture.getLines().size() : 0);
            } else {
                log.warn("⚠️ No ecritures in built DTO");
            }

            JsonNode convertedResponse = dtoBuilder.createConvertedResponseNode(pieceDTO, aiResponse);

            // ✅ STEP 4: Save to database
            pieceService.saveEcrituresAndFacture(refreshedPiece.getId(), refreshedPiece.getDossier().getId(), objectMapper.writeValueAsString(pieceDTO), convertedResponse);

            // ✅ STEP 5: Update status to PROCESSED
            updatePieceStatus(refreshedPiece, PieceStatus.PROCESSED);

            log.info("✅ Successfully processed piece {} with AI data", refreshedPiece.getId());

        } catch (Exception e) {
            log.error("❌ Error in processValidAIResponse for piece {}: {}", piece.getId(), e.getMessage(), e);
            throw e;
        }
    }
}