package com.pacioli.core.services;

import com.pacioli.core.models.Exercise;

import java.util.List;

public interface ExerciseService {
    List<Exercise> getAllExercises();
    List<Exercise> getExercisesByCabinetId(Long cabinetId);
    boolean validateExerciseAndCabinet(Long exerciseId, Long cabinetId);
    List<Exercise> getExercisesForEcriture(Long ecritureId);
    List<Exercise> getExercisesByDossier(Long dossierId);
}
