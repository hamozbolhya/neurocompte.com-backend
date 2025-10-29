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

        String[] requiredFields = {"Date", "JournalCode", "JournalLib", "FactureNum", "CompteNum", "CompteLib", "EcritLib", "Devise"};
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

            if (!isValidNumericValue(entry.get(field).asText())) {
                log.error("❌ Invalid numeric value for field {}: {}", field, entry.get(field).asText());
                return false;
            }
        }
        return true;
    }

    private boolean isValidNumericValue(String value) {
        try {
            double num = Double.parseDouble(value.trim());
            return !Double.isInfinite(num) && !Double.isNaN(num);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}