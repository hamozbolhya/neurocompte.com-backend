package com.pacioli.core.batches.processors.detection;

import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.PieceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class DuplicationDetectionService {

    private static final Comparator<Piece> BY_UPLOAD_THEN_ID = Comparator
            .comparing(Piece::getUploadDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Piece::getId);

    @Autowired
    private PieceRepository pieceRepository;

    public boolean isDuplicate(Piece piece) {
        return findOriginalPiece(piece).isPresent();
    }

    /**
     * Returns the canonical original piece if {@code piece} is a duplicate,
     * or empty if it is the original (oldest) or no match exists.
     */
    public Optional<Piece> findOriginalPiece(Piece piece) {
        // #region agent log
        try (FileWriter fw = new FileWriter("/Users/hamzaboulahia/perso/neurocompte.com-backend/.cursor/debug-f12bb6.log", true)) {
            fw.write("{\"sessionId\":\"f12bb6\",\"runId\":\"forced-check-1\",\"hypothesisId\":\"H1\",\"location\":\"DuplicationDetectionService.findOriginalPiece\",\"message\":\"Entered duplicate check\",\"data\":{\"pieceId\":"
                    + (piece != null ? piece.getId() : null)
                    + ",\"isForced\":"
                    + (piece != null ? piece.getIsForced() : null)
                    + ",\"status\":\""
                    + (piece != null && piece.getStatus() != null ? piece.getStatus().name() : "null")
                    + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (IOException ignored) {}
        // #endregion

        if (piece == null || piece.getFilename() == null) {
            return Optional.empty();
        }

        Optional<Piece> byHash = findOriginalByFileHash(piece);
        if (byHash.isPresent()) {
            // #region agent log
            try (FileWriter fw = new FileWriter("/Users/hamzaboulahia/perso/neurocompte.com-backend/.cursor/debug-f12bb6.log", true)) {
                fw.write("{\"sessionId\":\"f12bb6\",\"runId\":\"forced-check-1\",\"hypothesisId\":\"H2\",\"location\":\"DuplicationDetectionService.findOriginalPiece\",\"message\":\"Duplicate matched by hash\",\"data\":{\"pieceId\":"
                        + piece.getId() + ",\"originalId\":" + byHash.get().getId() + ",\"isForced\":" + piece.getIsForced()
                        + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            } catch (IOException ignored) {}
            // #endregion
            return byHash;
        }

        Optional<Piece> byName = findOriginalByOriginalFileName(piece);
        if (byName.isPresent()) {
            // #region agent log
            try (FileWriter fw = new FileWriter("/Users/hamzaboulahia/perso/neurocompte.com-backend/.cursor/debug-f12bb6.log", true)) {
                fw.write("{\"sessionId\":\"f12bb6\",\"runId\":\"forced-check-1\",\"hypothesisId\":\"H3\",\"location\":\"DuplicationDetectionService.findOriginalPiece\",\"message\":\"Duplicate matched by name\",\"data\":{\"pieceId\":"
                        + piece.getId() + ",\"originalId\":" + byName.get().getId() + ",\"isForced\":" + piece.getIsForced()
                        + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            } catch (IOException ignored) {}
            // #endregion
            return byName;
        }

        return Optional.empty();
    }

    /**
     * Mark a piece as duplicate: set flag + reference + status, then save.
     */
    public void markAsDuplicate(Piece duplicatePiece, Piece originalPiece) {
        duplicatePiece.setIsDuplicate(true);
        duplicatePiece.setOriginalPiece(originalPiece);
        duplicatePiece.setStatus(PieceStatus.DUPLICATE);
        pieceRepository.save(duplicatePiece);

        log.info("🔗 Batch: marked piece {} as duplicate of piece {} ({})",
                duplicatePiece.getId(), originalPiece.getId(),
                originalPiece.getOriginalFileName() != null ? originalPiece.getOriginalFileName() : originalPiece.getFilename());
    }

    private Optional<Piece> findOriginalByFileHash(Piece piece) {
        try {
            piece.getClass().getDeclaredField("fileHash");

            if (piece.getFileHash() != null && !piece.getFileHash().isBlank() && piece.getDossier() != null) {
                Long dossierId = piece.getDossier().getId();
                List<Piece> sameHashPieces = pieceRepository.findByDossierIdAndFileHash(dossierId, piece.getFileHash().trim());
                Optional<Piece> canonical = sameHashPieces.stream()
                        .filter(p -> p.getId() != null)
                        .min(BY_UPLOAD_THEN_ID);
                if (canonical.isPresent() && piece.getId() != null && !canonical.get().getId().equals(piece.getId())) {
                    log.info("🚫 Detected duplicate by file hash in dossier {}: piece {} → original {}",
                            dossierId, piece.getId(), canonical.get().getId());
                    return canonical;
                }
            }
        } catch (NoSuchFieldException e) {
            log.debug("FileHash field not available, skipping file hash check");
        }
        return Optional.empty();
    }

    private Optional<Piece> findOriginalByOriginalFileName(Piece piece) {
        String name = piece.getOriginalFileName();
        if (name == null || name.isBlank() || piece.getDossier() == null) {
            return Optional.empty();
        }
        List<Piece> matches = pieceRepository.findAllByDossierIdAndOriginalFileNameIgnoreCase(
                piece.getDossier().getId(), name.trim());
        Optional<Piece> canonical = matches.stream()
                .filter(p -> p.getId() != null)
                .min(BY_UPLOAD_THEN_ID);
        if (canonical.isPresent() && piece.getId() != null && !canonical.get().getId().equals(piece.getId())) {
            log.info("🚫 Detected duplicate by original filename in dossier {}: piece {} → original {}",
                    piece.getDossier().getId(), piece.getId(), canonical.get().getId());
            return canonical;
        }
        return Optional.empty();
    }
}