package com.pacioli.core.DTO;

import com.pacioli.core.enums.PieceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
public class PieceDTO {
    private Long id;
    private String filename;
    private String type;
    private Date uploadDate;
    private Double amount;

    private PieceStatus status;

    private FactureDataDTO factureData;
    private List<EcrituresDTO2> ecritures;
    private String dossierName;
    private Long dossierId;

    private String originalCurrency;
    private String dossierCurrency;
    private Double exchangeRate;

    // New field to store the currency from AI response
    private String aiCurrency;
    private Double aiAmount; // New field for AI amount

    // New fields for tracking exchange rate details
    private String convertedCurrency; // The currency the amount was converted to
    private LocalDate exchangeRateDate; // The date used for the exchange rate
}