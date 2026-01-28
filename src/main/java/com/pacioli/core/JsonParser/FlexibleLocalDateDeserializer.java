package com.pacioli.core.JsonParser;// There are several approaches to fix this issue:

// Option 1: Add a custom Jackson deserializer for LocalDate
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// Create a custom deserializer
public class FlexibleLocalDateDeserializer extends JsonDeserializer<LocalDate> {
    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String dateStr = p.getText();

        try {
            // Try to parse with the default ISO format (yyyy-MM-dd)
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e1) {
            try {
                // Try with dd/MM/yyyy format
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e2) {
                throw new IOException("Cannot parse date '" + dateStr + "'. Expected formats: yyyy-MM-dd or dd/MM/yyyy", e2);
            }
        }
    }
}