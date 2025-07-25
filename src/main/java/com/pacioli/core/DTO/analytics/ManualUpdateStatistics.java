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
public  class ManualUpdateStatistics {
    private Long totalManuallyUpdatedEcritures;
    private Long totalManuallyUpdatedLines;
    private Map<Long, Long> manuallyUpdatedEcrituresByCabinet;
    private Map<Long, Long> manuallyUpdatedLinesByCabinet;
    private Map<Long, String> manualUpdateCabinetNames; // For display purposes
    private List<ManualUpdateTrend> manualUpdateTrends;
    private Map<String, Long> manualUpdatesByPeriod; // Daily, weekly, monthly stats
    private Double manualUpdatePercentage; // Percentage of total ecritures that are manually updated
}
