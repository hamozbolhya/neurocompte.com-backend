package com.pacioli.core.batches.DTO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pacioli.core.DTO.EcrituresDTO2;
import com.pacioli.core.DTO.LineDTO;
import com.pacioli.core.DTO.PieceDTO;
import com.pacioli.core.batches.processors.EcritureBuilder;
import com.pacioli.core.batches.processors.FactureDataBuilder;
import com.pacioli.core.batches.processors.converters.CurrencyConversionService;
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
    private FactureDataBuilder factureDataBuilder;

    @Autowired
    private EcritureBuilder ecritureBuilder;

    @Autowired
    private BaseDTOBuilder baseDTOBuilder;
    @Autowired
    private ExchangeRateService exchangeRateService;

    @Autowired
    private CurrencyConversionService currencyConversionService;


    public PieceDTO buildPieceDTO(Piece piece, JsonNode aiResponse) throws JsonProcessingException {
        try {
            log.info("🔍 Building DTO for piece {}", piece.getId());

            // The aiResponse is now the NORMALIZED response
            JsonNode ecrituresNode = extractEcrituresFromNormalizedResponse(aiResponse);

            if (ecrituresNode == null || !ecrituresNode.isArray() || ecrituresNode.size() == 0) {
                log.error("❌ No valid ecritures array found in normalized response");
                throw new IllegalArgumentException("No valid ecritures array found in AI response");
            }

            log.info("✅ Found {} ecritures entries in normalized response", ecrituresNode.size());

            JsonNode firstEntry = ecrituresNode.get(0);

            // Apply currency conversion using dedicated service
            JsonNode convertedEcritures = currencyConversionService.convertEcrituresCurrency(ecrituresNode, piece);

            return buildPieceDTOFromData(piece, convertedEcritures, firstEntry);

        } catch (Exception e) {
            log.error("💥 Error building PieceDTO: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build PieceDTO from AI response", e);
        }
    }



    /**
     * Extract ecritures from normalized response structure
     */
    private JsonNode extractEcrituresFromNormalizedResponse(JsonNode normalizedResponse) {
        log.info("🔍 Extracting ecritures from normalized response - keys: {}", normalizedResponse.fieldNames());

        // Case 1: Normalized response has ecritures directly (both invoice and bank)
        if (normalizedResponse.has("ecritures")) {
            JsonNode ecritures = normalizedResponse.get("ecritures");
            boolean isBankStatement = normalizedResponse.has("isBankStatement") && normalizedResponse.get("isBankStatement").asBoolean();

            if (ecritures.isArray()) {
                log.info("✅ Found ecritures in normalized response: {} entries (isBank: {})",
                        ecritures.size(), isBankStatement);
                return ecritures;
            } else {
                log.warn("⚠️ ecritures exists but is not an array");
            }
        }

        // Case 2: Response might be the ecritures array directly
        if (normalizedResponse.isArray()) {
            log.info("✅ Normalized response is direct ecritures array: {} entries", normalizedResponse.size());
            return normalizedResponse;
        }

        // Case 3: Check for outputText structure (fallback for bank statements)
        if (normalizedResponse.has("outputText")) {
            log.info("📄 Found outputText, trying to extract ecritures from it");
            try {
                String outputText = normalizedResponse.get("outputText").asText();

                // ✅ NEW: Clean markdown code fences if present
                outputText = baseDTOBuilder.cleanMarkdownCodeFences(outputText);

                JsonNode parsedOutput = baseDTOBuilder.objectMapper.readTree(outputText);
                JsonNode ecritures = baseDTOBuilder.findEcrituresNode(parsedOutput);
                if (ecritures != null && ecritures.isArray()) {
                    log.info("✅ Extracted {} ecritures from outputText", ecritures.size());
                    return ecritures;
                }
            } catch (Exception e) {
                log.error("❌ Error parsing outputText: {}", e.getMessage());
            }
        }

        log.error("❌ Could not find ecritures in normalized response");
        return null;
    }


    private PieceDTO buildPieceDTOFromData(Piece piece, JsonNode convertedEcritures, JsonNode firstEntry) {
        PieceDTO pieceDTO = new PieceDTO();

        try {
            // Set basic piece information
            pieceDTO.setId(piece.getId());
            pieceDTO.setFilename(piece.getFilename());
            pieceDTO.setType(piece.getType());
            pieceDTO.setUploadDate(piece.getUploadDate());
            pieceDTO.setAmount(calculateLargestAmount(convertedEcritures));
            pieceDTO.setDossierId(piece.getDossier().getId());
            pieceDTO.setDossierName(piece.getDossier().getName());
            pieceDTO.setIsDuplicate(piece.getIsDuplicate());
            pieceDTO.setIsForced(piece.getIsForced());

            // Set facture data
            pieceDTO.setFactureData(factureDataBuilder.buildFactureData(firstEntry));

            // Set ecritures
            pieceDTO.setEcritures(ecritureBuilder.buildEcritures(convertedEcritures));

            if (firstEntry != null) {
                pieceDTO.setFactureData(factureDataBuilder.buildFactureData(firstEntry));
            }
            // Set currency information
            pieceDTO.setAiCurrency(piece.getAiCurrency());
            pieceDTO.setAiAmount(piece.getAiAmount());
            pieceDTO.setOriginalCurrency(piece.getAiCurrency());
            pieceDTO.setDossierCurrency(piece.getConvertedCurrency());

            if (piece.getExchangeRate() != null) {
                pieceDTO.setExchangeRate(piece.getExchangeRate());
                pieceDTO.setConvertedCurrency(piece.getConvertedCurrency());
                pieceDTO.setExchangeRateDate(piece.getExchangeRateDate());
            }

            if (piece.getOriginalPiece() != null) {
                pieceDTO.setOriginalPieceId(piece.getOriginalPiece().getId());
                pieceDTO.setOriginalPieceName(piece.getOriginalPiece().getOriginalFileName());
            }

            log.info("✅ Successfully built DTO for piece {}", piece.getId());

        } catch (Exception e) {
            log.error("❌ Error building PieceDTO from data: {}", e.getMessage());
            // Set minimal safe data
            pieceDTO.setId(piece.getId());
            pieceDTO.setFilename(piece.getFilename());
            pieceDTO.setDossierId(piece.getDossier().getId());
        }

        return pieceDTO;
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
