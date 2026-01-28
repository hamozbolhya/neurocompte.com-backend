package com.pacioli.core.utils;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class to normalize currency codes or symbols to standard ISO codes
 * This handles cases where AI returns currency symbols (like $) instead of codes (like USD)
 */
@Slf4j
@Component  // Add Component annotation to make it injectable
public class NormalizeCurrencyCode {

    /**
     * Normalizes a currency code or symbol to a standard ISO code
     *
     * @param input The currency code or symbol to normalize
     * @return The normalized ISO currency code, or the original input if it's already a valid code
     */
    public String normalizeCurrencyCode(String input) {
        if (input == null || input.trim().isEmpty()) {
            log.debug("Empty currency code provided, returning null");
            return null;
        }

        // Trim and convert to uppercase
        String normalized = input.trim().toUpperCase();

        // Map of common currency symbols to their ISO codes
        switch (normalized) {
            case "$":
            case "USD$":
            case "US$":
            case "DOLLAR":
            case "DOLLARS":
            case "US DOLLAR":
            case "US DOLLARS":
            case "UNITED STATES DOLLAR":
                log.debug("Normalized '{}' to USD", input);
                return "USD";

            case "€":
            case "EUR€":
            case "EU€":
            case "EURO":
            case "EUROS":
                log.debug("Normalized '{}' to EUR", input);
                return "EUR";

            case "£":
            case "GBP£":
            case "UK£":
            case "POUND":
            case "POUNDS":
            case "BRITISH POUND":
                log.debug("Normalized '{}' to GBP", input);
                return "GBP";

            case "¥":
            case "JPY¥":
            case "JP¥":
            case "YEN":
                log.debug("Normalized '{}' to JPY", input);
                return "JPY";

            case "DH":
            case "MAD":
            case "DIRHAM":
            case "DIRHAMS":
            case "MOROCCAN DIRHAM":
                log.debug("Normalized '{}' to MAD", input);
                return "MAD";

            case "DA":
            case "DZD":
            case "DINAR":
            case "DINARS":
            case "ALGERIAN DINAR":
                log.debug("Normalized '{}' to DZD", input);
                return "DZD";

            case "CFA":
            case "XOF":
            case "FRANC":
            case "FRANCS":
            case "CFA FRANC":
                log.debug("Normalized '{}' to XOF", input);
                return "XOF";

            default:
                // If it's already a 3-letter code, assume it's valid
                if (normalized.length() == 3 && normalized.matches("[A-Z]{3}")) {
                    log.debug("Using existing 3-letter currency code: {}", normalized);
                    return normalized;
                }

                // For unrecognized inputs, try to extract a valid code
                if (normalized.contains("USD") || normalized.contains("DOLLAR")) {
                    log.info("Extracted USD from: '{}'", input);
                    return "USD";
                } else if (normalized.contains("EUR") || normalized.contains("EURO")) {
                    log.info("Extracted EUR from: '{}'", input);
                    return "EUR";
                } else if (normalized.contains("MAD") || normalized.contains("DIRHAM")) {
                    log.info("Extracted MAD from: '{}'", input);
                    return "MAD";
                }

                // Default to USD for unrecognized inputs
                log.warn("Unrecognized currency code/symbol: '{}', defaulting to USD", input);
                return "USD";
        }
    }
}