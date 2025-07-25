package com.pacioli.core.DTO.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetAnalytics {
    private Long cabinetId;
    private String cabinetName;
    private Long totalDossiers;
    private Long totalPieces;
    private Long totalUsers;
    private Map<String, Long> piecesByStatus;
    private Long totalHistoriqueFiles;
    private Long totalForcedPieces;
    private Map<Long, Long> forcedPiecesByDossier;
}