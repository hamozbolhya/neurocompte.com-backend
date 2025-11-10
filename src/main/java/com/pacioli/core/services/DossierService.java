package com.pacioli.core.services;

import com.pacioli.core.DTO.DossierDTO;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Exercise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DossierService {


    Dossier createDossier(Dossier dossier, List<Exercise> exercicesData);

    Dossier getDossierById(Long dossierId);

    DossierDTO getTheDossierById(Long dossierId);

    Dossier updateExercises(Long dossierId, List<Exercise> updatedExercises);

    void deleteExercises(Long dossierId, List<Long> exerciseIds);

    Page<DossierDTO> getDossiersByCabinetId(Long cabinetId, Pageable pageable);

    DossierDTO updateDossier(Long id, Dossier dossierDetails);

    void deleteDossier(Long dossierId);

    DossierDTO updateActivity(Long dossierId, String activity);



    // ✅ ADD NEW SECURE METHODS - DON'T CHANGE EXISTING ONES
    DossierDTO getDossierForUser(Long dossierId, UUID userId);
    Page<Dossier> getDossiersForUser(UUID userId, Pageable pageable);
    boolean userHasAccessToDossier(UUID userId, Long dossierId);
    boolean userHasAccessToCabinet(UUID userId, Long cabinetId);
    Dossier createDossierSecure(Dossier dossier, List<Exercise> exercicesData, UUID userId);
    DossierDTO updateDossierSecure(Long id, Dossier dossierDetails, UUID userId);
    void deleteDossierSecure(Long dossierId, UUID userId);
}
