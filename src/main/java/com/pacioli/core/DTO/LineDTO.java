package com.pacioli.core.DTO;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LineDTO {
    private Long id;
    private String label;
    private Double debit;
    private Double credit;

    private AccountDTO account; // Full account object instead of just the name

    // Already existing fields for original values
    private Double originalDebit;
    private Double originalCredit;
    private String originalCurrency;
    private Double exchangeRate;

    // New fields for additional tracking
    private String convertedCurrency;
    private LocalDate exchangeRateDate;
    private Double usdDebit;
    private Double usdCredit;

    // Added converted amount fields
    private Double convertedDebit;
    private Double convertedCredit;
}