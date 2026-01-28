package com.pacioli.core.batches;

import com.pacioli.core.config.batch.BatchProcessingConfig;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.PieceRepository;
import com.pacioli.core.services.PieceService;
import com.pacioli.core.services.serviceImp.DuplicateDetectionService;
import com.pacioli.core.batches.processors.AIResponseProcessor;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@EnableScheduling
public class AIPieceProcessingService {
    @Autowired
    private BatchProcessingConfig batchConfig;
    @Autowired
    private PieceRepository pieceRepository;
    @Autowired
    private PieceService pieceService;
    @Autowired
    private DuplicateDetectionService duplicateDetectionService;
    @Autowired
    private AIResponseProcessor aiResponseProcessor;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void processPieceBatch() {
        List<Piece> pendingPieces = findPendingPieces();

        // Filter out bank statements (process all EXCEPT bank)
        List<Piece> nonBankPieces = pendingPieces.stream()
                .filter(piece -> !"Relevés bancaires".equals(piece.getType()))
                .collect(Collectors.toList());

        if (nonBankPieces.isEmpty()) {
            log.info("⏭️ No pending non-bank pieces to process");
            return;
        }

        log.info("⭐️ Starting batch processing of {} non-bank pieces", nonBankPieces.size());
        processPiecesConcurrently(nonBankPieces);
        log.info("✅ Non-bank batch processing completed");
    }

    @Scheduled(fixedRate = 300000) // 300000 ms = 5 minutes
    @Transactional
    public void processBankPieceBatch() {
        List<Piece> pendingPieces = findPendingPiecesForBank();

        // Filter only bank statements
        List<Piece> bankPieces = pendingPieces.stream()
                .filter(piece -> "Relevés bancaires".equals(piece.getType()))
                .collect(Collectors.toList());

        if (!bankPieces.isEmpty()) {
            log.info("🏦 Starting BANK batch processing of {} pieces", bankPieces.size());
            processPiecesConcurrently(bankPieces);
        } else {
            log.info("⏭️ No pending bank statements to process");
        }
    }

    private List<Piece> findPendingPiecesForBank() {
        // Get UPLOADED bank pieces first (priority for new pieces)
        List<Piece> uploadedPieces = pieceRepository
                .findTopNByStatusOrderByUploadDateAsc(PieceStatus.UPLOADED, Pageable.ofSize(batchConfig.getBatchSize()))
                .stream()
                .filter(piece -> !duplicateDetectionService.isDuplicate(piece))
                .filter(piece -> "Relevés bancaires".equals(piece.getType())) // Only bank statements
                .collect(Collectors.toList());

        // If we have UPLOADED pieces, use them and don't include PROCESSING pieces
        if (!uploadedPieces.isEmpty()) {
            return uploadedPieces;
        }

        // Only get PROCESSING pieces if no UPLOADED pieces found
        List<Piece> processingPieces = pieceRepository
                .findTopNByStatusOrderByUploadDateAsc(PieceStatus.PROCESSING, Pageable.ofSize(batchConfig.getBatchSize()))
                .stream()
                .filter(piece -> !duplicateDetectionService.isDuplicate(piece))
                .filter(piece -> "Relevés bancaires".equals(piece.getType())) // Only bank statements
                .collect(Collectors.toList());

        return processingPieces;
    }

    // scheduled method for bank statements (every 5 minutes)
    private List<Piece> findPendingPieces() {
        // Get UPLOADED pieces first (new pieces - priority)
        List<Piece> uploadedPieces = pieceRepository
                .findTopNByStatusOrderByUploadDateAsc(PieceStatus.UPLOADED, Pageable.ofSize(batchConfig.getBatchSize()))
                .stream()
                .filter(piece -> !duplicateDetectionService.isDuplicate(piece))
                .collect(Collectors.toList());

        // If we have UPLOADED pieces, process them immediately (1-minute cycle)
        if (!uploadedPieces.isEmpty()) {
            return uploadedPieces;
        }

        // Only get PROCESSING pieces if no UPLOADED pieces found
        List<Piece> processingPieces = pieceRepository
                .findTopNByStatusOrderByUploadDateAsc(PieceStatus.PROCESSING, Pageable.ofSize(batchConfig.getBatchSize()))
                .stream()
                .filter(piece -> !duplicateDetectionService.isDuplicate(piece))
                .collect(Collectors.toList());

        return processingPieces;
    }

    private void processPiecesConcurrently(List<Piece> pieces) {
        List<CompletableFuture<Void>> futures = pieces.stream()
                .map(this::processSinglePieceAsync)
                .collect(Collectors.toList());

        // Wait for all completions
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(10, TimeUnit.MINUTES) // Add timeout
                .join();
    }

    @Async("batchTaskExecutor")
    public CompletableFuture<Void> processSinglePieceAsync(Piece piece) {
        return CompletableFuture.runAsync(() -> processSinglePiece(piece));
    }

    private void processSinglePiece(Piece piece) {
        try {
            Piece currentPiece = pieceRepository.findById(piece.getId()).orElse(piece);

            if (shouldSkipProcessing(currentPiece)) {
                log.info("⏭️ Skipping piece {} - status: {}", currentPiece.getId(), currentPiece.getStatus());
                return;
            }
             // log.info("🔄 Processing piece {} - current status: {}", currentPiece.getId(), currentPiece.getStatus());
            // Process the piece through AI - use configurable retries
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