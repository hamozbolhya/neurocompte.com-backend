package com.pacioli.core.services;

import com.pacioli.core.DTO.ExerciseRequest;
import com.pacioli.core.models.Exercise;

import java.util.List;

public interface ExerciseService {
    List<Exercise> getExercisesByCabinetId(Long cabinetId);
    boolean validateExerciseAndCabinet(Long exerciseId, Long cabinetId);
    List<Exercise> getExercisesByDossier(Long dossierId);
    List<Exercise> createExercisesForDossier(Long dossierId, List<ExerciseRequest> exerciseRequests);
}
