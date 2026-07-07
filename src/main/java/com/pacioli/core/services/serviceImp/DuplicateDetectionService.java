package com.pacioli.core.services.serviceImp;

import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Ecriture;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.PieceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class DuplicateDetectionService {

    private static final String BANK_PIECE_TYPE = "Relevés bancaires";

    @Autowired
    private PieceRepository pieceRepository;

    private static boolean isBankStatement(Piece piece) {
        return piece != null && piece.getType() != null
                && BANK_PIECE_TYPE.equalsIgnoreCase(piece.getType().trim());
    }

    private static final Comparator<Piece> BY_UPLOAD_THEN_ID = Comparator
            .comparing(Piece::getUploadDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Piece::getId);

    /** Oldest piece in {@code candidates} (stable tie-break on id). */
    private static Optional<Piece> oldestAmong(List<Piece> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(p -> p.getId() != null)
                .min(BY_UPLOAD_THEN_ID);
    }

    /**
     * True duplicate only if {@code self} is not the canonical original: oldest in the whole group
     * (same hash / same name / same similarity cluster). Using "oldest among <i>other</i> matches" wrongly
     * marks the first upload as duplicate of the second when two+ files share a hash.
     */
    private static Optional<Piece> duplicateOfCanonicalOriginal(List<Piece> group, Piece self) {
        if (self == null || self.getId() == null) {
            return Optional.empty();
        }
        Optional<Piece> canonical = oldestAmong(group);
        if (canonical.isEmpty() || canonical.get().getId().equals(self.getId())) {
            return Optional.empty();
        }
        return canonical;
    }

    /** Label for logs / audit when originalFileName is missing (multipart vs JSON upload). */
    private static String originalDisplayLabel(Piece p) {
        if (p == null) {
            return "?";
        }
        String n = p.getOriginalFileName();
        if (n != null && !n.isBlank()) {
            return n;
        }
        return p.getFilename() != null ? p.getFilename() : "?";
    }

    /**
     * Same file bytes (MD5) already stored in this dossier on another piece.
     */
    private Optional<Piece> checkDuplicateByFileHash(Piece piece) {
        if (piece.getFileHash() == null || piece.getFileHash().isBlank()) {
            return Optional.empty();
        }
        if (piece.getDossier() == null || piece.getDossier().getId() == null) {
            return Optional.empty();
        }
        List<Piece> matches = pieceRepository.findByDossierIdAndFileHash(
                piece.getDossier().getId(), piece.getFileHash().trim());
        Optional<Piece> original = duplicateOfCanonicalOriginal(matches, piece);
        if (original.isPresent()) {
            log.warn("⚠️ Duplicate by MD5 in dossier {}: piece {} matches piece {} (hash prefix {}, original id={}, original label={})",
                    piece.getDossier().getId(), piece.getId(), original.get().getId(),
                    piece.getFileHash().length() > 8 ? piece.getFileHash().substring(0, 8) : piece.getFileHash(),
                    original.get().getId(), originalDisplayLabel(original.get()));
        }
        return original;
    }

    /**
     * Same original filename in dossier (case-insensitive), excluding current piece.
     * Replaces fragile {@code matches.get(0)} which could be the same row or arbitrary order.
     */
    private Optional<Piece> checkOriginalFileNameDuplicateInDossier(Piece piece) {
        String name = piece.getOriginalFileName();
        if (name == null || name.trim().isEmpty() || piece.getDossier() == null || piece.getDossier().getId() == null) {
            return Optional.empty();
        }
        List<Piece> matches = pieceRepository.findAllByDossierIdAndOriginalFileNameIgnoreCase(
                piece.getDossier().getId(), name.trim());
        Optional<Piece> original = duplicateOfCanonicalOriginal(matches, piece);
        if (original.isPresent()) {
            log.warn("⚠️ Duplicate original filename in dossier {}: '{}' → piece {} vs {} (original id={}, original label={})",
                    piece.getDossier().getId(), name.trim(), piece.getId(), original.get().getId(),
                    original.get().getId(), originalDisplayLabel(original.get()));
        }
        return original;
    }

    /**
     * Non-bank: exact name (case-insensitive) then similar-filename heuristics.
     */
    public Optional<Piece> checkTechnicalDuplicate(Piece piece) {
        Long dossierId = piece.getDossier().getId();
        String originalFileName = piece.getOriginalFileName();
        log.debug("🔍 Technical duplicate check: dossier={}, pieceId={}, filename={}", dossierId, piece.getId(), originalFileName);

        Optional<Piece> byName = checkOriginalFileNameDuplicateInDossier(piece);
        if (byName.isPresent()) {
            return byName;
        }

        Optional<Piece> similar = checkSimilarFilenames(dossierId, originalFileName, piece);
        if (similar.isPresent()) {
            log.warn("⚠️ Similar filename pattern: piece {} in dossier {}", piece.getId(), dossierId);
            return similar;
        }

        return Optional.empty();
    }

    /**
     * Similar filename patterns (renamed copies); excludes {@code excludePieceId}.
     */
    private Optional<Piece> checkSimilarFilenames(Long dossierId, String originalFileName, Piece self) {
        if (originalFileName == null || originalFileName.trim().isEmpty() || self == null || self.getId() == null) {
            return Optional.empty();
        }

        String baseFileName = extractBaseFileName(originalFileName);

        if (baseFileName.length() < 3) {
            return Optional.empty();
        }

        log.debug("🔍 Similar filenames with base: {}", baseFileName);

        List<Piece> similarPieces = pieceRepository.findSimilarFileNames(dossierId, originalFileName, baseFileName);
        return duplicateOfCanonicalOriginal(similarPieces, self);
    }

    /**
     * Extract base filename for similarity comparison
     */
    private String extractBaseFileName(String filename) {
        if (filename == null) return "";

        // Remove file extension
        String nameWithoutExt = filename.contains(".") ?
                filename.substring(0, filename.lastIndexOf('.')) : filename;

        // Remove common suffixes that indicate copies/versions
        String[] suffixesToRemove = {
                "_copy", "_Copy", "_COPY",
                "_duplicate", "_Duplicate", "_DUPLICATE",
                "_new", "_New", "_NEW",
                "_v1", "_v2", "_v3", "_V1", "_V2", "_V3",
                "_version1", "_version2", "_version3",
                "_final", "_Final", "_FINAL",
                "(1)", "(2)", "(3)", "(4)", "(5)",
                " - Copy", " - copy", " - COPY"
        };

        String result = nameWithoutExt;
        for (String suffix : suffixesToRemove) {
            if (result.endsWith(suffix)) {
                result = result.substring(0, result.length() - suffix.length());
                break; // Remove only one suffix
            }
        }

        return result.trim();
    }

    public Optional<Piece> checkFunctionalDuplicate(Piece piece) {

        if (Boolean.TRUE.equals(piece.getIsForced())) {
            log.info("⏭ Pièce {} ignorée dans checkFunctionalDuplicate (isForced=true)", piece.getId());
            return Optional.empty();
        }

        // Check 1: Invoice-based duplicates (if FactureData is available)
        if (piece.getFactureData() != null) {
            Optional<Piece> invoiceDuplicate = checkInvoiceDuplicate(piece);
            if (invoiceDuplicate.isPresent()) {
                return invoiceDuplicate;
            }
        }

        // Check 2: Ecriture-based duplicates (if Ecritures are available)
        if (piece.getEcritures() != null && !piece.getEcritures().isEmpty()) {
            Optional<Piece> ecritudeDuplicate = checkEcritureDuplicate(piece);
            if (ecritudeDuplicate.isPresent()) {
                return ecritudeDuplicate;
            }
        }

        log.info("✅ No functional duplicate found for piece {}", piece.getId());
        return Optional.empty();
    }

    /**
     * Check for duplicates based on invoice data (FactureData)
     */
    private Optional<Piece> checkInvoiceDuplicate(Piece piece) {
        Date invoiceDate = piece.getFactureData().getInvoiceDate();
        Double totalTTC = piece.getFactureData().getTotalTTC();
        Long dossierId = piece.getDossier().getId();

        log.info("🔍 Checking for invoice-based duplicate: dossier={}, invoiceDate={}, totalTTC={}",
                dossierId, invoiceDate, totalTTC);

        if (invoiceDate == null || totalTTC == null) {
            log.debug("Missing invoice date or total TTC for piece {}, skipping invoice duplicate check", piece.getId());
            return Optional.empty();
        }

        List<Piece> duplicates = pieceRepository.findFunctionalDuplicates(dossierId, invoiceDate, totalTTC);

        // Remove current piece from results if it's included
        duplicates.removeIf(p -> p.getId().equals(piece.getId()));

        if (!duplicates.isEmpty()) {
            Piece originalPiece = duplicates.get(0); // Take the first (oldest) as original
            log.warn("⚠️ Invoice-based duplicate detected for piece {}: matches piece {} (invoice date: {}, total: {}, original id={}, original label={})",
                    piece.getId(), originalPiece.getId(), invoiceDate, totalTTC,
                    originalPiece.getId(), originalDisplayLabel(originalPiece));
            return Optional.of(originalPiece);
        }

        return Optional.empty();
    }

    /**
     * Check for duplicates based on ecriture data
     */
    private Optional<Piece> checkEcritureDuplicate(Piece piece) {
        Long dossierId = piece.getDossier().getId();

        log.info("🔍 Checking for ecriture-based duplicate for piece {}", piece.getId());

        for (Ecriture ecriture : piece.getEcritures()) {
            LocalDate entryDate = ecriture.getEntryDate();
            Double maxAmount = calculateMaxAmountFromEcriture(ecriture);

            if (entryDate == null || maxAmount == null || maxAmount == 0.0) {
                continue;
            }

//            log.debug("Checking ecriture: entryDate={}, maxAmount={}", entryDate, maxAmount);

            // Check against existing ecritures in the dossier
            List<Piece> duplicates = pieceRepository.findByEcritureData(dossierId, entryDate, maxAmount);

            // Remove current piece from results if it's included
            duplicates.removeIf(p -> p.getId().equals(piece.getId()));

            if (!duplicates.isEmpty()) {
                Piece originalPiece = duplicates.get(0); // Take the first (oldest) as original
                log.warn("⚠️ Ecriture-based duplicate detected for piece {}: matches piece {} (entry date: {}, amount: {}, original id={}, original label={})",
                        piece.getId(), originalPiece.getId(), entryDate, maxAmount,
                        originalPiece.getId(), originalDisplayLabel(originalPiece));
                return Optional.of(originalPiece);
            }
        }

        return Optional.empty();
    }

    public Optional<Piece> performComprehensiveDuplicateCheck(Piece piece) {
        if (Boolean.TRUE.equals(piece.getIsForced())) {
            log.info("⏭ Pièce {} ignorée dans performComprehensiveDuplicateCheck (isForced=true)", piece.getId());
            return Optional.empty();
        }

        log.info("🔍 Performing comprehensive duplicate check for piece {} (fileHash={}, originalFileName={})",
                piece.getId(), piece.getFileHash(), piece.getOriginalFileName());

        boolean bank = isBankStatement(piece);

        // Check 1: MD5 content hash (same dossier) — strongest for re-uploaded identical files
        Optional<Piece> hashDuplicate = checkDuplicateByFileHash(piece);
        if (hashDuplicate.isPresent()) {
            Piece orig = hashDuplicate.get();
            log.info("🚫 Duplicate trace: piece {} is duplicate of piece {} (reason=MD5, original={})",
                    piece.getId(), orig.getId(), originalDisplayLabel(orig));
            return hashDuplicate;
        }

        // Check 2: Original filename (case-insensitive); bank skips fuzzy "similar name" only
        Optional<Piece> nameDuplicate = bank
                ? checkOriginalFileNameDuplicateInDossier(piece)
                : checkTechnicalDuplicate(piece);
        if (nameDuplicate.isPresent()) {
            Piece orig = nameDuplicate.get();
            log.info("🚫 Duplicate trace: piece {} is duplicate of piece {} (reason=filename/technical, original={})",
                    piece.getId(), orig.getId(), originalDisplayLabel(orig));
            return nameDuplicate;
        }

        // Bank statements: skip functional / ecriture duplicate logic. Different PDFs often share the same
        // transaction date and line amounts (same bank, recurring fees, round amounts) → false positives.
        if (bank) {
            log.debug("Bank statement — skipping ecriture/invoice duplicate heuristics for piece {}", piece.getId());
            return Optional.empty();
        }

        // Check 2: Functional duplicates (invoice + ecriture based)
        Optional<Piece> functionalDuplicate = checkFunctionalDuplicate(piece);
        if (functionalDuplicate.isPresent()) {
            Piece orig = functionalDuplicate.get();
            log.info("🚫 Duplicate trace: piece {} is duplicate of piece {} (reason=functional, original={})",
                    piece.getId(), orig.getId(), originalDisplayLabel(orig));
            return functionalDuplicate;
        }

        // Check 3: Enhanced ecriture-based check with tolerance for rounding differences
        Optional<Piece> toleranceDuplicate = checkEcritureDuplicateWithTolerance(piece);
        if (toleranceDuplicate.isPresent()) {
            Piece orig = toleranceDuplicate.get();
            log.info("🚫 Duplicate trace: piece {} is duplicate of piece {} (reason=tolerance/ecriture, original={})",
                    piece.getId(), orig.getId(), originalDisplayLabel(orig));
            return toleranceDuplicate;
        }

        log.info("✅ Comprehensive duplicate check passed for piece {}", piece.getId());
        return Optional.empty();
    }

    private Optional<Piece> checkEcritureDuplicateWithTolerance(Piece piece) {
        if (Boolean.TRUE.equals(piece.getIsForced())) {
            log.info("⏭ Pièce {} ignorée dans checkEcritureDuplicateWithTolerance (isForced=true)", piece.getId());
            return Optional.empty();
        }

        if (piece.getEcritures() == null || piece.getEcritures().isEmpty()) {
            return Optional.empty();
        }

        Long dossierId = piece.getDossier().getId();
        double tolerance = 0.01; // 1 cent tolerance

        log.debug("🔍 Checking for ecriture duplicates with tolerance for piece {}", piece.getId());

        for (Ecriture ecriture : piece.getEcritures()) {
            LocalDate entryDate = ecriture.getEntryDate();
            Double maxAmount = calculateMaxAmountFromEcriture(ecriture);

            if (entryDate == null || maxAmount == null || maxAmount == 0.0) {
                continue;
            }

            Double minAmount = maxAmount - tolerance;
            Double maxAmountWithTolerance = maxAmount + tolerance;

//            log.debug("Checking ecriture with tolerance: entryDate={}, amount={}±{}", entryDate, maxAmount, tolerance);

            List<Piece> duplicates = pieceRepository.findByEcritureDataWithTolerance(
                    dossierId, entryDate, minAmount, maxAmountWithTolerance);

            // Remove current piece from results if it's included
            duplicates.removeIf(p -> p.getId().equals(piece.getId()));

            if (!duplicates.isEmpty()) {
                Piece originalPiece = duplicates.get(0);
                log.warn("⚠️ Tolerance-based duplicate detected for piece {}: matches piece {} (entry date: {}, amount: {}±{}, original id={}, original label={})",
                        piece.getId(), originalPiece.getId(), entryDate, maxAmount, tolerance,
                        originalPiece.getId(), originalDisplayLabel(originalPiece));
                return Optional.of(originalPiece);
            }
        }

        return Optional.empty();
    }

    private Double calculateMaxAmountFromEcriture(Ecriture ecriture) {
        if (ecriture.getLines() == null || ecriture.getLines().isEmpty()) {
            return 0.0;
        }

        return ecriture.getLines().stream()
                .mapToDouble(line -> Math.max(
                        line.getDebit() != null ? line.getDebit() : 0.0,
                        line.getCredit() != null ? line.getCredit() : 0.0
                ))
                .max()
                .orElse(0.0);
    }

    public void markAsDuplicate(Piece duplicatePiece, Piece originalPiece) {
        log.info("🔗 Marking piece {} as duplicate of piece {}", duplicatePiece.getId(), originalPiece.getId());

        duplicatePiece.setIsDuplicate(true);
        duplicatePiece.setOriginalPiece(originalPiece);
        duplicatePiece.setStatus(PieceStatus.DUPLICATE);

        // Save the changes
        pieceRepository.save(duplicatePiece);

        log.info("✅ Successfully marked piece {} as duplicate", duplicatePiece.getId());
    }

    public boolean isDuplicate(Piece piece) {
        return piece.getIsDuplicate() != null && piece.getIsDuplicate();
    }
}
