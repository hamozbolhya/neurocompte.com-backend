package com.pacioli.core.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;

@Data
@AllArgsConstructor
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
}
