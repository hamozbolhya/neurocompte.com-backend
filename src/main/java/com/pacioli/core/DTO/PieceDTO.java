package com.pacioli.core.DTO;

import com.pacioli.core.enums.PieceStatus;
import lombok.Builder;
import lombok.Data;

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

}
