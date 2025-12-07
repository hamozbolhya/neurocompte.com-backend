package com.pacioli.core.cleanup;

import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.PieceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class PieceCleanupService {

    @Autowired
    private PieceRepository pieceRepository;

    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void cleanupStaleProcessingPieces() {
        log.info("🧹 Cleaning up stale processing pieces...");

        // Find pieces stuck in PROCESSING status for more than 1 hour
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        List<Piece> stalePieces = pieceRepository.findByStatusAndUploadDateBefore(
                PieceStatus.PROCESSING,
                Date.from(oneHourAgo.atZone(ZoneId.systemDefault()).toInstant())
        );

        for (Piece piece : stalePieces) {
            log.info("Resetting stale piece {} from PROCESSING to UPLOADED", piece.getId());
            piece.setStatus(PieceStatus.UPLOADED);
            pieceRepository.save(piece);
        }

        log.info("✅ Cleaned up {} stale pieces", stalePieces.size());
    }
}
