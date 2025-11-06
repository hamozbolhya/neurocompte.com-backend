package com.pacioli.core.batches.processors.detection;

import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.PieceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class DuplicationDetectionService {

    @Autowired
    private PieceRepository pieceRepository;

    public boolean isDuplicate(Piece piece) {
        if (piece == null || piece.getFilename() == null) {
            return false;
        }

        // Check by filename
        if (isDuplicateByFilename(piece)) {
            return true;
        }

        // Check by content hash (if available)
        if (isDuplicateByFileHash(piece)) {
            return true;
        }

        // Check by AI data signature (amount + currency + date)
        if (isAIDataDuplicate(piece)) {
            return true;
        }

        return false;
    }

    private boolean isDuplicateByFilename(Piece piece) {
        Optional<Piece> existingPiece = pieceRepository.findByFilename(piece.getFilename());
        if (existingPiece.isPresent() && !existingPiece.get().getId().equals(piece.getId())) {
            log.info("🚫 Detected duplicate by filename: {}", piece.getFilename());
            return true;
        }
        return false;
    }

    private boolean isDuplicateByFileHash(Piece piece) {
        // Check if Piece entity has fileHash field - if not, skip this check
        try {
            // Using reflection to check if fileHash field exists
            piece.getClass().getDeclaredField("fileHash");

            if (piece.getFileHash() != null) {
                List<Piece> sameHashPieces = pieceRepository.findByFileHash(piece.getFileHash());
                if (sameHashPieces.stream().anyMatch(p -> !p.getId().equals(piece.getId()))) {
                    log.info("🚫 Detected duplicate by file hash: {}", piece.getFileHash());
                    return true;
                }
            }
        } catch (NoSuchFieldException e) {
            log.debug("FileHash field not available in Piece entity, skipping file hash check");
        }
        return false;
    }

    private boolean isAIDataDuplicate(Piece piece) {
        if (piece.getAiAmount() == null || piece.getAiCurrency() == null || piece.getUploadDate() == null) {
            return false;
        }

        // Calculate tolerance (1% of amount)
        double tolerance = piece.getAiAmount() * 0.01;
        double minAmount = piece.getAiAmount() - tolerance;
        double maxAmount = piece.getAiAmount() + tolerance;

        // Find pieces with similar AI data in the same dossier
        List<Piece> similarPieces = pieceRepository.findSimilarAIData(
                piece.getDossier().getId(),
                minAmount,
                maxAmount,
                piece.getAiCurrency(),
                piece.getUploadDate()
        );

        boolean isDuplicate = similarPieces.stream()
                .anyMatch(p -> !p.getId().equals(piece.getId()));

        if (isDuplicate) {
            log.info("🚫 Detected duplicate by AI data: amount={}, currency={}, date={}",
                    piece.getAiAmount(), piece.getAiCurrency(), piece.getUploadDate());
        }

        return isDuplicate;
    }
}