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

        Optional<Long> contractIdOpt = resolveContractIdForConsumption(cabinetId, uploadDay, loaded.getId());
        if (contractIdOpt.isEmpty()) {
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
     * Prefer a contract whose period contains the upload day; then try "today" (processing day);
     * finally the cabinet's current active contract so consumption still applies when dates are misaligned.
     */
    private Optional<Long> resolveContractIdForConsumption(Long cabinetId, LocalDate uploadDay, Long pieceId) {
        List<CabinetContract> byUpload = contractRepository.findByCabinetIdAndDateInContract(cabinetId, uploadDay);
        if (byUpload != null && !byUpload.isEmpty()) {
            return Optional.of(byUpload.get(0).getId());
        }

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (!today.equals(uploadDay)) {
            List<CabinetContract> byToday = contractRepository.findByCabinetIdAndDateInContract(cabinetId, today);
            if (byToday != null && !byToday.isEmpty()) {
                log.info("📊 No contract for upload day {} — using contract covering today {} (piece {})", uploadDay, today, pieceId);
                return Optional.of(byToday.get(0).getId());
            }
        }

        Optional<CabinetContract> active = contractRepository.findFirstByCabinetIdAndActiveTrueOrderByStartDateDesc(cabinetId);
        if (active.isPresent()) {
            log.warn("📊 No contract for upload day {} nor today {} — attributing piece {} to active contract {}",
                    uploadDay, today, pieceId, active.get().getId());
            return Optional.of(active.get().getId());
        }

        log.warn("No cabinet contract for cabinet {} covers upload/today and no active contract (piece {})", cabinetId, pieceId);
        return Optional.empty();
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
