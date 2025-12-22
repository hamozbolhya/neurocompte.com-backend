package com.pacioli.core.batches.DTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    // ==================== UTILITY METHODS ====================

    protected LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDate.now();
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // Continue to next formatter
            }
        }
        return LocalDate.now();
    }

    protected String formatDateToStandard(String dateStr) {
        try {
            LocalDate date = parseDate(dateStr);
            return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
    }

    protected double parseDoubleSafely(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return 0.0;
        }

        try {
            String value = node.get(fieldName).asText();
            if (value == null || value.trim().isEmpty()) {
                return 0.0;
            }
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException e) {
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
        if (parsedJson.has("ecritures")) return parsedJson.get("ecritures");
        if (parsedJson.has("Ecritures")) return parsedJson.get("Ecritures");
        return null;
    }

    // Add this method to BaseDTOBuilder
    protected String cleanMarkdownCodeFences(String text) {
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
}