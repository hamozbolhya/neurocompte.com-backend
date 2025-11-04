package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PieceValidator {

    @Autowired private ObjectMapper objectMapper;

    public boolean isValidAIResponse(JsonNode root) {
        return root.has("outputText") && validateEcritures(root.get("outputText"));
    }

    public boolean validateEcritures(JsonNode node) {
        try {
            String textValue = node.asText();

            if (textValue == null || textValue.trim().isEmpty()) {
                log.error("❌ Empty output text from AI service");
                return false;
            }

            JsonNode parsedJson = parseJsonText(textValue);
            JsonNode ecritures = findEcrituresNode(parsedJson);

            return isValidEcrituresArray(ecritures);

        } catch (Exception e) {
            log.error("💥 Error validating ecritures: {}", e.getMessage(), e);
            return false;
        }
    }

    private JsonNode parseJsonText(String textValue) throws JsonProcessingException {
        return objectMapper.readTree(textValue);
    }

    private JsonNode findEcrituresNode(JsonNode parsedJson) {
        if (parsedJson.has("ecritures")) return parsedJson.get("ecritures");
        if (parsedJson.has("Ecritures")) return parsedJson.get("Ecritures");
        return null;
    }

    private boolean isValidEcrituresArray(JsonNode ecritures) {
        if (ecritures == null || !ecritures.isArray() || ecritures.size() == 0) {
            log.error("❌ Invalid ecritures array");
            return false;
        }

        return validateAllEcritureEntries(ecritures);
    }

    private boolean validateAllEcritureEntries(JsonNode ecritures) {
        for (int i = 0; i < ecritures.size(); i++) {
            if (!validateEcritureFields(ecritures.get(i))) {
                log.error("❌ Invalid ecriture at index {}: {}", i, ecritures.get(i));
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

        // Bank statements don't require FactureNum
        String[] requiredFields = {"Date", "JournalCode", "JournalLib", "CompteNum", "CompteLib", "EcritLib", "Devise"};
        String[] numericFields = {"DebitAmt", "CreditAmt"};

        return hasAllRequiredFields(entry, requiredFields) &&
                hasValidNumericFields(entry, numericFields);
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
        return root.has("outputText") && validateBankEcritures(root.get("outputText"));
    }

    public boolean validateBankEcritures(JsonNode node) {
        try {
            String textValue = node.asText();

            if (textValue == null || textValue.trim().isEmpty()) {
                log.error("❌ Empty output text from bank AI service");
                return false;
            }

            JsonNode parsedJson = parseJsonText(textValue);
            JsonNode ecritures = findBankEcrituresNode(parsedJson);

            return isValidBankEcrituresArray(ecritures);

        } catch (Exception e) {
            log.error("💥 Error validating bank ecritures: {}", e.getMessage(), e);
            return false;
        }
    }

    private JsonNode findBankEcrituresNode(JsonNode parsedJson) {
        if (parsedJson.has("Ecritures")) {
            JsonNode ecrituresNode = parsedJson.get("Ecritures");
            if (ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                JsonNode firstItem = ecrituresNode.get(0);
                if (firstItem.isArray() && firstItem.size() > 0) {
                    JsonNode entriesNode = firstItem.get(0);
                    if (entriesNode.has("entries")) {
                        return entriesNode.get("entries");
                    }
                }
            }
        }
        return null;
    }

    private boolean isValidBankEcrituresArray(JsonNode entries) {
        if (entries == null || !entries.isArray() || entries.size() == 0) {
            log.error("❌ Invalid bank entries array");
            return false;
        }

        return validateAllBankEcritureEntries(entries);
    }

    private boolean validateAllBankEcritureEntries(JsonNode entries) {
        for (int i = 0; i < entries.size(); i++) {
            if (!validateBankEcritureFields(entries.get(i))) {
                log.error("❌ Invalid bank ecriture at index {}: {}", i, entries.get(i));
                return false;
            }
        }
        return true;
    }
}