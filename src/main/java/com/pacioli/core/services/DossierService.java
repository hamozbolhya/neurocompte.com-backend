package com.pacioli.core.services;

import com.pacioli.core.DTO.DossierDTO;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Exercise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DossierService {

    Dossier createDossier(Dossier dossier, List<Exercise> exercicesData);
    Dossier getDossierById(Long dossierId);

    DossierDTO getTheDossierById(Long dossierId);

    Page<Dossier> getDossiers(Pageable pageable);

    Dossier updateExercises(Long dossierId, List<Exercise> updatedExercises);
    void deleteExercises(Long dossierId, List<Long> exerciseIds);

    Page<DossierDTO> getDossiersByCabinetId(Long cabinetId, Pageable pageable);

    DossierDTO updateDossier(Long id, Dossier dossierDetails);
}
