package com.pacioli.core.services;

import com.pacioli.core.DTO.ExerciseRequest;
import com.pacioli.core.models.Exercise;
import org.springframework.lang.NonNull;

import java.util.List;

public interface ExerciseService {
    List<Exercise> getExercisesByCabinetId(@NonNull Long cabinetId);
    boolean validateExerciseAndCabinet(@NonNull Long exerciseId, Long cabinetId);
    List<Exercise> getExercisesByDossier(@NonNull Long dossierId);
    List<Exercise> createExercisesForDossier(@NonNull Long dossierId, @NonNull List<ExerciseRequest> exerciseRequests);
}
