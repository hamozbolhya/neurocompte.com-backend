package com.pacioli.core.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;

@Data
@NoArgsConstructor
public class EcritureExportDTO {

    private String uniqueEntryNumber; // e.uniqueEntryNumber
    private LocalDate entryDate;      // e.entryDate
    private String journalName;       // j.name
    private String pieceFilename;     // p.filename
    private String accountLabel;      // a.label
    private String lineLabel;         // l.label
    private Double debit;             // l.debit
    private Double credit;            // l.credit
    private String invoiceNumber;     // fd.invoiceNumber
    private Date invoiceDate;         // fd.invoiceDate
    private Double totalTTC;          // fd.totalTTC
    private Double totalHT;           // fd.totalHT
    private Double totalTVA;          // fd.totalTVA
    private Double taxRate;           // fd.taxRate
    private String tier;              // fd.tier
    private String ice;               // fd.ice

    private Double debitTotal;
    private Double creditTotal;

    public EcritureExportDTO(
            String uniqueEntryNumber,
            LocalDate entryDate,
            String journalName,
            String pieceFilename,
            String accountLabel,
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
}
