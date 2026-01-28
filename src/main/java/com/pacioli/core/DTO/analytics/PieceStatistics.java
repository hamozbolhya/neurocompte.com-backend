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
public class PieceStatistics {
    private Long totalPieces;
    private Long uploadedPieces;      // UPLOADED status
    private Long processingPieces;    // PROCESSING status
    private Long processedPieces;     // PROCESSED status
    private Long rejectedPieces;      // REJECTED status
    private Long duplicatePieces;     // DUPLICATE status - ADD THIS BACK
    private Long forcedPieces;        // isForced = true count
    private Map<String, Long> piecesByStatus;
    private List<PieceUploadTrend> uploadTrends;
    private Map<Long, Long> forcedPiecesByCabinet;  // Forced pieces by cabinet
    private Map<Long, Long> forcedPiecesByDossier;  // Forced pieces by dossier
}

