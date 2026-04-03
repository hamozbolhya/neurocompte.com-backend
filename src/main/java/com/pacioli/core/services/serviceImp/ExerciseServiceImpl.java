package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Exercise;
import com.pacioli.core.repositories.DossierRepository;
import com.pacioli.core.repositories.ExerciceRepository;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.ExerciseService;
import com.pacioli.core.services.UserService;
import com.pacioli.core.DTO.ExerciseRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExerciseServiceImpl implements ExerciseService {

    private final ExerciceRepository exerciseRepository;
    private final DossierRepository dossierRepository;
    private final AuditService auditService;
    private final UserService userService;

    public ExerciseServiceImpl(ExerciceRepository exerciseRepository, DossierRepository dossierRepository,
                               AuditService auditService, UserService userService) {
        this.exerciseRepository = exerciseRepository;
        this.dossierRepository = dossierRepository;
        this.auditService = auditService;
        this.userService = userService;
    }

    // ✅ Méthode utilitaire pour récupérer le cabinet cible à partir du dossier
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

    // ✅ Méthode utilitaire à partir de l'exercice
    private Long getTargetCabinetId(Exercise exercise) {
        if (exercise != null && exercise.getDossier() != null && exercise.getDossier().getCabinet() != null) {
            return exercise.getDossier().getCabinet().getId();
        }
        return null;
    }

    // ✅ Méthode utilitaire pour le nom du cabinet cible à partir de l'exercice
    private String getTargetCabinetName(Exercise exercise) {
        if (exercise != null && exercise.getDossier() != null && exercise.getDossier().getCabinet() != null) {
            return exercise.getDossier().getCabinet().getName();
        }
        return null;
    }

    @Override
    public List<Exercise> getExercisesByCabinetId(@NonNull Long cabinetId) {
        List<Exercise> exercises = exerciseRepository.findExercisesByCabinetId(cabinetId);

        // ✅ Audit de consultation
        auditService.logView(
                userService.getCurrentUser(),
                "ExerciseList",
                cabinetId,
                "Cabinet-" + cabinetId
        );

        return exercises;
    }

    @Override
    public List<Exercise> getExercisesByDossier(@NonNull Long dossierId) {
        List<Exercise> exercises = exerciseRepository.findExercisesByDossierID(dossierId);

        // ✅ Audit de consultation
        auditService.logView(
                userService.getCurrentUser(),
                "ExerciseList",
                dossierId,
                "Dossier-" + dossierId
        );

        return exercises;
    }

    @Override
    public boolean validateExerciseAndCabinet(@NonNull Long exerciseId, Long cabinetId) {
        boolean isValid = exerciseRepository.validateExerciseAndCabinet(exerciseId, cabinetId).isPresent();

        // ✅ Audit avec cabinet cible (si on peut récupérer l'exercice)
        if (!isValid) {
            // Essayer de récupérer l'exercice pour avoir le cabinet cible
            Exercise exercise = exerciseRepository.findById(exerciseId).orElse(null);
            Long targetCabinetId = exercise != null ? getTargetCabinetId(exercise) : null;
            String targetCabinetName = exercise != null ? getTargetCabinetName(exercise) : null;

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "VALIDATE",
                    "Exercise",
                    exerciseId,
                    "Exercise-" + exerciseId,
                    "Exercise validation failed for cabinet " + cabinetId,
                    targetCabinetId,
                    targetCabinetName
            );
        }

        return isValid;
    }

    @Override
    @Transactional
    public List<Exercise> createExercisesForDossier(@NonNull Long dossierId, @NonNull List<ExerciseRequest> exerciseRequests) {
        // Find the dossier
        Dossier dossier = dossierRepository.findById(dossierId).orElseThrow(() -> {
            // ✅ Audit d'échec (pas de dossier donc pas de cabinet cible)
            auditService.logFailure(
                    userService.getCurrentUser(),
                    "CREATE",
                    "Exercise",
                    dossierId,
                    "Dossier-" + dossierId,
                    "Dossier non trouvé avec l'ID: " + dossierId
            );
            return new RuntimeException("Dossier non trouvé avec l'ID: " + dossierId);
        });

        // ✅ Récupérer le cabinet cible
        Long targetCabinetId = getTargetCabinetId(dossier);
        String targetCabinetName = getTargetCabinetName(dossier);

        // Validate each exercise request
        for (int i = 0; i < exerciseRequests.size(); i++) {
            ExerciseRequest request = exerciseRequests.get(i);

            // Validate date order
            if (request.getStartDate().isAfter(request.getEndDate())) {
                String errorMessage = "L'exercice " + (i + 1) + " a une date de début (" + request.getStartDate() + ") après la date de fin (" + request.getEndDate() + ")";

                // ✅ Audit d'échec avec cabinet cible
                auditService.logFailureWithTargetCabinet(
                        userService.getCurrentUser(),
                        "CREATE",
                        "Exercise",
                        dossierId,
                        dossier.getName(),
                        errorMessage,
                        targetCabinetId,
                        targetCabinetName
                );

                throw new RuntimeException(errorMessage);
            }

            // ✅ Check for overlapping date ranges (more restrictive)
            boolean overlapExists = exerciseRepository.existsOverlappingExercise(dossier, request.getStartDate(), request.getEndDate());

            if (overlapExists) {
                String errorMessage = "Un exercice existe déjà qui chevauche la période " + request.getStartDate() + " à " + request.getEndDate() + " dans ce dossier";

                // ✅ Audit d'échec avec cabinet cible
                auditService.logFailureWithTargetCabinet(
                        userService.getCurrentUser(),
                        "CREATE",
                        "Exercise",
                        dossierId,
                        dossier.getName(),
                        errorMessage,
                        targetCabinetId,
                        targetCabinetName
                );

                throw new RuntimeException(errorMessage);
            }

            // Optional: Keep exact match check for more specific error message
            boolean exactMatchExists = exerciseRepository.existsByDossierAndStartDateAndEndDate(dossier, request.getStartDate(), request.getEndDate());

            if (exactMatchExists) {
                String errorMessage = "Un exercice existe déjà pour les dates exactes " + request.getStartDate() + " à " + request.getEndDate() + " dans ce dossier";

                // ✅ Audit d'échec avec cabinet cible
                auditService.logFailureWithTargetCabinet(
                        userService.getCurrentUser(),
                        "CREATE",
                        "Exercise",
                        dossierId,
                        dossier.getName(),
                        errorMessage,
                        targetCabinetId,
                        targetCabinetName
                );

                throw new RuntimeException(errorMessage);
            }
        }

        // Create and save exercises
        List<Exercise> exercises = exerciseRequests.stream().map(request -> {
            Exercise exercise = new Exercise();
            exercise.setDossier(dossier);
            exercise.setStartDate(request.getStartDate());
            exercise.setEndDate(request.getEndDate());
            exercise.setActive(request.getActive() != null ? request.getActive() : true);
            return exercise;
        }).collect(Collectors.toList());

        List<Exercise> savedExercises = exerciseRepository.saveAll(new ArrayList<>(exercises));

        // ✅ Audit avec cabinet cible
        Map<String, Object> exerciseDetails = new HashMap<>();
        exerciseDetails.put("dossierId", dossierId);
        exerciseDetails.put("dossierName", dossier.getName());
        exerciseDetails.put("exercisesCount", savedExercises.size());
        exerciseDetails.put("exercises", savedExercises.stream()
                .map(ex -> Map.of(
                        "id", ex.getId(),
                        "startDate", ex.getStartDate(),
                        "endDate", ex.getEndDate(),
                        "active", ex.isActive()
                )).collect(Collectors.toList()));

        auditService.logSuccessWithTargetCabinet(
                userService.getCurrentUser(),
                "CREATE",
                "Exercise",
                dossierId,
                dossier.getName(),
                null,
                exerciseDetails,
                targetCabinetId,
                targetCabinetName
        );

        return savedExercises;
    }
}