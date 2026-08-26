package com.pacioli.core.services;

import com.pacioli.core.DTO.DossierDTO;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Exercise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.UUID;

public interface DossierService {


    Dossier createDossier(@NonNull Dossier dossier, List<Exercise> exercicesData);

    Dossier getDossierById(@NonNull Long dossierId);

    DossierDTO getTheDossierById(@NonNull Long dossierId);

    Dossier updateExercises(@NonNull Long dossierId, List<Exercise> updatedExercises);

    void deleteExercises(@NonNull Long dossierId, @NonNull List<Long> exerciseIds);

    Page<DossierDTO> getDossiersByCabinetId(@NonNull Long cabinetId, Pageable pageable);

    DossierDTO updateDossier(@NonNull Long id, @NonNull Dossier dossierDetails);

    void deleteDossier(@NonNull Long dossierId);

    DossierDTO updateActivity(@NonNull Long dossierId, String activity);



    // ✅ ADD NEW SECURE METHODS - DON'T CHANGE EXISTING ONES
    DossierDTO getDossierForUser(@NonNull Long dossierId, @NonNull UUID userId);
    Page<Dossier> getDossiersForUser(@NonNull UUID userId, Pageable pageable);
    boolean userHasAccessToDossier(@NonNull UUID userId, @NonNull Long dossierId);
    boolean userHasAccessToCabinet(@NonNull UUID userId, @NonNull Long cabinetId);
    Dossier createDossierSecure(@NonNull Dossier dossier, List<Exercise> exercicesData, @NonNull UUID userId);
    DossierDTO updateDossierSecure(@NonNull Long id, @NonNull Dossier dossierDetails, @NonNull UUID userId);
    void deleteDossierSecure(@NonNull Long dossierId, @NonNull UUID userId);
    DossierDTO getDossierForPacioli(@NonNull Long dossierId);
    int updateAllCompaniesInAi();
}
