package com.pacioli.core.services.serviceImp;

import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Ecriture;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.PieceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class DuplicateDetectionService {

    @Autowired
    private PieceRepository pieceRepository;

    /**
     * Check for technical duplicates based on original filename and similar patterns
     * This should be called during piece upload (savePiece method)
     *
     * @param dossierId        The dossier ID
     * @param originalFileName The original filename of the uploaded file
     * @return Optional containing the original piece if duplicate found
     */
    public Optional<Piece> checkTechnicalDuplicate(Long dossierId, String originalFileName) {
        log.info("🔍 Checking for technical duplicate: dossier={}, filename={}", dossierId, originalFileName);

        // ✅ Check 1: Exact filename match using List
        List<Piece> matches = pieceRepository.findAllByDossierIdAndOriginalFileName(dossierId, originalFileName);
        if (!matches.isEmpty()) {
            Piece firstMatch = matches.get(0); // Use the first found
            log.warn("⚠️ Exact filename duplicate detected: {} match(es) found for file: {} in dossier: {}",
                    matches.size(), originalFileName, dossierId);
            return Optional.of(firstMatch);
        }

        // ✅ Check 2: Similar filename patterns (for renamed files)
        Optional<Piece> similarMatch = checkSimilarFilenames(dossierId, originalFileName);
        if (similarMatch.isPresent()) {
            log.warn("⚠️ Similar filename pattern detected for file: {} in dossier: {}", originalFileName, dossierId);
            return similarMatch;
        }

        log.info("✅ No technical duplicate found for file: {}", originalFileName);
        return Optional.empty();
    }


    /**
     * Check for similar filename patterns to detect renamed files
     */
    private Optional<Piece> checkSimilarFilenames(Long dossierId, String originalFileName) {
        if (originalFileName == null || originalFileName.trim().isEmpty()) {
            return Optional.empty();
        }

        // Extract base filename without extension and common suffixes
        String baseFileName = extractBaseFileName(originalFileName);

        if (baseFileName.length() < 3) { // Skip very short filenames
            return Optional.empty();
        }

        log.debug("🔍 Checking for similar filenames with base: {}", baseFileName);

        List<Piece> similarPieces = pieceRepository.findSimilarFileNames(dossierId, originalFileName, baseFileName);

        if (!similarPieces.isEmpty()) {
            Piece similarPiece = similarPieces.get(0); // Take the oldest
            log.info("Found similar filename: {} vs {}", originalFileName, similarPiece.getOriginalFileName());
            return Optional.of(similarPiece);
        }

        return Optional.empty();
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

        // Remove trailing numbers and spaces
        result = result.replaceAll("\\s*\\d+\\s*$", "").trim();

        return result;
    }

    public Optional<Piece> checkFunctionalDuplicate(Piece piece) {

        if (Boolean.TRUE.equals(piece.getIsForced())) {
            log.info("⏭ Pièce {} ignorée dans checkFunctionalDuplicate (isForced=true)", piece.getId());
            return Optional.empty();
        }

        Long dossierId = piece.getDossier().getId();

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
            log.warn("⚠️ Invoice-based duplicate detected for piece {}: matches piece {} (invoice date: {}, total: {})",
                    piece.getId(), originalPiece.getId(), invoiceDate, totalTTC);
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
                log.warn("⚠️ Ecriture-based duplicate detected for piece {}: matches piece {} (entry date: {}, amount: {})",
                        piece.getId(), originalPiece.getId(), entryDate, maxAmount);
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

        log.info("🔍 Performing comprehensive duplicate check for piece {}", piece.getId());

        // Check 1: Technical duplicates (filename-based)
        Optional<Piece> technicalDuplicate = checkTechnicalDuplicate(
                piece.getDossier().getId(), piece.getOriginalFileName());
        if (technicalDuplicate.isPresent() && !technicalDuplicate.get().getId().equals(piece.getId())) {
            log.warn("🚫 Technical duplicate found during comprehensive check");
            return technicalDuplicate;
        }

        // Check 2: Functional duplicates (invoice + ecriture based)
        Optional<Piece> functionalDuplicate = checkFunctionalDuplicate(piece);
        if (functionalDuplicate.isPresent()) {
            log.warn("🚫 Functional duplicate found during comprehensive check");
            return functionalDuplicate;
        }

        // Check 3: Enhanced ecriture-based check with tolerance for rounding differences
        Optional<Piece> toleranceDuplicate = checkEcritureDuplicateWithTolerance(piece);
        if (toleranceDuplicate.isPresent()) {
            log.warn("🚫 Tolerance-based duplicate found during comprehensive check");
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
                log.warn("⚠️ Tolerance-based duplicate detected for piece {}: matches piece {} (entry date: {}, amount: {}±{})",
                        piece.getId(), originalPiece.getId(), entryDate, maxAmount, tolerance);
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