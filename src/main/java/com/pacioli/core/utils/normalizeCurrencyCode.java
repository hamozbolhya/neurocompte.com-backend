package com.pacioli.core.utils;

import lombok.extern.slf4j.Slf4j;

/**
 * Helper method to normalize currency codes or symbols to standard ISO codes
 * This handles cases where AI returns currency symbols (like $) instead of codes (like USD)
 */


@Slf4j
public class normalizeCurrencyCode {
    private String normalizeCurrencyCode(String input) {
        if (input == null || input.trim().isEmpty()) {
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
                return "USD";

            case "€":
            case "EUR€":
            case "EU€":
            case "EURO":
            case "EUROS":
                return "EUR";

            case "£":
            case "GBP£":
            case "UK£":
            case "POUND":
            case "POUNDS":
                return "GBP";

            case "¥":
            case "JPY¥":
            case "JP¥":
            case "YEN":
                return "JPY";

            case "DH":
            case "MAD":
            case "DIRHAM":
            case "DIRHAMS":
                return "MAD";

            case "DA":
            case "DZD":
            case "DINAR":
            case "DINARS":
                return "DZD";

            case "CFA":
            case "XOF":
            case "FRANC":
            case "FRANCS":
                return "XOF";

            default:
                // If it's already a 3-letter code, assume it's valid
                if (normalized.length() == 3 && normalized.matches("[A-Z]{3}")) {
                    return normalized;
                }
                // Default to USD for unrecognized inputs
                log.warn("Unrecognized currency code/symbol: '{}', defaulting to USD", input);
                return "USD";
        }
    }
}