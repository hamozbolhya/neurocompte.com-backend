package com.pacioli.core.batches.processors.normalizers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AIResponseNormalizer {

    @Autowired
    private ObjectMapper objectMapper;

    public JsonNode normalizeAIResponse(JsonNode aiResponse, boolean isBankStatement) {
        try {
            if (isBankStatement) {
                return normalizeBankResponse(aiResponse);
            } else {
                return normalizeInvoiceResponse(aiResponse);
            }
        } catch (Exception e) {
            log.error("❌ Error normalizing AI response: {}", e.getMessage());
            return aiResponse; // Return original if normalization fails
        }
    }

    private JsonNode normalizeBankResponse(JsonNode bankResponse) {
        try {
            // For bank responses, we need to extract from outputText
            if (bankResponse.has("outputText")) {
                String outputText = bankResponse.get("outputText").asText();
                JsonNode parsedOutput = objectMapper.readTree(outputText);
                JsonNode ecrituresNode = extractBankEcritures(parsedOutput);

                if (ecrituresNode != null && ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                    ObjectNode normalizedResponse = objectMapper.createObjectNode();
                    normalizedResponse.set("ecritures", ecrituresNode);
                    normalizedResponse.put("isBankStatement", true);

                    log.info("🏦 Normalized bank response with {} entries", ecrituresNode.size());
                    return normalizedResponse;
                } else {
                    log.error("❌ Could not extract bank ecritures from outputText");
                }
            } else {
                log.error("❌ Bank response missing outputText field");
            }
        } catch (Exception e) {
            log.error("❌ Error normalizing bank response: {}", e.getMessage());
        }

        // Return original if normalization fails
        log.warn("⚠️ Returning original bank response due to normalization failure");
        return bankResponse;
    }

    private JsonNode normalizeInvoiceResponse(JsonNode invoiceResponse) {
        try {
            // For invoice responses, check if we have outputText or direct Ecritures
            if (invoiceResponse.has("outputText")) {
                // Case 1: Response has outputText field (wrapped response)
                String outputText = invoiceResponse.get("outputText").asText();
                JsonNode parsedOutput = objectMapper.readTree(outputText);

                ObjectNode normalized = objectMapper.createObjectNode();
                if (parsedOutput.has("ecritures")) {
                    normalized.set("ecritures", parsedOutput.get("ecritures"));
                } else if (parsedOutput.has("Ecritures")) {
                    normalized.set("ecritures", parsedOutput.get("Ecritures"));
                } else if (parsedOutput.isArray()) {
                    // Direct array of ecritures
                    normalized.set("ecritures", parsedOutput);
                } else {
                    // If no ecritures found in outputText, use the entire outputText as ecritures
                    normalized.set("ecritures", parsedOutput);
                }

                normalized.put("isBankStatement", false);
                log.info("📄 Normalized invoice response from outputText");
                return normalized;

            } else if (invoiceResponse.has("ecritures") || invoiceResponse.has("Ecritures")) {
                // Case 2: Response already has ecritures directly
                ObjectNode normalized = objectMapper.createObjectNode();
                if (invoiceResponse.has("ecritures")) {
                    normalized.set("ecritures", invoiceResponse.get("ecritures"));
                } else {
                    normalized.set("ecritures", invoiceResponse.get("Ecritures"));
                }
                normalized.put("isBankStatement", false);
                log.info("📄 Normalized invoice response (direct ecritures)");
                return normalized;
            }

        } catch (Exception e) {
            log.error("❌ Error normalizing invoice response: {}", e.getMessage());
        }

        // If normalization fails, return original
        return invoiceResponse;
    }

    private JsonNode extractBankEcritures(JsonNode parsedJson) {
        log.info("🏦 Extracting bank ecritures from parsed JSON - keys: {}", parsedJson.fieldNames());

        if (parsedJson.has("Ecritures")) {
            JsonNode ecrituresNode = parsedJson.get("Ecritures");
            log.info("🏦 Found Ecritures node with {} elements", ecrituresNode.size());

            if (ecrituresNode.isArray()) {
                ArrayNode allEntries = objectMapper.createArrayNode();

                for (JsonNode transactionGroup : ecrituresNode) {
                    if (transactionGroup.has("entries") && transactionGroup.get("entries").isArray()) {
                        JsonNode entries = transactionGroup.get("entries");
                        entries.forEach(allEntries::add);
                       // log.debug("🏦 Added {} entries from transaction group", entries.size());
                    }
                }

                if (allEntries.size() > 0) {
                    log.info("🏦 Successfully extracted {} total entries", allEntries.size());
                    return allEntries;
                } else {
                    log.warn("⚠️ No entries extracted from transaction groups");
                }
            } else {
                log.warn("⚠️ Ecritures node is not an array");
            }
        } else {
            log.warn("⚠️ No Ecritures found in parsed JSON");
        }

        log.error("❌ Could not extract bank ecritures from structure");
        return null;
    }
}