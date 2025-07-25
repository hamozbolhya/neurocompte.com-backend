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
public class CabinetStatistics {
    private Long totalCabinets;
    private Long activeCabinets;
    private Long inactiveCabinets;
    private List<CabinetCreationTrend> creationTrends;
}