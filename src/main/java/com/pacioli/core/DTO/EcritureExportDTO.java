package com.pacioli.core.DTO;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;

@Data
@NoArgsConstructor
public class EcritureExportDTO {

    // Original fields
    private String uniqueEntryNumber;
    private LocalDate entryDate;
    private String journalName;
    private String pieceFilename;
    private String accountLabel;
    private String accountNumber;
    private String lineLabel;
    private Double debit;
    private Double credit;
    private String invoiceNumber;
    private Date invoiceDate;
    private Double totalTTC;
    private Double totalHT;
    private Double totalTVA;
    private Double taxRate;
    private String tier;
    private String ice;
    private Double debitTotal;
    private Double creditTotal;

    // New currency conversion fields
    private String originalCurrency;
    private String convertedCurrency;
    private Double exchangeRate;
    private LocalDate exchangeRateDate;

    // Original amounts before conversion
    private Double originalDebit;
    private Double originalCredit;

    // Converted amounts
    private Double convertedDebit;
    private Double convertedCredit;
    private Double convertedTotalTTC;
    private Double convertedTotalHT;
    private Double convertedTotalTVA;

    // USD equivalents
    private Double usdDebit;
    private Double usdCredit;
    private Double usdTotalTTC;
    private Double usdTotalHT;
    private Double usdTotalTVA;

    // Original constructor
    public EcritureExportDTO(
            String uniqueEntryNumber,
            LocalDate entryDate,
            String journalName,
            String pieceFilename,
            String accountLabel,
            String accountNumber,
            String lineLabel,
            Double debit,
            Double credit,
            String invoiceNumber,
            Date invoiceDate,
            Double totalTTC,
            Double totalHT,
            Double totalTVA,
            Double taxRate,
            String tier,
            String ice,
            Double debitTotal,
            Double creditTotal
    ) {
        this.uniqueEntryNumber = uniqueEntryNumber;
        this.entryDate = entryDate;
        this.journalName = journalName;
        this.pieceFilename = pieceFilename;
        this.accountLabel = accountLabel;
        this.accountNumber = accountNumber;
        this.lineLabel = lineLabel;
        this.debit = debit;
        this.credit = credit;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.totalTTC = totalTTC;
        this.totalHT = totalHT;
        this.totalTVA = totalTVA;
        this.taxRate = taxRate;
        this.tier = tier;
        this.ice = ice;
        this.debitTotal = debitTotal;
        this.creditTotal = creditTotal;
    }

    // Enhanced constructor including all new currency-related fields
    public EcritureExportDTO(
            String uniqueEntryNumber,
            LocalDate entryDate,
            String journalName,
            String pieceFilename,
            String accountLabel,
            String accountNumber,
            String lineLabel,
            Double debit,
            Double credit,
            String invoiceNumber,
            Date invoiceDate,
            Double totalTTC,
            Double totalHT,
            Double totalTVA,
            Double taxRate,
            String tier,
            String ice,
            Double debitTotal,
            Double creditTotal,
            // New fields below
            String originalCurrency,
            String convertedCurrency,
            Double exchangeRate,
            LocalDate exchangeRateDate,
            Double originalDebit,
            Double originalCredit,
            Double convertedDebit,
            Double convertedCredit,
            Double convertedTotalTTC,
            Double convertedTotalHT,
            Double convertedTotalTVA,
            Double usdDebit,
            Double usdCredit,
            Double usdTotalTTC,
            Double usdTotalHT,
            Double usdTotalTVA
    ) {
        // Initialize original fields
        this.uniqueEntryNumber = uniqueEntryNumber;
        this.entryDate = entryDate;
        this.journalName = journalName;
        this.pieceFilename = pieceFilename;
        this.accountLabel = accountLabel;
        this.accountNumber = accountNumber;
        this.lineLabel = lineLabel;
        this.debit = debit;
        this.credit = credit;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.totalTTC = totalTTC;
        this.totalHT = totalHT;
        this.totalTVA = totalTVA;
        this.taxRate = taxRate;
        this.tier = tier;
        this.ice = ice;
        this.debitTotal = debitTotal;
        this.creditTotal = creditTotal;

        // Initialize new fields
        this.originalCurrency = originalCurrency;
        this.convertedCurrency = convertedCurrency;
        this.exchangeRate = exchangeRate;
        this.exchangeRateDate = exchangeRateDate;
        this.originalDebit = originalDebit;
        this.originalCredit = originalCredit;
        this.convertedDebit = convertedDebit;
        this.convertedCredit = convertedCredit;
        this.convertedTotalTTC = convertedTotalTTC;
        this.convertedTotalHT = convertedTotalHT;
        this.convertedTotalTVA = convertedTotalTVA;
        this.usdDebit = usdDebit;
        this.usdCredit = usdCredit;
        this.usdTotalTTC = usdTotalTTC;
        this.usdTotalHT = usdTotalHT;
        this.usdTotalTVA = usdTotalTVA;
    }
}