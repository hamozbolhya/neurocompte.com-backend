package com.pacioli.core.DTO;

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
    private FactureDataDTO factureData;
    private List<EcritureDTO> ecritures;
    private String dossierName;
    private Long dossierId;

}
