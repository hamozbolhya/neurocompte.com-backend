package com.pacioli.core.services.serviceImp;

import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.CabinetContract;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.DossierRepository;
import com.pacioli.core.repositories.PieceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Enforces cabinet contract quotas at upload and before marking a piece PROCESSED.
 */
@Slf4j
@Service
public class CabinetContractQuotaService {

    private static final List<PieceStatus> PIPELINE_STATUSES = List.of(PieceStatus.UPLOADED, PieceStatus.PROCESSING);

    private final DossierRepository dossierRepository;
    private final PieceRepository pieceRepository;
    private final CabinetContractConsumptionService contractConsumptionService;

    public CabinetContractQuotaService(DossierRepository dossierRepository,
                                       PieceRepository pieceRepository,
                                       CabinetContractConsumptionService contractConsumptionService) {
        this.dossierRepository = dossierRepository;
        this.pieceRepository = pieceRepository;
        this.contractConsumptionService = contractConsumptionService;
    }

    /**
     * Smallest calendar range that contains both the contract period and the anchor day (e.g. today).
     * Fixes pipeline under-count when "active" contract dates are stale but uploads happen now.
     */
    static LocalDate[] countingWindowInclusive(CabinetContract contract, LocalDate anchor) {
        LocalDate s = contract.getStartDate();
        LocalDate e = contract.getEndDate();
        LocalDate start = s.isAfter(anchor) ? anchor : s;
        LocalDate end = e.isBefore(anchor) ? anchor : e;
        return new LocalDate[]{start, end};
    }

    private static Date[] toExclusiveEndBounds(LocalDate startInclusive, LocalDate endInclusive, ZoneId zone) {
        Date from = Date.from(startInclusive.atStartOfDay(zone).toInstant());
        Date endExclusive = Date.from(endInclusive.plusDays(1).atStartOfDay(zone).toInstant());
        return new Date[]{from, endExclusive};
    }

    /**
     * @param pdfPageCount page count for this file (≥1); for bank PDFs, reject the whole upload if it exceeds remaining pages
     */
    public void assertUploadWithinQuota(@NonNull Long dossierId, String pieceType, int pdfPageCount) {
        Dossier dossier = dossierRepository.findById(dossierId).orElse(null);
        if (dossier == null || dossier.getCabinet() == null || dossier.getCabinet().getId() == null) {
            return;
        }
        Long cabinetId = dossier.getCabinet().getId();
        LocalDate anchor = LocalDate.now(ZoneId.systemDefault());
        Optional<CabinetContract> contractOpt = contractConsumptionService.findApplicableContract(cabinetId, anchor);
        if (contractOpt.isEmpty()) {
            return;
        }
        CabinetContract contract = contractOpt.get();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate[] win = countingWindowInclusive(contract, anchor);
        Date[] bounds = toExclusiveEndBounds(win[0], win[1], zone);

        String bankType = CabinetContractConsumptionService.BANK_PIECE_TYPE;
        if (isBankPieceType(pieceType)) {
            int pagesThisFile = Math.max(1, pdfPageCount);
            long quota = contract.getBankStatementPageQuota() == null ? 0L : contract.getBankStatementPageQuota();
            long consumed = nullToZero(contract.getBankStatementPageConsumption());
            Long pipelineBox = pieceRepository.sumBankPipelinePagesForCabinetInPeriod(
                    cabinetId, bankType, PIPELINE_STATUSES, bounds[0], bounds[1]);
            long pipeline = pipelineBox == null ? 0L : pipelineBox;
            long remaining = quota - consumed - pipeline;
            if (pagesThisFile > remaining) {
                log.warn("Bank upload rejected: {} pages in file, remaining {} (quota {}, consumed {}, pipeline {}) cabinet {}",
                        pagesThisFile, remaining, quota, consumed, pipeline, cabinetId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        String.format(
                                "Ce relevé bancaire fait %d page(s) alors qu’il ne reste que %d page(s) autorisée(s) sur le contrat pour cette période. Téléversement refusé.",
                                pagesThisFile, Math.max(0, remaining)));
            }
        } else {
            long quota = contract.getNormalStatementPieceQuota() == null ? 0L : contract.getNormalStatementPieceQuota();
            long consumed = nullToZero(contract.getNormalStatementPieceConsumption());
            long pipeline = pieceRepository.countNormalPipelineForCabinetInPeriod(
                    cabinetId, bankType, PIPELINE_STATUSES, bounds[0], bounds[1]);
            long remaining = quota - consumed - pipeline;
            if (remaining <= 0) {
                log.warn("Normal piece upload rejected: no remaining slots (quota {}, consumed {}, pipeline {}) cabinet {}",
                        quota, consumed, pipeline, cabinetId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        String.format(
                                "Quota de pièces (hors relevés bancaires) atteint pour cette période de contrat (%d / %d). Téléversement refusé.",
                                consumed + pipeline, quota));
            }
        }
    }

