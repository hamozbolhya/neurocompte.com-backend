package com.pacioli.core.utils;

import com.pacioli.core.models.Ecriture;
import com.pacioli.core.models.Line;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class EcritureValidationUtil {

    /**
     * Validates that debit and credit totals are balanced using BigDecimal for precision
     * @param ecriture The ecriture to validate
     * @param decimalPrecision Number of decimal places (e.g., 2, 3, 4)
     * @return Map of errors, empty if validation passes
     */
    public static Map<String, String> validateEcritureBalance(Ecriture ecriture, int decimalPrecision) {
        Map<String, String> errors = new HashMap<>();

        if (ecriture.getLines() == null || ecriture.getLines().isEmpty()) {
            errors.put("lines", "Au moins une ligne comptable est requise.");
            return errors;
        }

        // ✅ USE BIGDECIMAL FOR EXACT PRECISION
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal totalConvertedDebit = BigDecimal.ZERO;
        BigDecimal totalConvertedCredit = BigDecimal.ZERO;

        boolean hasConvertedAmounts = false;

        for (Line line : ecriture.getLines()) {
            // Validate each line has either debit or credit, not both
            BigDecimal debit = line.getDebit() != null ?
                    BigDecimal.valueOf(line.getDebit()).setScale(decimalPrecision, RoundingMode.HALF_UP) :
                    BigDecimal.ZERO;
            BigDecimal credit = line.getCredit() != null ?
                    BigDecimal.valueOf(line.getCredit()).setScale(decimalPrecision, RoundingMode.HALF_UP) :
                    BigDecimal.ZERO;

            // Check if both debit and credit are non-zero
            BigDecimal tolerance = BigDecimal.ONE.divide(
                    BigDecimal.TEN.pow(decimalPrecision),
                    decimalPrecision + 2,
                    RoundingMode.HALF_UP
            );

            if (debit.compareTo(tolerance) > 0 && credit.compareTo(tolerance) > 0) {
                errors.put("line_" + line.getId(),
                        "Une ligne ne peut pas avoir à la fois un débit et un crédit.");
            }

            // Check if line has account
            if (line.getAccount() == null) {
                errors.put("line_account_" + line.getId(),
                        "Chaque ligne doit avoir un compte associé.");
            }

            // Add to totals based on amountUpdated flag
            if (ecriture.getAmountUpdated() != null && ecriture.getAmountUpdated()) {
                // Use converted amounts if they exist
                if (line.getConvertedDebit() != null) {
                    totalDebit = totalDebit.add(
                            BigDecimal.valueOf(line.getConvertedDebit())
                                    .setScale(decimalPrecision, RoundingMode.HALF_UP)
                    );
                    hasConvertedAmounts = true;
                } else {
                    totalDebit = totalDebit.add(debit);
                }

                if (line.getConvertedCredit() != null) {
                    totalCredit = totalCredit.add(
                            BigDecimal.valueOf(line.getConvertedCredit())
                                    .setScale(decimalPrecision, RoundingMode.HALF_UP)
                    );
                    hasConvertedAmounts = true;
                } else {
                    totalCredit = totalCredit.add(credit);
                }
            } else {
                totalDebit = totalDebit.add(debit);
                totalCredit = totalCredit.add(credit);
            }

            // Track converted amounts separately
            if (line.getConvertedDebit() != null) {
                totalConvertedDebit = totalConvertedDebit.add(
                        BigDecimal.valueOf(line.getConvertedDebit())
                                .setScale(decimalPrecision, RoundingMode.HALF_UP)
                );
                hasConvertedAmounts = true;
            }
            if (line.getConvertedCredit() != null) {
                totalConvertedCredit = totalConvertedCredit.add(
                        BigDecimal.valueOf(line.getConvertedCredit())
                                .setScale(decimalPrecision, RoundingMode.HALF_UP)
                );
                hasConvertedAmounts = true;
            }
        }

        // ✅ COMPARE USING BIGDECIMAL - NO FLOATING POINT ERRORS
        BigDecimal difference = totalDebit.subtract(totalCredit).abs();

        log.info("💰 Backend Validation (decimal={}): Debit={}, Credit={}, Difference={}",
                decimalPrecision, totalDebit, totalCredit, difference);

        // ✅ Totals must be EXACTLY equal (difference must be ZERO)
        if (difference.compareTo(BigDecimal.ZERO) != 0) {
            String errorMsg = String.format(
                    "La somme du débit (%s) doit être égale à la somme du crédit (%s). Différence: %s",
                    totalDebit.toPlainString(),
                    totalCredit.toPlainString(),
                    difference.toPlainString()
            );
            errors.put("total", errorMsg);
            log.warn("❌ Validation failed: {}", errorMsg);
        }

        // Validate converted amounts if they exist
        if (hasConvertedAmounts) {
            BigDecimal convertedDifference = totalConvertedDebit.subtract(totalConvertedCredit).abs();

            if (convertedDifference.compareTo(BigDecimal.ZERO) != 0) {
                String errorMsg = String.format(
                        "La somme du débit converti (%s) doit être égale à la somme du crédit converti (%s). Différence: %s",
                        totalConvertedDebit.toPlainString(),
                        totalConvertedCredit.toPlainString(),
                        convertedDifference.toPlainString()
                );
                errors.put("totalConverted", errorMsg);
                log.warn("❌ Converted amounts validation failed: {}", errorMsg);
            }
        }

        return errors;
    }

    /**
     * Validates exchange rate information
     */
    public static Map<String, String> validateExchangeRate(Ecriture ecriture) {
        Map<String, String> errors = new HashMap<>();

        if (ecriture.getLines() == null || ecriture.getLines().isEmpty()) {
            return errors;
        }

        // Check if any line has exchange rate information
        boolean hasExchangeRate = ecriture.getLines().stream()
                .anyMatch(line -> line.getExchangeRate() != null && line.getExchangeRate() > 0);

        if (hasExchangeRate) {
            for (Line line : ecriture.getLines()) {
                if (line.getExchangeRate() != null && line.getExchangeRate() > 0) {
                    // Validate that currencies are provided
                    if (line.getOriginalCurrency() == null || line.getOriginalCurrency().isEmpty()) {
                        errors.put("exchangeRate", "La devise d'origine est requise lorsqu'un taux de change est fourni.");
                    }
                    if (line.getConvertedCurrency() == null || line.getConvertedCurrency().isEmpty()) {
                        errors.put("exchangeRate", "La devise de conversion est requise lorsqu'un taux de change est fourni.");
                    }

                    // Validate that currencies are different
                    if (line.getOriginalCurrency() != null &&
                            line.getConvertedCurrency() != null &&
                            line.getOriginalCurrency().equals(line.getConvertedCurrency())) {
                        errors.put("exchangeRate", "Les devises d'origine et de conversion doivent être différentes.");
                    }
                }
            }
        }

        return errors;
    }
}