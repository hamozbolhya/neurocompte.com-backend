package com.pacioli.core.batches;

import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.PieceRepository;
import com.pacioli.core.services.PieceService;
import com.pacioli.core.services.serviceImp.DuplicateDetectionService;
import com.pacioli.core.batches.processors.AIResponseProcessor;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
@EnableScheduling
public class AIPieceProcessingService {
    private static final int BATCH_SIZE = 20;

    @Autowired private PieceRepository pieceRepository;
    @Autowired private PieceService pieceService;
    @Autowired private DuplicateDetectionService duplicateDetectionService;
    @Autowired private AIResponseProcessor aiResponseProcessor;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void processPieceBatch() {
        List<Piece> pendingPieces = findPendingPieces();

        if (pendingPieces.isEmpty()) {
            log.info("⏭️ No pending pieces to process");
            return;
        }

        log.info("⭐️ Starting batch processing of {} pieces", pendingPieces.size());
        processPiecesConcurrently(pendingPieces);
        log.info("✅ Batch processing completed");
    }

    private List<Piece> findPendingPieces() {
        List<Piece> uploadedPieces = pieceRepository
                .findTop20ByStatusOrderByUploadDateAsc(PieceStatus.UPLOADED)
                .stream()
                .filter(piece -> !duplicateDetectionService.isDuplicate(piece))
                .collect(Collectors.toList());

        if (uploadedPieces.isEmpty()) {
            return pieceRepository
                    .findTop20ByStatusOrderByUploadDateAsc(PieceStatus.PROCESSING)
                    .stream()
                    .filter(piece -> !duplicateDetectionService.isDuplicate(piece))
                    .collect(Collectors.toList());
        }

        return uploadedPieces;
    }

    private void processPiecesConcurrently(List<Piece> pieces) {
        ExecutorService executor = Executors.newFixedThreadPool(BATCH_SIZE);
        List<CompletableFuture<Void>> futures = pieces.stream()
                .map(piece -> CompletableFuture.runAsync(() -> processSinglePiece(piece), executor))
                .collect(Collectors.toList());

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
    }

    private void processSinglePiece(Piece piece) {
        try {
            Piece currentPiece = pieceRepository.findById(piece.getId()).orElse(piece);

            if (shouldSkipProcessing(currentPiece)) {
                log.info("⏭️ Skipping piece {} - status: {}", currentPiece.getId(), currentPiece.getStatus());
                return;
            }

            log.info("🔄 Processing piece {} - current status: {}", currentPiece.getId(), currentPiece.getStatus());

            // Process the piece through AI
            aiResponseProcessor.processPieceWithRetry(currentPiece, 1);

            // ✅ CRITICAL FIX: Reload the piece after AI processing to get updated AI data
            Piece processedPiece = pieceRepository.findById(piece.getId())
                    .orElseThrow(() -> new RuntimeException("Piece not found after processing: " + piece.getId()));

            log.info("✅ AI processing completed for piece {} - new status: {}, AI Amount: {}, AI Currency: {}",
                    processedPiece.getId(), processedPiece.getStatus(),
                    processedPiece.getAiAmount(), processedPiece.getAiCurrency());

            // ✅ CRITICAL FIX: Force WebSocket notification with the updated piece data
            notifyPiecesUpdate(processedPiece.getDossier().getId());

        } catch (Exception e) {
            log.error("❌ Failed to process piece {}: {}", piece.getId(), e.getMessage());
            rejectPiece(piece, "Processing failed: " + e.getMessage());
        }
    }

    private boolean shouldSkipProcessing(Piece piece) {
        boolean shouldSkip = piece.getStatus() == PieceStatus.PROCESSED ||
                piece.getStatus() == PieceStatus.DUPLICATE ||
                duplicateDetectionService.isDuplicate(piece);

        if (shouldSkip) {
            log.debug("⏭️ Skipping piece {} - Status: {}, IsDuplicate: {}",
                    piece.getId(), piece.getStatus(), duplicateDetectionService.isDuplicate(piece));
        }

        return shouldSkip;
    }

    private void rejectPiece(Piece piece, String reason) {
        log.error("❌ Rejecting piece {}: {}", piece.getId(), reason);
        updatePieceStatus(piece, PieceStatus.REJECTED);

        // ✅ Ensure notification is sent for rejected pieces too
        notifyPiecesUpdate(piece.getDossier().getId());
    }

    private void updatePieceStatus(Piece piece, PieceStatus status) {
        Piece currentPiece = pieceRepository.findById(piece.getId()).orElse(piece);
        currentPiece.setStatus(status);

        // ✅ Preserve AI data if it exists
        if (piece.getAiAmount() != null) {
            currentPiece.setAiAmount(piece.getAiAmount());
        }
        if (piece.getAiCurrency() != null) {
            currentPiece.setAiCurrency(piece.getAiCurrency());
        }

        Piece savedPiece = pieceRepository.save(currentPiece);

        log.info("📝 Updated piece {} status to: {}", savedPiece.getId(), status);

        // WebSocket notification will be handled by the calling method
    }

    private void notifyPiecesUpdate(Long dossierId) {
        try {
            log.info("📢 Sending WebSocket notification for dossier {}", dossierId);
            pieceService.notifyPiecesUpdate(dossierId);
            log.info("✅ WebSocket notification sent for dossier {}", dossierId);
        } catch (Exception e) {
            log.error("❌ Failed to notify WebSocket for dossier {}: {}", dossierId, e.getMessage());
        }
    }
}