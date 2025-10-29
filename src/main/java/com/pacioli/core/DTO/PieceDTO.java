package com.pacioli.core.DTO;

import com.pacioli.core.enums.PieceStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Slf4j
@Data
public class PieceDTO {
    private Long id;
    private String filename;
    private String originalFileName;

    private String type;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "UTC")
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

    private Boolean exchangeRateUpdated = false;
    // New field to store the currency from AI response
    private String aiCurrency;
    private Double aiAmount; // New field for AI amount

    // New fields for tracking exchange rate details
    private String convertedCurrency; // The currency the amount was converted to
    private LocalDate exchangeRateDate; // The date used for the exchange rate

    private Boolean isDuplicate;

    private Boolean isForced;

    private Long originalPieceId;
    private String originalPieceName;


    public void setEcritures(List<EcrituresDTO2> ecritures) {
        this.ecritures = ecritures;
        // Debug log
        if (ecritures != null) {
            log.info("📊 Set {} ecritures in PieceDTO", ecritures.size());
            for (int i = 0; i < ecritures.size(); i++) {
                EcrituresDTO2 ecriture = ecritures.get(i);
                log.info("  Ecriture {}: {} lines", i,
                        ecriture.getLines() != null ? ecriture.getLines().size() : 0);
            }
        }
    }
}