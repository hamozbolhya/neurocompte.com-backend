package com.pacioli.core.services;

import com.pacioli.core.DTO.EcritureDTO;
import com.pacioli.core.DTO.EcritureExportDTO;
import com.pacioli.core.models.Ecriture;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface EcritureService {
    // Fetch ecritures by Piece ID
    List<Ecriture> getEcrituresByPieceId(Long pieceId);

    Page<EcritureDTO> getEcrituresByExerciseAndCabinet(Long exerciseId, Long cabinetId, int page, int size);

    Ecriture updateEcriture(Ecriture ecriture);
    Ecriture getEcritureById(Long id);
    EcritureDTO getEcritureDetails(Long ecritureId);
    void deleteEcritures(List<Long> ecritureIds);

    void updateCompte(String account, List<Long> ecritureIds);

    Ecriture updateEcriture(Long ecritureId, Ecriture ecritureRequest);

    List<EcritureExportDTO> exportEcritures(Long dossierId, Long exerciseId, Long journalId, LocalDate startDate, LocalDate endDate);
}
