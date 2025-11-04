package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Exercise;
import com.pacioli.core.repositories.DossierRepository;
import com.pacioli.core.repositories.ExerciceRepository;
import com.pacioli.core.services.ExerciseService;
import com.pacioli.core.DTO.ExerciseRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExerciseServiceImpl implements ExerciseService {

    private final ExerciceRepository exerciseRepository;
    private final DossierRepository dossierRepository;

    @Autowired
    public ExerciseServiceImpl(ExerciceRepository exerciseRepository, DossierRepository dossierRepository) {
        this.exerciseRepository = exerciseRepository;
        this.dossierRepository = dossierRepository;
    }

    @Override
    public List<Exercise> getExercisesByCabinetId(Long cabinetId) {
        return exerciseRepository.findExercisesByCabinetId(cabinetId);
    }

    @Override
    public List<Exercise> getExercisesByDossier(Long dossierId) {
        return exerciseRepository.findExercisesByDossierID(dossierId);
    }

    @Override
    public boolean validateExerciseAndCabinet(Long exerciseId, Long cabinetId) {
        return exerciseRepository.validateExerciseAndCabinet(exerciseId, cabinetId).isPresent();
    }

    @Override
    @Transactional
    public List<Exercise> createExercisesForDossier(Long dossierId, List<ExerciseRequest> exerciseRequests) {
        // Find the dossier
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new RuntimeException("Dossier non trouvé avec l'ID: " + dossierId));

        // Validate each exercise request
        for (int i = 0; i < exerciseRequests.size(); i++) {
            ExerciseRequest request = exerciseRequests.get(i);

            // Validate date order
            if (request.getStartDate().isAfter(request.getEndDate())) {
                throw new RuntimeException("L'exercice " + (i + 1) + " a une date de début (" +
                        request.getStartDate() + ") après la date de fin (" + request.getEndDate() + ")");
            }

            // ✅ Check for overlapping date ranges (more restrictive)
            boolean overlapExists = exerciseRepository.existsOverlappingExercise(
                    dossier, request.getStartDate(), request.getEndDate()
            );

            if (overlapExists) {
                throw new RuntimeException("Un exercice existe déjà qui chevauche la période " +
                        request.getStartDate() + " à " + request.getEndDate() + " dans ce dossier");
            }

            // Optional: Keep exact match check for more specific error message
            boolean exactMatchExists = exerciseRepository.existsByDossierAndStartDateAndEndDate(
                    dossier, request.getStartDate(), request.getEndDate()
            );

            if (exactMatchExists) {
                throw new RuntimeException("Un exercice existe déjà pour les dates exactes " +
                        request.getStartDate() + " à " + request.getEndDate() + " dans ce dossier");
            }
        }

        // Create and save exercises
        List<Exercise> exercises = exerciseRequests.stream()
                .map(request -> {
                    Exercise exercise = new Exercise();
                    exercise.setDossier(dossier);
                    exercise.setStartDate(request.getStartDate());
                    exercise.setEndDate(request.getEndDate());
                    exercise.setActive(request.getActive() != null ? request.getActive() : true);
                    return exercise;
                })
                .collect(Collectors.toList());

        return exerciseRepository.saveAll(exercises);
    }
}