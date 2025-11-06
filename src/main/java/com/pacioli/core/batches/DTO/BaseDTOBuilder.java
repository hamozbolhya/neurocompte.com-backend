package com.pacioli.core.batches.DTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
public class BaseDTOBuilder {

    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected NormalizeCurrencyCode normalizeCurrencyCode;

    protected static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-d"),
            DateTimeFormatter.ofPattern("yyyy-M-dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy-dd-MM")
    );

    // ==================== UTILITY METHODS ====================

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

    protected String formatDateToStandard(String dateStr) {
        try {
            LocalDate date = parseDate(dateStr);
            return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            log.trace("❌ Date formatting failed for: {}", dateStr);
            return LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
    }

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

    protected String extractStringSafely(JsonNode node, String fieldName, String defaultValue) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return defaultValue;
        }
        String value = node.get(fieldName).asText();
        return (value == null || value.trim().isEmpty()) ? defaultValue : value.trim();
    }

    protected JsonNode findEcrituresNode(JsonNode parsedJson) {
        // First check for normal format
        if (parsedJson.has("ecritures")) return parsedJson.get("ecritures");
        if (parsedJson.has("Ecritures")) {
            JsonNode ecrituresNode = parsedJson.get("Ecritures");

            // Handle bank statement format with transaction groups
            if (ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                JsonNode firstItem = ecrituresNode.get(0);

                // Check for transaction groups with entries
                if (firstItem.isObject() && firstItem.has("entries")) {
                    log.info("🏦 Detected bank statement format with transaction groups in DTO");
                    return extractAllEntriesFromTransactionGroups(ecrituresNode);
                }

                // Check for nested format
                if (firstItem.isArray() && firstItem.size() > 0) {
                    JsonNode nestedFirst = firstItem.get(0);
                    if (nestedFirst.isObject() && nestedFirst.has("entries")) {
                        log.info("🏦 Detected nested bank statement format in DTO");
                        return extractAllEntriesFromNestedGroups(ecrituresNode);
                    }
                }
            }

            // If not bank format, return as-is (normal format)
            log.info("📄 Detected normal Ecritures format in DTO");
            return ecrituresNode;
        }
        return null;
    }

    // Add the same helper methods to BaseDTOBuilder
    private JsonNode extractAllEntriesFromTransactionGroups(JsonNode transactionGroups) {
        ArrayNode allEntries = objectMapper.createArrayNode();

        for (JsonNode group : transactionGroups) {
            if (group.has("entries") && group.get("entries").isArray()) {
                JsonNode entries = group.get("entries");
                entries.forEach(allEntries::add);
            }
        }

        log.info("🏦 Combined {} entries from {} transaction groups in DTO", allEntries.size(), transactionGroups.size());
        return allEntries;
    }

    private JsonNode extractAllEntriesFromNestedGroups(JsonNode nestedGroups) {
        ArrayNode allEntries = objectMapper.createArrayNode();

        for (JsonNode outerGroup : nestedGroups) {
            if (outerGroup.isArray()) {
                for (JsonNode innerGroup : outerGroup) {
                    if (innerGroup.isObject() && innerGroup.has("entries") && innerGroup.get("entries").isArray()) {
                        JsonNode entries = innerGroup.get("entries");
                        entries.forEach(allEntries::add);
                    }
                }
            }
        }

        log.info("🏦 Combined {} entries from nested transaction groups in DTO", allEntries.size());
        return allEntries;
    }
}
