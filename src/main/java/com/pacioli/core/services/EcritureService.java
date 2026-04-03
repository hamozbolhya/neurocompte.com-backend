package com.pacioli.core.services;

import com.pacioli.core.DTO.EcritureDTO;
import com.pacioli.core.DTO.EcritureExportDTO;
import com.pacioli.core.models.Ecriture;
import org.springframework.data.domain.Page;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.List;

public interface EcritureService {
    // Fetch ecritures by Piece ID
    List<Ecriture> getEcrituresByPieceId(@NonNull Long pieceId);

    Page<EcritureDTO> getEcrituresByExerciseAndCabinet(Long exerciseId, Long cabinetId, int page, int size);

    Ecriture updateEcriture(@NonNull Ecriture ecriture);
    Ecriture getEcritureById(@NonNull Long id);
    EcritureDTO getEcritureDetails(@NonNull Long ecritureId);
    void deleteEcritures(@NonNull List<Long> ecritureIds);

    void updateCompte(String account, @NonNull List<Long> ecritureIds);

    Ecriture updateEcriture(@NonNull Long ecritureId, @NonNull Ecriture ecritureRequest);

    List<EcritureExportDTO> exportEcritures(@NonNull Long dossierId, Long exerciseId, Long journalId, LocalDate startDate, LocalDate endDate);
}
