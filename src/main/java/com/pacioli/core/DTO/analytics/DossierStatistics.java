package com.pacioli.core.DTO.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DossierStatistics {
    private Long totalDossiers;
    private Long activeDossiers;
    private Map<Long, Long> dossiersByCabinet;
    private List<DossierCreationTrend> creationTrends;
}