    /**
     * Ensures consumed + pipeline (including this piece while PROCESSING) does not exceed quota before completing AI processing.
     */
    public void assertMayCompleteProcessing(@NonNull Piece piece) {
        Piece loaded = pieceRepository.findByIdWithDossierAndCabinet(piece.getId()).orElse(piece);
        Dossier dossier = loaded.getDossier();
        if (dossier == null || dossier.getCabinet() == null || dossier.getCabinet().getId() == null) {
            return;
        }
        Long cabinetId = dossier.getCabinet().getId();
        LocalDate anchor = uploadDateToLocalDate(piece.getUploadDate());
        Optional<CabinetContract> contractOpt = contractConsumptionService.findApplicableContract(cabinetId, anchor);
        if (contractOpt.isEmpty()) {
            return;
        }
        CabinetContract contract = contractOpt.get();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate[] win = countingWindowInclusive(contract, anchor);
        Date[] bounds = toExclusiveEndBounds(win[0], win[1], zone);

        String bankType = CabinetContractConsumptionService.BANK_PIECE_TYPE;
        if (isBankPieceType(piece.getType())) {
            long quota = contract.getBankStatementPageQuota() == null ? 0L : contract.getBankStatementPageQuota();
            long consumed = nullToZero(contract.getBankStatementPageConsumption());
            Long pipelineBox = pieceRepository.sumBankPipelinePagesForCabinetInPeriod(
                    cabinetId, bankType, PIPELINE_STATUSES, bounds[0], bounds[1]);
            long pipeline = pipelineBox == null ? 0L : pipelineBox;
            if (consumed + pipeline > quota) {
                log.warn("Bank piece {} processing rejected: consumed {} + pipeline {} > quota {}", piece.getId(), consumed, pipeline, quota);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Quota pages relevés bancaires dépassé : impossible de finaliser le traitement de cette pièce.");
            }
        } else {
            long quota = contract.getNormalStatementPieceQuota() == null ? 0L : contract.getNormalStatementPieceQuota();
            long consumed = nullToZero(contract.getNormalStatementPieceConsumption());
            long pipeline = pieceRepository.countNormalPipelineForCabinetInPeriod(
                    cabinetId, bankType, PIPELINE_STATUSES, bounds[0], bounds[1]);
            if (consumed + pipeline > quota) {
                log.warn("Normal piece {} processing rejected: consumed {} + pipeline {} > quota {}", piece.getId(), consumed, pipeline, quota);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Quota de pièces dépassé : impossible de finaliser le traitement de cette pièce.");
            }
        }
    }

    private static LocalDate uploadDateToLocalDate(Date uploadDate) {
        if (uploadDate == null) {
            return LocalDate.now(ZoneId.systemDefault());
        }
        return uploadDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static boolean isBankPieceType(String pieceType) {
        if (pieceType == null) {
            return false;
        }
        return CabinetContractConsumptionService.BANK_PIECE_TYPE.equalsIgnoreCase(pieceType.trim());
    }

    private static long nullToZero(Long v) {
        return v == null ? 0L : v;
    }
}
