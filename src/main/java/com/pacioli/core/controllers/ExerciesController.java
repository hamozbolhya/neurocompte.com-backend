package com.pacioli.core.controllers;

import com.pacioli.core.models.Exercise;
import com.pacioli.core.services.ExerciseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exercises")
public class ExerciesController {

    private final ExerciseService exerciseService;

    @Autowired
    public ExerciesController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping("/cabinet/{cabinetId}")
    public ResponseEntity<List<Exercise>> getExercisesByCabinetId(@PathVariable Long cabinetId) {
        List<Exercise> exercises = exerciseService.getExercisesByCabinetId(cabinetId);
        return ResponseEntity.ok(exercises);
    }

    @GetMapping("/exercice-by-dossier/{dossierId}")
    public ResponseEntity<List<Exercise>> getExercisesByDossier(@PathVariable Long dossierId) {
        List<Exercise> exercises = exerciseService.getExercisesByDossier(dossierId);
        return ResponseEntity.ok(exercises);
    }

}
