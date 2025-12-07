package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PieceValidator {

    @Autowired
    private ObjectMapper objectMapper;

    public boolean isValidAIResponse(JsonNode root) {
//        log.info("🔍 Validating AI response structure - Root keys: {}", root.fieldNames());

        // Check for normalized structure first
        if (root.has("ecritures")) {
            log.info("✅ Found normalized ecritures structure");
            return validateEcrituresArray(root.get("ecritures"));
        }

        // Check for original outputText structure
        if (root.has("outputText")) {
            log.info("📄 Found outputText structure");
            return validateEcritures(root.get("outputText"));
        }

        // Check if root itself is the ecritures array
        if (root.isArray()) {
            log.info("📄 Root is direct ecritures array");
            return validateEcrituresArray(root);
        }

        log.error("❌ Invalid AI response structure - no ecritures or outputText found");
        return false;
    }

    private boolean validateEcrituresArray(JsonNode ecritures) {
        if (ecritures == null || !ecritures.isArray() || ecritures.size() == 0) {
            log.error("❌ Invalid ecritures array - null, not array, or empty");
            return false;
        }

        return validateAllEcritureEntries(ecritures);
    }

    public boolean validateEcritures(JsonNode node) {
        try {
            String textValue = node.asText();

            if (textValue == null || textValue.trim().isEmpty()) {
                log.error("❌ Empty output text from AI service");
                return false;
            }

            JsonNode parsedJson = parseJsonText(textValue);

            // Check for ecritures in parsed JSON
            if (parsedJson.has("ecritures")) {
                return isValidEcrituresArray(parsedJson.get("ecritures"));
            } else if (parsedJson.has("Ecritures")) {
                return isValidEcrituresArray(parsedJson.get("Ecritures"));
            } else if (parsedJson.isArray()) {
                // Direct array of ecritures
                return isValidEcrituresArray(parsedJson);
            }

            log.error("❌ No ecritures found in parsed outputText");
            return false;

        } catch (Exception e) {
            log.error("💥 Error validating ecritures: {}", e.getMessage(), e);
            return false;
        }
    }


    private JsonNode parseJsonText(String textValue) throws JsonProcessingException {
        return objectMapper.readTree(textValue);
    }


    private boolean isValidEcrituresArray(JsonNode ecritures) {
        return validateEcrituresArray(ecritures);
    }

    private boolean validateAllEcritureEntries(JsonNode ecritures) {
        for (int i = 0; i < ecritures.size(); i++) {
            if (!validateEcritureFields(ecritures.get(i))) {
                log.error("❌ Invalid ecriture at index {}: {}", i, ecritures.get(i));
                return false;
            }
        }
        log.info("✅ All {} ecriture entries are valid", ecritures.size());
        return true;
    }

    private boolean validateEcritureFields(JsonNode entry) {
        if (entry == null) {
            log.error("❌ Ecriture entry is null");
            return false;
        }

        // Different required fields for bank statements vs normal pieces
        String[] requiredFields = {"Date", "JournalCode", "JournalLib", "CompteNum", "CompteLib", "EcritLib", "Devise"};
        String[] numericFields = {"DebitAmt", "CreditAmt"};

        return hasAllRequiredFields(entry, requiredFields) &&
                hasValidNumericFields(entry, numericFields);
    }


    public boolean validateBankEcritureFields(JsonNode entry) {
        if (entry == null) {
            log.error("❌ Bank ecriture entry is null");
            return false;
        }

        // Bank statements might have empty dates or other fields
        // Make validation more flexible than normal pieces
        String[] requiredFields = {"JournalCode", "JournalLib", "CompteNum", "CompteLib", "EcritLib"};
        String[] numericFields = {"DebitAmt", "CreditAmt"};

        // Check required fields (allow empty values for some)
        for (String field : requiredFields) {
            if (!entry.has(field)) {
                log.error("❌ Missing required field in bank entry: {}", field);
                return false;
            }
        }

        // Check numeric fields
        return hasValidNumericFields(entry, numericFields);
    }

    private boolean hasAllRequiredFields(JsonNode entry, String[] fields) {
        for (String field : fields) {
            if (!entry.has(field) || entry.get(field).isNull() || entry.get(field).asText().trim().isEmpty()) {
                log.error("❌ Missing or empty required field: {}", field);
                return false;
            }
        }
        return true;
    }

    private boolean hasValidNumericFields(JsonNode entry, String[] fields) {
        for (String field : fields) {
            if (!entry.has(field)) {
                log.error("❌ Missing required numeric field: {}", field);
                return false;
            }

            // ✅ ADDED: Comma support for European decimal format
            String rawValue = entry.get(field).asText().trim();
            String normalizedValue = rawValue.replace(',', '.');

            if (!isValidNumericValue(normalizedValue)) {
                log.error("❌ Invalid numeric value for field {}: {} (normalized from '{}')", field, normalizedValue, rawValue);
                return false;
            }
        }
        return true;
    }

    private boolean isValidNumericValue(String value) {
        try {
            // ✅ ADDED: Handle empty values
            if (value.isEmpty()) {
                value = "0";
            }

            double num = Double.parseDouble(value);
            return !Double.isInfinite(num) && !Double.isNaN(num);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean isValidBankAIResponse(JsonNode root) {
//        log.info("🏦 Validating bank AI response structure - Root keys: {}", root.fieldNames());

        // Check if it's already a normalized response with ecritures
        if (root.has("ecritures")) {
            JsonNode ecrituresNode = root.get("ecritures");
            log.info("🏦 Validating normalized bank response with {} entries", ecrituresNode.size());
            return isValidBankEcrituresArray(ecrituresNode);
        }

        // Check for outputText structure (original format)
        if (root.has("outputText")) {
            log.info("🏦 Validating bank response with outputText");
            return validateBankEcritures(root.get("outputText"));
        }

        log.error("❌ Bank AI response has neither 'ecritures' nor 'outputText'");
        return false;
    }

    public boolean validateBankEcritures(JsonNode node) {
        try {
            String textValue = node.asText();

            if (textValue == null || textValue.trim().isEmpty()) {
                log.error("❌ Empty output text from bank AI service");
                return false;
            }

            JsonNode parsedJson = parseJsonText(textValue);
            JsonNode entries = findBankEcrituresNode(parsedJson);

            return isValidBankEcrituresArray(entries);

        } catch (Exception e) {
            log.error("💥 Error validating bank ecritures: {}", e.getMessage(), e);
            return false;
        }
    }

    // In PieceValidator.java - ADD THIS METHOD
    JsonNode findBankEcrituresNode(JsonNode parsedJson) {
//        log.info("🏦 Processing bank statement structure - Root keys: {}", parsedJson.fieldNames());

        // First check if we already have a flat ecritures array (from normalized response)
        if (parsedJson.has("ecritures")) {
            JsonNode ecrituresNode = parsedJson.get("ecritures");
            log.info("🏦 Found flat ecritures array with {} entries", ecrituresNode.size());

            // Check if these are transaction groups or regular entries
            if (ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                JsonNode firstItem = ecrituresNode.get(0);
                if (firstItem.has("isTransactionGroup") && firstItem.get("isTransactionGroup").asBoolean()) {
                    log.info("🏦 Found transaction group structure");
                }
            }
            return ecrituresNode;
        }

        // Then check for the nested Ecritures → entries structure
        if (parsedJson.has("Ecritures")) {
            JsonNode ecrituresNode = parsedJson.get("Ecritures");
            log.info("🏦 Found nested Ecritures structure with {} transaction groups", ecrituresNode.size());

            if (ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                ArrayNode allEntries = objectMapper.createArrayNode();

                for (JsonNode transactionGroup : ecrituresNode) {
                    if (transactionGroup.isObject() && transactionGroup.has("entries")) {
                        JsonNode entries = transactionGroup.get("entries");
                        if (entries.isArray()) {
                            // Create transaction group node
                            ObjectNode groupNode = objectMapper.createObjectNode();
                            groupNode.set("entries", entries);
                            groupNode.put("isTransactionGroup", true);

                            // Add date from first entry if available
                            if (entries.size() > 0 && entries.get(0).has("Date")) {
                                groupNode.put("Date", entries.get(0).get("Date").asText());
                            }

                            allEntries.add(groupNode);
                        }
                    }
                }

                if (allEntries.size() > 0) {
                    log.info("🏦 Created {} transaction groups", allEntries.size());
                    return allEntries;
                }
            }
        }

        log.warn("❌ Could not find bank ecritures in expected format");
        return null;
    }

    private boolean isValidBankEcrituresArray(JsonNode entries) {
        if (entries == null || !entries.isArray() || entries.size() == 0) {
            log.error("❌ Invalid bank entries array");
            return false;
        }

        // For bank statements, allow some flexibility with required fields
        return validateAllBankEcritureEntries(entries);
    }

    private boolean validateAllBankEcritureEntries(JsonNode entries) {
        for (int i = 0; i < entries.size(); i++) {
            JsonNode entry = entries.get(i);

            // Check if this is a transaction group
            if (entry.has("isTransactionGroup") && entry.get("isTransactionGroup").asBoolean() &&
                    entry.has("entries") && entry.get("entries").isArray()) {

                // Validate each entry in the transaction group
                JsonNode innerEntries = entry.get("entries");
                for (int j = 0; j < innerEntries.size(); j++) {
                    if (!validateBankEcritureFields(innerEntries.get(j))) {
                        log.error("❌ Invalid bank ecriture at index {} in transaction group {}: {}",
                                j, i, innerEntries.get(j));
                        return false;
                    }
                }
            } else {
                // Regular entry validation
                if (!validateBankEcritureFields(entry)) {
                    log.error("❌ Invalid bank ecriture at index {}: {}", i, entry);
                    return false;
                }
            }
        }
        return true;
    }
}