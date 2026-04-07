package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.CabinetContract;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.CabinetContractRepository;
import com.pacioli.core.repositories.PieceRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class CabinetContractConsumptionService {

    public static final String BANK_PIECE_TYPE = "Relevés bancaires";

    private final CabinetContractRepository contractRepository;
    private final PieceRepository pieceRepository;

    @Value("${file.upload.dir:Files/}")
    private String uploadDir;

    public CabinetContractConsumptionService(CabinetContractRepository contractRepository,
                                            PieceRepository pieceRepository) {
        this.contractRepository = contractRepository;
        this.pieceRepository = pieceRepository;
    }

    /**
     * Call when a piece reaches {@link com.pacioli.core.enums.PieceStatus#PROCESSED}.
     * Attributes normal consumption (+1 per processed non-bank piece) and bank pages (+N for processed bank PDFs).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordProcessedPiece(@NonNull Piece piece) {
        log.info("📊 Recording consumption for piece {}: type={}", piece.getId(), piece.getType());

        Piece loaded = pieceRepository.findByIdWithDossierAndCabinet(piece.getId()).orElse(piece);

        Dossier dossier = loaded.getDossier();
        if (dossier == null || dossier.getCabinet() == null || dossier.getCabinet().getId() == null) {
            log.warn("Skipping contract consumption: piece {} has no dossier/cabinet", piece.getId());
            return;
        }
        Long cabinetId = dossier.getCabinet().getId();
        LocalDate uploadDay = uploadDateToLocalDate(loaded.getUploadDate());
        log.info("📊 Piece upload local date: {}, Cabinet ID: {}", uploadDay, cabinetId);

        Optional<Long> contractIdOpt = findApplicableContract(cabinetId, uploadDay).map(CabinetContract::getId);
        if (contractIdOpt.isEmpty()) {
            log.warn("No cabinet contract for cabinet {} covers upload/today and no active contract (piece {})", cabinetId, loaded.getId());
            return;
        }
        Long contractId = contractIdOpt.get();
        log.info("📊 Using contract ID: {} for piece {}", contractId, loaded.getId());

        if (BANK_PIECE_TYPE.equalsIgnoreCase(loaded.getType())) {
            int pages = resolveBankPdfPageCount(loaded);
            if (pages < 1) {
                pages = 1;
            }
            log.info("📊 Incrementing bank pages by {} for contract {}", pages, contractId);
            int updated = contractRepository.incrementBankPagesConsumed(contractId, pages);
            if (updated == 0) {
                log.warn("incrementBankPagesConsumed had no effect for contract {}", contractId);
            }
        } else {
            log.info("📊 Incrementing normal pieces by 1 for contract {}", contractId);
            int updated = contractRepository.incrementNormalPiecesConsumed(contractId, 1L);
            if (updated == 0) {
                log.warn("incrementNormalPiecesConsumed had no effect for contract {}", contractId);
            }
        }
    }

    /**
     * Same resolution as consumption: period containing {@code referenceDay}, then today, then active contract.
     * Not read-only so it can safely join a read-write transaction when called from quota checks during upload.
     */
    public Optional<CabinetContract> findApplicableContract(Long cabinetId, LocalDate referenceDay) {
        if (cabinetId == null || referenceDay == null) {
            return Optional.empty();
        }
        List<CabinetContract> byRef = contractRepository.findByCabinetIdAndDateInContract(cabinetId, referenceDay);
        if (byRef != null && !byRef.isEmpty()) {
            return Optional.of(byRef.get(0));
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (!today.equals(referenceDay)) {
            List<CabinetContract> byToday = contractRepository.findByCabinetIdAndDateInContract(cabinetId, today);
            if (byToday != null && !byToday.isEmpty()) {
                return Optional.of(byToday.get(0));
            }
        }
        return contractRepository.findFirstByCabinetIdAndActiveTrueOrderByStartDateDesc(cabinetId);
    }

    private static LocalDate uploadDateToLocalDate(Date uploadDate) {
        if (uploadDate == null) {
            return LocalDate.now();
        }
        return uploadDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * Prefer stored {@link Piece#getPageCount()}; for bank PDFs on disk, recount via PDFBox when needed.
     */
    private int resolveBankPdfPageCount(Piece piece) {
        if (piece.getPageCount() != null && piece.getPageCount() > 0) {
            return piece.getPageCount();
        }
        String filename = piece.getFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return 1;
        }
        Path path = Paths.get(uploadDir).resolve(filename).normalize();
        if (!Files.isRegularFile(path)) {
            log.warn("Bank PDF not found for page count: {}", path);
            return 1;
        }
        try (PDDocument document = PDDocument.load(path.toFile())) {
            int n = document.getNumberOfPages();
            if (n > 0) {
                piece.setPageCount(n);
                pieceRepository.save(piece);
            }
            return Math.max(1, n);
        } catch (Exception e) {
            log.warn("Could not read PDF page count for piece {}: {}", piece.getId(), e.getMessage());
            return 1;
        }
    }
}
