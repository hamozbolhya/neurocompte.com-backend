package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Exercise;
import com.pacioli.core.repositories.ExerciceRepository;
import com.pacioli.core.services.ExerciseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExerciseServiceImpl implements ExerciseService {

    private final ExerciceRepository exerciseRepository;

    @Autowired
    public ExerciseServiceImpl(ExerciceRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    @Override
    public List<Exercise> getAllExercises() {
        return exerciseRepository.findAll();
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
    public List<Exercise> getExercisesForEcriture(Long ecritureId) {
        return exerciseRepository.findExercisesByEcritureId(ecritureId);
    }

}

