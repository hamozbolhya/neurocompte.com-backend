package com.pacioli.core.DTO;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetStatsDTO {

    private Long cabinetId;

    private String cabinetName;

    private String userEmail;

    private Long dossierCount;

    private Long pieceCount;
}
