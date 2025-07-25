package com.pacioli.core.DTO.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAnalyticsDTO {
    private CabinetStatistics cabinetStats;
    private PieceStatistics pieceStats;
    private DossierStatistics dossierStats;
    private HistoriqueStatistics historiqueStats;
    private List<CabinetAnalytics> cabinetAnalytics;
}