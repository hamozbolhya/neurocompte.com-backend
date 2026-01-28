package com.pacioli.core.DTO;

import lombok.Data;
import java.time.LocalDate;
import java.util.Date;

@Data
public class FactureDataDTO {
    private Long id;
    private String invoiceNumber;
    private Date invoiceDate;
    private Double totalTTC;
    private Double totalHT;
    private Double totalTVA;
    private Double taxRate;
    private String tier;
    private String ice;
    private String devise;

    // Exchange rate information
    private Double exchangeRate;
    private String originalCurrency;
    private String convertedCurrency;
    private LocalDate exchangeRateDate;

    // Converted amounts
    private Double convertedTotalTTC;
    private Double convertedTotalHT;
    private Double convertedTotalTVA;

    // USD equivalents
    private Double usdTotalTTC;
    private Double usdTotalHT;
    private Double usdTotalTVA;
}