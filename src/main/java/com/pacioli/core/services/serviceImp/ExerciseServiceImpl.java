package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Exercise;
import com.pacioli.core.repositories.DossierRepository;
import com.pacioli.core.repositories.ExerciceRepository;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.ExerciseService;
import com.pacioli.core.services.UserService;
import com.pacioli.core.DTO.ExerciseRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    public ExerciseServiceImpl(ExerciceRepository exerciseRepository, DossierRepository dossierRepository, AuditService auditService, UserService userService) {
        this.exerciseRepository = exerciseRepository;
        this.dossierRepository = dossierRepository;
        this.auditService = auditService;
        this.userService = userService;
    }

    @Override
    public List<Exercise> getExercisesByCabinetId(Long cabinetId) {
        List<Exercise> exercises = exerciseRepository.findExercisesByCabinetId(cabinetId);
        return exercises;
    }

    @Override
    public List<Exercise> getExercisesByDossier(Long dossierId) {
        List<Exercise> exercises = exerciseRepository.findExercisesByDossierID(dossierId);
        return exercises;
    }

    @Override
    public boolean validateExerciseAndCabinet(Long exerciseId, Long cabinetId) {
        boolean isValid = exerciseRepository.validateExerciseAndCabinet(exerciseId, cabinetId).isPresent();

        // Audit: Validation d'exercice
        if (!isValid) {
            auditService.logFailure(userService.getCurrentUser(), "VALIDATE", "Exercise", exerciseId, "Exercise-" + exerciseId, "Exercise validation failed for cabinet " + cabinetId);
        }

        return isValid;
    }

    @Override
    @Transactional
    public List<Exercise> createExercisesForDossier(Long dossierId, List<ExerciseRequest> exerciseRequests) {
        // Find the dossier
        Dossier dossier = dossierRepository.findById(dossierId).orElseThrow(() -> {
            auditService.logFailure(userService.getCurrentUser(), "CREATE", "Exercise", dossierId, "Dossier-" + dossierId, "Dossier non trouvé avec l'ID: " + dossierId);
            return new RuntimeException("Dossier non trouvé avec l'ID: " + dossierId);
        });

        // Validate each exercise request
        for (int i = 0; i < exerciseRequests.size(); i++) {
            ExerciseRequest request = exerciseRequests.get(i);

            // Validate date order
            if (request.getStartDate().isAfter(request.getEndDate())) {
                String errorMessage = "L'exercice " + (i + 1) + " a une date de début (" + request.getStartDate() + ") après la date de fin (" + request.getEndDate() + ")";

                auditService.logFailure(userService.getCurrentUser(), "CREATE", "Exercise", dossierId, dossier.getName(), errorMessage);

                throw new RuntimeException(errorMessage);
            }

            // ✅ Check for overlapping date ranges (more restrictive)
            boolean overlapExists = exerciseRepository.existsOverlappingExercise(dossier, request.getStartDate(), request.getEndDate());

            if (overlapExists) {
                String errorMessage = "Un exercice existe déjà qui chevauche la période " + request.getStartDate() + " à " + request.getEndDate() + " dans ce dossier";

                auditService.logFailure(userService.getCurrentUser(), "CREATE", "Exercise", dossierId, dossier.getName(), errorMessage);

                throw new RuntimeException(errorMessage);
            }

            // Optional: Keep exact match check for more specific error message
            boolean exactMatchExists = exerciseRepository.existsByDossierAndStartDateAndEndDate(dossier, request.getStartDate(), request.getEndDate());

            if (exactMatchExists) {
                String errorMessage = "Un exercice existe déjà pour les dates exactes " + request.getStartDate() + " à " + request.getEndDate() + " dans ce dossier";

                auditService.logFailure(userService.getCurrentUser(), "CREATE", "Exercise", dossierId, dossier.getName(), errorMessage);

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

        List<Exercise> savedExercises = exerciseRepository.saveAll(exercises);

        // Audit: Création d'exercices
        Map<String, Object> exerciseDetails = new HashMap<>();
        exerciseDetails.put("dossierId", dossierId);
        exerciseDetails.put("dossierName", dossier.getName());
        exerciseDetails.put("exercisesCount", savedExercises.size());
        exerciseDetails.put("exercises", savedExercises.stream().map(ex -> Map.of("id", ex.getId(), "startDate", ex.getStartDate(), "endDate", ex.getEndDate(), "active", ex.isActive())).collect(Collectors.toList()));

        auditService.logSuccess(userService.getCurrentUser(), "CREATE", "Exercise", dossierId, dossier.getName(), null, exerciseDetails);

        return savedExercises;
    }
}