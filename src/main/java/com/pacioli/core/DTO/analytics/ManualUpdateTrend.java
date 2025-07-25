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
public class ManualUpdateTrend {
    private String period; // e.g., "2024-01", "2024-02"
    private Long ecritureCount;
    private Long lineCount;
    private Map<Long, Long> cabinetBreakdown;
}
