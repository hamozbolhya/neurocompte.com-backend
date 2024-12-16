package com.pacioli.core.services;

import com.pacioli.core.DTO.EcritureDTO;
import com.pacioli.core.DTO.EcritureExportDTO;
import com.pacioli.core.models.Ecriture;

import java.time.LocalDate;
import java.util.List;

public interface EcritureService {
    // Fetch all ecritures
    List<Ecriture> getAllEcritures();

    // Fetch ecritures by Piece ID
    List<Ecriture> getEcrituresByPieceId(Long pieceId);

    List<Ecriture> getEcrituresByExercise(Long exerciseId);
    List<EcritureDTO> getEcrituresByExerciseAndCabinet(Long exerciseId, Long cabinetId);

    Ecriture updateEcriture(Ecriture ecriture);
    Ecriture getEcritureById(Long id);
    EcritureDTO getEcritureDetails(Long ecritureId);
    void deleteEcritures(List<Long> ecritureIds);

    void updateCompte(String account, List<Long> ecritureIds);

    Ecriture updateEcriture(Long ecritureId, Ecriture ecritureRequest);

    List<EcritureExportDTO> exportEcritures(Long dossierId, Long exerciseId, Long journalId, LocalDate startDate, LocalDate endDate);
}
