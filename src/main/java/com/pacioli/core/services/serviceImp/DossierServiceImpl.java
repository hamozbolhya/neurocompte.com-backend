package com.pacioli.core.services.serviceImp;

import com.pacioli.core.DTO.Company;
import com.pacioli.core.DTO.DossierDTO;
import com.pacioli.core.DTO.PaysDTO;
import com.pacioli.core.Exceptions.CompanyAiException;
import com.pacioli.core.Exceptions.ExerciseDateConflictException;
import com.pacioli.core.models.*;
import com.pacioli.core.repositories.*;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.CompanyAiService;
import com.pacioli.core.services.DossierService;
import com.pacioli.core.services.UserService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class DossierServiceImpl implements DossierService {

    private final DossierRepository dossierRepository;
    private final CompanyAiService companyAiService;
    private final AuditService auditService;
    private final UserService userService;

    @Autowired
    private CabinetRepository cabinetRepository;

    @Autowired
    private ExerciceRepository exerciseRepository;

    @Autowired
    private EcritureRepository ecritureRepository;

    @Autowired
    private JournalRepository journalRepository;

    @Autowired
    private UserRepository userRepository;

    public DossierServiceImpl(DossierRepository dossierRepository,
                              CompanyAiService companyAiService,
                              AuditService auditService,
                              UserService userService) {
        this.dossierRepository = dossierRepository;
        this.companyAiService = companyAiService;
        this.auditService = auditService;
        this.userService = userService;
    }

    // ✅ Méthode utilitaire pour récupérer le cabinet cible (le cabinet du dossier)
    private Long getTargetCabinetId(Dossier dossier) {
        if (dossier != null && dossier.getCabinet() != null) {
            return dossier.getCabinet().getId();
        }
        return null;
    }

    // ✅ Méthode utilitaire pour récupérer le nom du cabinet cible
    private String getTargetCabinetName(Dossier dossier) {
        if (dossier != null && dossier.getCabinet() != null) {
            return dossier.getCabinet().getName();
        }
        return null;
    }

    @Override
    @Transactional
    public Dossier createDossier(@NonNull Dossier dossier, List<Exercise> exercicesData) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Creating dossier: {} for cabinet: {}", requestId, dossier.getName(), dossier.getCabinet().getId());

        // ✅ FIX: Check if a dossier with the same name exists IN THE SAME CABINET
        Dossier existingDossier = dossierRepository.findByNameAndCabinetId(dossier.getName(), dossier.getCabinet().getId()).orElse(null);

        if (existingDossier != null) {
            log.error("[{}] Dossier with name '{}' already exists in cabinet {} with ID: {}", requestId, dossier.getName(), dossier.getCabinet().getId(), existingDossier.getId());

            // Audit échec création
            auditService.logFailure(userService.getCurrentUser(), "CREATE", "Dossier", null, dossier.getName(), "Un dossier avec le nom '" + dossier.getName() + "' existe déjà dans ce cabinet.");

            throw new IllegalArgumentException("Un dossier avec le nom '" + dossier.getName() + "' existe déjà dans ce cabinet.");
        }

        // If no Dossier with the same name exists in this cabinet, check the Cabinet exists
        Long cabinetEntityId = Objects.requireNonNull(
                Objects.requireNonNull(dossier.getCabinet(), "cabinet").getId(), "cabinet id");
        Cabinet cabinet = cabinetRepository.findById(cabinetEntityId).orElseThrow(() -> {
            auditService.logFailure(userService.getCurrentUser(), "CREATE", "Dossier", null, dossier.getName(), "Cabinet non trouvé");
            return new RuntimeException("Cabinet non trouvé");
        });
        dossier.setCabinet(cabinet);

        // Set default decimal precision if not provided for new dossiers
        if (dossier.getDecimalPrecision() == null) {
            dossier.setDecimalPrecision(2);
            log.info("[{}] Set default decimal precision to: 2", requestId);
        }

        // Save the new Dossier
        Dossier savedDossier = dossierRepository.save(dossier);
        log.info("[{}] New dossier created with ID: {} and decimal precision: {}", requestId, savedDossier.getId(), savedDossier.getDecimalPrecision());

        // ✅ Récupérer le cabinet cible
        Long targetCabinetId = getTargetCabinetId(savedDossier);
        String targetCabinetName = getTargetCabinetName(savedDossier);

        // Create the list of default journals for new dossiers
        createDefaultJournals(savedDossier);

        // Handle Exercise data if provided
        if (exercicesData != null && !exercicesData.isEmpty()) {
            for (Exercise exercise : exercicesData) {
                // Check if an Exercise with the same dates already exists for the Dossier
                boolean exists = exerciseRepository.existsByDossierAndStartDateAndEndDate(savedDossier, exercise.getStartDate(), exercise.getEndDate());

                if (exists) {
                    // Audit échec création exercice
                    auditService.logFailureWithTargetCabinet(
                            userService.getCurrentUser(),
                            "CREATE",
                            "Exercise",
                            savedDossier.getId(),
                            savedDossier.getName(),
                            "Le dossier a déjà des exercices à ces dates",
                            targetCabinetId,
                            targetCabinetName
                    );
                    throw new ExerciseDateConflictException("Le dossier a déjà des exercices à ces dates");
                }

                // Set the Dossier for each Exercise and save it
                exercise.setDossier(savedDossier);
                exerciseRepository.save(exercise);
            }
        }

        // Call the AI Company API - use POST for new companies
        String countryCode = savedDossier.getCountry() != null ? savedDossier.getCountry().getCode() : "NOT_FOUND";

        Company company = new Company();
        company.setId(savedDossier.getId());
        company.setName(savedDossier.getName());
        company.setCountry(countryCode);
        company.setActivity(savedDossier.getActivity());

        try {
            // For new dossiers, use POST
            log.info("[{}] Calling Company AI API to create company for dossier ID: {}", requestId, savedDossier.getId());
            Company createdCompany = companyAiService.createCompany(company);
            log.info("[{}] Company created successfully in AI service with ID: {}", requestId, createdCompany.getId());

        } catch (Exception e) {
            // Log the error and trigger a transaction rollback
            log.error("[{}] Error creating company in AI service for dossier ID {}: {}", requestId, savedDossier.getId(), e.getMessage(), e);

            // Audit échec AI
            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "CREATE",
                    "Dossier",
                    savedDossier.getId(),
                    savedDossier.getName(),
                    "Erreur lors de la création de la société dans le service AI: " + e.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );

            // The @Transactional annotation will ensure rollback on RuntimeException
            throw new CompanyAiException("Erreur lors de la création de la société dans le service AI: " + e.getMessage(), e);
        }

        // Audit succès création avec cabinet cible
        auditService.logSuccessWithTargetCabinet(
                userService.getCurrentUser(),
                "CREATE",
                "Dossier",
                savedDossier.getId(),
                savedDossier.getName(),
                null,
                Map.of(
                        "dossierName", savedDossier.getName(),
                        "cabinetId", savedDossier.getCabinet().getId(),
                        "country", countryCode,
                        "activity", savedDossier.getActivity(),
                        "exercisesCount", exercicesData != null ? exercicesData.size() : 0
                ),
                targetCabinetId,
                targetCabinetName
        );

        return savedDossier;
    }

    @Override
    @Transactional
    public Dossier updateExercises(@NonNull Long dossierId, List<Exercise> updatedExercises) {
        // Fetch the dossier by ID
        Dossier dossier = dossierRepository.findById(dossierId).orElseThrow(() -> {
            auditService.logFailure(userService.getCurrentUser(), "UPDATE", "Exercise", dossierId, "Dossier-" + dossierId, "Dossier non trouvé");
            return new RuntimeException("Dossier non trouvé");
        });

        // ✅ Récupérer le cabinet cible
        Long targetCabinetId = getTargetCabinetId(dossier);
        String targetCabinetName = getTargetCabinetName(dossier);

        List<Exercise> savedExercises = new ArrayList<>();

        // Validate and update the exercises
        for (Exercise updatedExercise : updatedExercises) {
            // Fetch the existing exercise (if updating)
            Long exerciseId = updatedExercise.getId();
            Exercise existingExercise = exerciseId != null
                    ? exerciseRepository.findById(exerciseId).orElse(null)
                    : null;

            // Validate the new date range
            validateExerciseDateRange(dossier, updatedExercise, existingExercise);

            if (existingExercise != null) {
                // Sauvegarder l'ancien état
                Exercise oldExercise = new Exercise();
                oldExercise.setId(existingExercise.getId());
                oldExercise.setStartDate(existingExercise.getStartDate());
                oldExercise.setEndDate(existingExercise.getEndDate());
                oldExercise.setActive(existingExercise.isActive());

                // Update existing exercise
                existingExercise.setStartDate(updatedExercise.getStartDate());
                existingExercise.setEndDate(updatedExercise.getEndDate());
                Exercise saved = exerciseRepository.save(existingExercise);
                savedExercises.add(saved);

                // Audit mise à jour exercice avec cabinet cible
                auditService.logSuccessWithTargetCabinet(
                        userService.getCurrentUser(),
                        "UPDATE",
                        "Exercise",
                        saved.getId(),
                        "Exercise-" + saved.getId(),
                        oldExercise,
                        saved,
                        targetCabinetId,
                        targetCabinetName
                );
            } else {
                // Create a new exercise
                updatedExercise.setDossier(dossier);
                Exercise saved = exerciseRepository.save(updatedExercise);
                savedExercises.add(saved);

                // Audit création exercice avec cabinet cible
                auditService.logSuccessWithTargetCabinet(
                        userService.getCurrentUser(),
                        "CREATE",
                        "Exercise",
                        saved.getId(),
                        "Exercise-" + saved.getId(),
                        null,
                        saved,
                        targetCabinetId,
                        targetCabinetName
                );
            }
        }

        return dossier;
    }

    @Override
    @Transactional
    public void deleteExercises(@NonNull Long dossierId, @NonNull List<Long> exerciseIds) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Suppression des exercices pour le dossier ID: {}, Identifiants des exercices: {}", requestId, dossierId, exerciseIds);

        // Vérifier que le dossier existe
        Dossier dossier = dossierRepository.findById(dossierId).orElseThrow(() -> {
            auditService.logFailure(userService.getCurrentUser(), "DELETE", "Exercise", dossierId, "Dossier-" + dossierId, "Dossier non trouvé avec ID: " + dossierId);
            return new RuntimeException("Dossier non trouvé avec ID: " + dossierId);
        });

        // ✅ Récupérer le cabinet cible
        Long targetCabinetId = getTargetCabinetId(dossier);
        String targetCabinetName = getTargetCabinetName(dossier);

        // Récupérer les exercices à supprimer
        List<Exercise> exercisesToDelete = exerciseRepository.findAllById(exerciseIds);
        if (exercisesToDelete.isEmpty()) {
            String errorMessage = "Aucun exercice trouvé avec les identifiants fournis : " + exerciseIds;
            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "DELETE",
                    "Exercise",
                    dossierId,
                    dossier.getName(),
                    errorMessage,
                    targetCabinetId,
                    targetCabinetName
            );
            throw new IllegalArgumentException(errorMessage);
        }

        // Valider que tous les exercices appartiennent au dossier
        for (Exercise exercise : exercisesToDelete) {
            if (!exercise.getDossier().getId().equals(dossierId)) {
                String errorMessage = "L'exercice avec l'ID " + exercise.getId() + " n'appartient pas au dossier spécifié.";
                auditService.logFailureWithTargetCabinet(
                        userService.getCurrentUser(),
                        "DELETE",
                        "Exercise",
                        exercise.getId(),
                        exercise.getStartDate() + " - " + exercise.getEndDate(),
                        errorMessage,
                        targetCabinetId,
                        targetCabinetName
                );
                throw new IllegalArgumentException(errorMessage);
            }

            // Récupérer les écritures pour l'exercice
            List<Ecriture> ecritures = ecritureRepository.findByDossierAndExerciseId(dossierId, exercise.getId());
            if (!ecritures.isEmpty()) {
                String errorMessage = "Impossible de supprimer l'exercice avec l'ID " + exercise.getId() + " car des écritures comptables y sont associées.";
                auditService.logFailureWithTargetCabinet(
                        userService.getCurrentUser(),
                        "DELETE",
                        "Exercise",
                        exercise.getId(),
                        exercise.getStartDate() + " - " + exercise.getEndDate(),
                        errorMessage,
                        targetCabinetId,
                        targetCabinetName
                );
                throw new IllegalArgumentException(errorMessage);
            }
        }

        // Audit avant suppression
        for (Exercise exercise : exercisesToDelete) {
            auditService.logSuccessWithTargetCabinet(
                    userService.getCurrentUser(),
                    "DELETE",
                    "Exercise",
                    exercise.getId(),
                    exercise.getStartDate() + " - " + exercise.getEndDate(),
                    exercise,
                    null,
                    targetCabinetId,
                    targetCabinetName
            );
        }

        // Supprimer les exercices
        log.info("[{}] Suppression des exercices: {}", requestId, exercisesToDelete);
        exerciseRepository.deleteAll(exercisesToDelete);
        log.info("[{}] Exercices supprimés avec succès.", requestId);
    }

    @Override
    @Transactional
    public DossierDTO updateDossier(@NonNull Long id, @NonNull Dossier dossierDetails) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Updating dossier with ID: {}", requestId, id);

        Dossier existingDossier = dossierRepository.findById(id).orElseThrow(() -> {
            auditService.logFailure(userService.getCurrentUser(), "UPDATE", "Dossier", id, "Dossier-" + id, "Dossier not found for ID: " + id);
            return new RuntimeException("Dossier not found for ID: " + id);
        });

        // ✅ Récupérer le cabinet cible
        Long targetCabinetId = getTargetCabinetId(existingDossier);
        String targetCabinetName = getTargetCabinetName(existingDossier);

        // Store original values for comparison
        String originalName = existingDossier.getName();
        Country originalCountry = existingDossier.getCountry();
        // Sauvegarder l'ancien état
        Dossier oldDossier = new Dossier();
        oldDossier.setId(existingDossier.getId());
        oldDossier.setName(existingDossier.getName());
        oldDossier.setICE(existingDossier.getICE());
        oldDossier.setAddress(existingDossier.getAddress());
        oldDossier.setCity(existingDossier.getCity());
        oldDossier.setPhone(existingDossier.getPhone());
        oldDossier.setEmail(existingDossier.getEmail());
        oldDossier.setCountry(existingDossier.getCountry());
        oldDossier.setActivity(existingDossier.getActivity());
        oldDossier.setDecimalPrecision(existingDossier.getDecimalPrecision());

        // Update dossier fields
        if (dossierDetails.getName() != null) {
            existingDossier.setName(dossierDetails.getName());
        }
        if (dossierDetails.getICE() != null) {
            existingDossier.setICE(dossierDetails.getICE());
        }
        if (dossierDetails.getAddress() != null) {
            existingDossier.setAddress(dossierDetails.getAddress());
        }
        if (dossierDetails.getCity() != null) {
            existingDossier.setCity(dossierDetails.getCity());
        }
        if (dossierDetails.getPhone() != null) {
            existingDossier.setPhone(dossierDetails.getPhone());
        }
        if (dossierDetails.getEmail() != null) {
            existingDossier.setEmail(dossierDetails.getEmail());
        }
        if (dossierDetails.getCountry() != null) {
            existingDossier.setCountry(dossierDetails.getCountry());
        }
        // Update decimal precision if provided
        if (dossierDetails.getDecimalPrecision() != null) {
            existingDossier.setDecimalPrecision(dossierDetails.getDecimalPrecision());
            log.info("[{}] Updated decimal precision to: {}", requestId, dossierDetails.getDecimalPrecision());
        }

        Dossier savedDossier = dossierRepository.save(existingDossier);
        log.info("[{}] Dossier updated successfully with ID: {}", requestId, savedDossier.getId());

        // Call Company AI API only if the name or country changed
        boolean nameChanged = dossierDetails.getName() != null && !dossierDetails.getName().equals(originalName);
        boolean countryChanged = dossierDetails.getCountry() != null && (originalCountry == null || !dossierDetails.getCountry().getCode().equals(originalCountry.getCode()));

        if (nameChanged || countryChanged) {
            try {
                log.info("[{}] Calling Company AI API to update company for dossier ID: {}", requestId, savedDossier.getId());

                String countryCode = savedDossier.getCountry() != null ? savedDossier.getCountry().getCode() : "MAR";

                Company company = new Company();
                company.setId(savedDossier.getId());
                company.setName(savedDossier.getName());
                company.setCountry(countryCode);

                // Use PUT method for updating existing companies
                Company updatedCompany = companyAiService.updateCompany(savedDossier.getId(), company);
                log.info("[{}] Company updated successfully in AI service with ID: {}", requestId, updatedCompany.getId());
            } catch (Exception e) {
                // Log the error but don't fail the transaction
                log.error("[{}] Error updating company in AI service for dossier ID {}: {}", requestId, savedDossier.getId(), e.getMessage(), e);

                // Audit échec AI mais on continue
                auditService.logFailureWithTargetCabinet(
                        userService.getCurrentUser(),
                        "UPDATE",
                        "Dossier",
                        savedDossier.getId(),
                        savedDossier.getName(),
                        "AI update failed: " + e.getMessage(),
                        targetCabinetId,
                        targetCabinetName
                );
            }
        } else {
            log.info("[{}] No need to update company in AI service (no name or country change)", requestId);
        }

        // Audit succès mise à jour avec cabinet cible
        auditService.logSuccessWithTargetCabinet(
                userService.getCurrentUser(),
                "UPDATE",
                "Dossier",
                savedDossier.getId(),
                savedDossier.getName(),
                oldDossier,
                savedDossier,
                targetCabinetId,
                targetCabinetName
        );

        return convertToDTO(savedDossier);
    }

    @Override
    @Transactional
    public void deleteDossier(@NonNull Long dossierId) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Deleting dossier with ID: {}", requestId, dossierId);

        // Verify that the dossier exists
        Dossier dossier = dossierRepository.findById(dossierId).orElseThrow(() -> {
            auditService.logFailure(userService.getCurrentUser(), "DELETE", "Dossier", dossierId, "Dossier-" + dossierId, "Dossier not found for ID: " + dossierId);
            return new RuntimeException("Dossier not found for ID: " + dossierId);
        });

        // ✅ Récupérer le cabinet cible
        Long targetCabinetId = getTargetCabinetId(dossier);
        String targetCabinetName = getTargetCabinetName(dossier);

        // Audit avant suppression avec cabinet cible
        auditService.logSuccessWithTargetCabinet(
                userService.getCurrentUser(),
                "DELETE",
                "Dossier",
                dossierId,
                dossier.getName(),
                dossier,
                null,
                targetCabinetId,
                targetCabinetName
        );

        try {
            // Call Company AI API to delete the company
            log.info("[{}] Calling Company AI API to delete company for dossier ID: {}", requestId, dossierId);
            boolean deleted = companyAiService.deleteCompany(dossierId);

            if (deleted) {
                log.info("[{}] Company deleted successfully from AI service for dossier ID: {}", requestId, dossierId);
            } else {
                log.warn("[{}] Company deletion from AI service returned false for dossier ID: {}", requestId, dossierId);
            }
        } catch (Exception e) {
            // Log the error but continue with the deletion
            log.error("[{}] Error deleting company from AI service for dossier ID {}: {}", requestId, dossierId, e.getMessage(), e);

            // Audit échec AI avec cabinet cible
            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "DELETE",
                    "Dossier",
                    dossierId,
                    dossier.getName(),
                    "AI deletion failed: " + e.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );
        }

        // Now delete the dossier from our system
        dossierRepository.deleteById(dossierId);
        log.info("[{}] Dossier deleted successfully with ID: {}", requestId, dossierId);
    }

    @Override
    @Transactional
    public DossierDTO updateActivity(@NonNull Long dossierId, String activity) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Updating activity for dossier ID: {} to: {}", requestId, dossierId, activity);

        Dossier dossier = dossierRepository.findById(dossierId).orElseThrow(() -> {
            auditService.logFailure(userService.getCurrentUser(), "UPDATE", "Dossier", dossierId, "Dossier-" + dossierId, "Dossier not found for ID: " + dossierId);
            return new RuntimeException("Dossier not found for ID: " + dossierId);
        });

        // ✅ Récupérer le cabinet cible
        Long targetCabinetId = getTargetCabinetId(dossier);
        String targetCabinetName = getTargetCabinetName(dossier);

        String oldActivity = dossier.getActivity();
        dossier.setActivity(activity);
        Dossier savedDossier = dossierRepository.save(dossier);
        log.info("[{}] Activity updated successfully for dossier ID: {}", requestId, dossierId);

        // === NEW: Update AI with the modified company data ===
        try {
            Company company = new Company();
            company.setId(savedDossier.getId());
            company.setName(savedDossier.getName());
            company.setCountry(savedDossier.getCountry() != null ? savedDossier.getCountry().getCode() : null);
            company.setActivity(savedDossier.getActivity());
            log.info("[{}] Company AI updated successfully for dossier ID: {}, company data {}", requestId, dossierId, company);
        } catch (Exception ex) {
            log.warn("[{}] Failed to update AI service for dossier ID: {}: {}", requestId, dossierId, ex.getMessage());
            // Continue gracefully – do not block DB update due to AI service failure

            // Audit échec AI avec cabinet cible
            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "UPDATE",
                    "Dossier",
                    dossierId,
                    dossier.getName(),
                    "AI update failed: " + ex.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );
        }

        // Audit mise à jour activité avec cabinet cible
        auditService.logSuccessWithTargetCabinet(
                userService.getCurrentUser(),
                "UPDATE",
                "Dossier",
                dossierId,
                dossier.getName(),
                Map.of("oldActivity", oldActivity),
                Map.of("newActivity", activity),
                targetCabinetId,
                targetCabinetName
        );

        // Return the updated DTO
        return getTheDossierById(dossierId);
    }

    @Override
    @Transactional
    public int updateAllCompaniesInAi() {
        log.info("Starting batch update of all companies in AI service");

        // Récupérer tous les dossiers
        List<Dossier> allDossiers = dossierRepository.findAll();
        log.info("Found {} dossiers to update", allDossiers.size());

        int successCount = 0;
        int failedCount = 0;
        List<Long> failedIds = new ArrayList<>();

        for (Dossier dossier : allDossiers) {
            // ✅ Récupérer le cabinet cible pour chaque dossier
            Long targetCabinetId = getTargetCabinetId(dossier);
            String targetCabinetName = getTargetCabinetName(dossier);

            try {
                updateSingleCompanyInAi(dossier);
                successCount++;

                // Audit succès batch update avec cabinet cible
                auditService.logSuccessWithTargetCabinet(
                        userService.getCurrentUser(),
                        "BATCH_UPDATE",
                        "Dossier",
                        dossier.getId(),
                        dossier.getName(),
                        null,
                        Map.of("status", "AI update success"),
                        targetCabinetId,
                        targetCabinetName
                );

                if (successCount % 10 == 0) {
                    log.info("Progress: {} companies updated successfully", successCount);
                }

            } catch (Exception e) {
                failedCount++;
                failedIds.add(dossier.getId());
                log.error("Failed to update company for dossier ID {}: {}", dossier.getId(), e.getMessage());

                // Audit échec batch update avec cabinet cible
                auditService.logFailureWithTargetCabinet(
                        userService.getCurrentUser(),
                        "BATCH_UPDATE",
                        "Dossier",
                        dossier.getId(),
                        dossier.getName(),
                        "AI update failed: " + e.getMessage(),
                        targetCabinetId,
                        targetCabinetName
                );

                // Ajouter un délai entre les erreurs pour éviter la surcharge
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

            // Petit délai entre les appels pour ne pas surcharger l'API AI
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("Batch update completed: {} successful, {} failed", successCount, failedCount);
        if (!failedIds.isEmpty()) {
            log.warn("Failed dossier IDs: {}", failedIds);
        }

        // Audit final du batch (sans cabinet cible car c'est un résumé global)
        auditService.logSuccess(
                userService.getCurrentUser(),
                "BATCH_UPDATE",
                "Dossier",
                null,
                "Batch AI Update",
                null,
                Map.of(
                        "total", allDossiers.size(),
                        "success", successCount,
                        "failed", failedCount,
                        "failedIds", failedIds
                )
        );

        return successCount;
    }

    // Les méthodes get (sans modification)
    @Override
    public Dossier getDossierById(@NonNull Long dossierId) {
        return dossierRepository.findById(dossierId).orElseThrow(() -> new RuntimeException("Dossier non trouvé avec l'identifiant : " + dossierId));
    }

    @Override
    public DossierDTO getTheDossierById(@NonNull Long dossierId) {
        return dossierRepository.findDossierById(dossierId).orElseThrow(() -> new RuntimeException("Dossier non trouvé avec l'identifiant : " + dossierId));
    }

    @Override
    public Page<DossierDTO> getDossiersByCabinetId(@NonNull Long cabinetId, Pageable pageable) {
        return dossierRepository.findDossierDTOsByCabinetId(cabinetId, pageable);
    }

    @Override
    public DossierDTO getDossierForUser(@NonNull Long dossierId, @NonNull UUID userId) {
        log.info("User {} accessing dossier {}", userId, dossierId);
        Dossier dossier = dossierRepository.findByIdAndCabinetUsersId(dossierId, userId).orElseThrow(() -> new SecurityException("Dossier not found or access denied"));
        return convertToDTO(dossier);
    }

    @Override
    public DossierDTO getDossierForPacioli(@NonNull Long dossierId) {
        log.info("PACIOLI user accessing dossier {}", dossierId);
        Dossier dossier = dossierRepository.findById(dossierId).orElseThrow(() -> new RuntimeException("Dossier not found"));
        return convertToDTO(dossier);
    }

    @Override
    public Page<Dossier> getDossiersForUser(@NonNull UUID userId, Pageable pageable) {
        log.info("User {} fetching accessible dossiers", userId);
        return dossierRepository.findByCabinetUsersId(userId, pageable);
    }

    @Override
    public Dossier createDossierSecure(@NonNull Dossier dossier, List<Exercise> exercicesData, @NonNull UUID userId) {
        return createDossier(dossier, exercicesData);
    }

    @Override
    public boolean userHasAccessToCabinet(@NonNull UUID userId, @NonNull Long cabinetId) {
        return userRepository.existsByIdAndCabinetId(userId, cabinetId);
    }

    @Override
    public boolean userHasAccessToDossier(@NonNull UUID userId, @NonNull Long dossierId) {
        return dossierRepository.existsByIdAndCabinetUsersId(dossierId, userId);
    }

    @Override
    public DossierDTO updateDossierSecure(@NonNull Long id, @NonNull Dossier dossierDetails, @NonNull UUID userId) {
        return updateDossier(id, dossierDetails);
    }

    @Override
    public void deleteDossierSecure(@NonNull Long dossierId, @NonNull UUID userId) {
        if (!userHasAccessToDossier(userId, dossierId)) {
            throw new SecurityException("User cannot delete this dossier");
        }
        deleteDossier(dossierId);
    }

    private static DossierDTO convertToDTO(Dossier savedDossier) {
        DossierDTO dto = new DossierDTO();
        dto.setId(savedDossier.getId());
        dto.setName(savedDossier.getName());
        dto.setICE(savedDossier.getICE());
        dto.setAddress(savedDossier.getAddress());
        dto.setCity(savedDossier.getCity());
        dto.setPhone(savedDossier.getPhone());
        dto.setEmail(savedDossier.getEmail());
        dto.setActivity(savedDossier.getActivity());
        dto.setDecimalPrecision(savedDossier.getDecimalPrecision());

        if (savedDossier.getCountry() != null) {
            PaysDTO paysDTO = new PaysDTO();
            paysDTO.setCountry(savedDossier.getCountry().getName());
            paysDTO.setCode(savedDossier.getCountry().getCode());

            if (savedDossier.getCountry().getCurrency() != null) {
                PaysDTO.CurrencyDTO currencyDTO = new PaysDTO.CurrencyDTO();
                currencyDTO.setCode(savedDossier.getCountry().getCurrency().getCode());
                currencyDTO.setName(savedDossier.getCountry().getCurrency().getName());
                paysDTO.setCurrency(currencyDTO);
            }

            dto.setPays(paysDTO);
        }

        DossierDTO.CabinetDTO cabinetDTO = new DossierDTO.CabinetDTO();
        cabinetDTO.setId(savedDossier.getCabinet().getId());
        dto.setCabinet(cabinetDTO);

        return dto;
    }

    private void createDefaultJournals(Dossier dossier) {
        List<Journal> defaultJournals = List.of(
                new Journal("HA", "Achats", dossier.getCabinet(), dossier),
                new Journal("VE", "Ventes", dossier.getCabinet(), dossier),
                new Journal("BQ", "Banque", dossier.getCabinet(), dossier),
                new Journal("CA", "Caisse", dossier.getCabinet(), dossier),
                new Journal("PA", "Paie", dossier.getCabinet(), dossier),
                new Journal("OD", "Opérations Diverses", dossier.getCabinet(), dossier)
        );

        List<Journal> journalsToCreate = defaultJournals.stream()
                .filter(journal -> !journalRepository.existsByNameAndDossierId(journal.getName(), dossier.getId()))
                .toList();

        if (!journalsToCreate.isEmpty()) {
            journalRepository.saveAll(journalsToCreate);
        }
    }

    private void validateExerciseDateRange(Dossier dossier, Exercise updatedExercise, Exercise existingExercise) {
        boolean overlapExists = exerciseRepository.existsByDossierAndStartDateAndEndDateOverlap(
                dossier, updatedExercise.getStartDate(), updatedExercise.getEndDate(), updatedExercise.getId());

        if (overlapExists) {
            throw new IllegalArgumentException("Les dates de l'exercice se chevauchent avec celles d'exercices existants");
        }

        List<Ecriture> ecritures = ecritureRepository.findByDossierAndExerciseId(dossier.getId(), updatedExercise.getId());

        for (Ecriture ecriture : ecritures) {
            if (ecriture.getEntryDate().isBefore(updatedExercise.getStartDate()) ||
                    ecriture.getEntryDate().isAfter(updatedExercise.getEndDate())) {
                throw new IllegalArgumentException("Impossible de modifier l'exercice car des écritures comptables existent en dehors des nouvelles dates proposées");
            }
        }
    }

    private void updateSingleCompanyInAi(Dossier dossier) {
        Company company = convertDossierToCompany(dossier);
        companyAiService.updateCompany(dossier.getId(), company);
        log.debug("Successfully updated company in AI for dossier ID: {}", dossier.getId());
    }

    private Company convertDossierToCompany(Dossier dossier) {
        Company company = new Company();
        company.setId(dossier.getId());
        company.setName(dossier.getName());
        company.setActivity(dossier.getActivity());

        if (dossier.getCountry() != null) {
            company.setCountry(dossier.getCountry().getCode());
        }

        return company;
    }
}