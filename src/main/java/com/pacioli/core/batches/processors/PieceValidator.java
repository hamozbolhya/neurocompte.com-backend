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

    private static final int MAX_ENTRY_SNIPPET_CHARS = 400;

    @Autowired
    private ObjectMapper objectMapper;

    private static String summarizeNode(JsonNode node, int maxChars) {
        if (node == null) {
            return "null";
        }
        String s = node.toString();
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "… (" + s.length() + " chars)";
    }

    public boolean isValidAIResponse(JsonNode root) {
        // Check for normalized structure first
        if (root.has("ecritures")) {
            return validateEcrituresArray(root.get("ecritures"));
        }

        // Check for original outputText structure
        if (root.has("outputText")) {
            return validateEcritures(root.get("outputText"));
        }

        // Check if root itself is the ecritures array
        if (root.isArray()) {
            return validateEcrituresArray(root);
        }

        log.error("❌ Invalid AI response structure — expected ecritures, outputText, or root array");
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

            // ✅ NEW: Clean markdown code fences if present
            textValue = cleanMarkdownCodeFences(textValue);

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
            log.error("❌ Error validating ecritures from outputText: {}", e.getMessage());
            return false;
        }
    }

    private String cleanMarkdownCodeFences(String text) {
        if (text == null) {
            return "";
        }

        String cleaned = text.trim();

        // Remove leading ```json or ```
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim();
        }

        // Remove trailing ```
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        return cleaned;
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
                log.error("❌ Invalid invoice ecriture at index {} — {}", i, summarizeNode(ecritures.get(i), MAX_ENTRY_SNIPPET_CHARS));
                return false;
            }
        }
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

            String rawValue = entry.get(field).asText().trim();
            String normalizedValue = normalizeAmountStringForParsing(rawValue);

            if (!isValidNumericValue(normalizedValue)) {
                log.error("❌ Invalid numeric value for field {}: {} (normalized from '{}')", field, normalizedValue, rawValue);
                return false;
            }
        }
        return true;
    }

    /**
     * Parses amount strings from AI output: US thousands (1,071,520), EU decimals (1234,56),
     * EU thousands (1.071.520), and US decimals (1234.56).
     */
    static String normalizeAmountStringForParsing(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String s = rawValue.trim().replace('\u00A0', ' ').replaceAll("\\s+", "");
        if (s.isEmpty()) {
            return "";
        }

        int commaCount = 0;
        int dotCount = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',') {
                commaCount++;
            } else if (c == '.') {
                dotCount++;
            }
        }

        // Mixed: 1,234,567.89 (US) or 1.234.567,89 (EU)
        if (commaCount >= 1 && dotCount == 1) {
            int lastDot = s.lastIndexOf('.');
            int lastComma = s.lastIndexOf(',');
            if (lastDot > lastComma) {
                return s.replace(",", "");
            }
            return s.replace(".", "").replace(',', '.');
        }

        // Only commas
        if (commaCount > 0 && dotCount == 0) {
            int lastComma = s.lastIndexOf(',');
            String after = s.substring(lastComma + 1);
            if (commaCount > 1) {
                return s.replace(",", "");
            }
            if (after.length() <= 2) {
                return s.substring(0, lastComma) + '.' + after;
            }
            return s.replace(",", "");
        }

        // Only dots: several dots → EU-style thousands (1.071.520); one dot → decimal (1234.56)
        if (dotCount > 0 && commaCount == 0) {
            if (dotCount > 1) {
                return s.replace(".", "");
            }
            return s;
        }

        return s;
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
        // Check if it's already a normalized response with ecritures
        if (root.has("ecritures")) {
            JsonNode ecrituresNode = root.get("ecritures");
            return isValidBankEcrituresArray(ecrituresNode);
        }

        // Check for outputText structure (original format)
        if (root.has("outputText")) {
            return validateBankEcritures(root.get("outputText"));
        }

        log.error("❌ Bank AI response — missing both 'ecritures' and 'outputText'");
        return false;
    }

    public boolean validateBankEcritures(JsonNode node) {
        try {
            String textValue = node.asText();

            if (textValue == null || textValue.trim().isEmpty()) {
                log.error("❌ Empty output text from bank AI service");
                return false;
            }

            // ✅ NEW: Clean markdown code fences if present
            textValue = cleanMarkdownCodeFences(textValue);

            JsonNode parsedJson = parseJsonText(textValue);
            JsonNode entries = findBankEcrituresNode(parsedJson);

            return isValidBankEcrituresArray(entries);

        } catch (Exception e) {
            log.error("❌ Error parsing/validating bank ecritures: {}", e.getMessage());
            return false;
        }
    }

    // In PieceValidator.java - UPDATE findBankEcrituresNode method
    JsonNode findBankEcrituresNode(JsonNode parsedJson) {
//        log.info("🏦 Processing bank statement structure - Root keys: {}", parsedJson.fieldNames());

        // First check if we already have a flat ecritures array (from normalized response)
        if (parsedJson.has("ecritures")) {
            JsonNode ecrituresNode = parsedJson.get("ecritures");
            return ecrituresNode;
        }

        // Then check for the nested Ecritures → entries structure
        if (parsedJson.has("Ecritures")) {
            JsonNode ecrituresNode = parsedJson.get("Ecritures");

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
                    return allEntries;
                }
            }
        }

        // ✅ NEW: Also check for direct array structure (some bank responses might be arrays)
        if (parsedJson.isArray()) {
            return parsedJson;
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
                        log.error("❌ Invalid bank ecriture at inner index {} in group {} — {}",
                                j, i, summarizeNode(innerEntries.get(j), MAX_ENTRY_SNIPPET_CHARS));
                        return false;
                    }
                }
            } else {
                // Regular entry validation
                if (!validateBankEcritureFields(entry)) {
                    log.error("❌ Invalid bank ecriture at index {} — {}", i, summarizeNode(entry, MAX_ENTRY_SNIPPET_CHARS));
                    return false;
                }
            }
        }
        return true;
    }
}