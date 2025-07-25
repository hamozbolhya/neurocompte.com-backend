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
public class HistoriqueStatistics {
    private Long totalHistoriqueFiles;
    private Map<Long, Long> historiqueFilesByCabinet;
    private List<HistoriqueUploadTrend> uploadTrends;
}