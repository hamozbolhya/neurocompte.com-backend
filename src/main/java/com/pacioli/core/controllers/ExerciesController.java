package com.pacioli.core.controllers;

import com.pacioli.core.DTO.ExerciseRequest;
import com.pacioli.core.models.Exercise;
import com.pacioli.core.repositories.UserRepository;
import com.pacioli.core.services.DossierService;
import com.pacioli.core.services.ExerciseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/exercises")
public class ExerciesController {
    @Autowired
    private  ExerciseService exerciseService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DossierService dossierService;

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
    public ResponseEntity<List<Exercise>> getExercisesByDossier(@PathVariable Long dossierId,
        @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        UUID userId = extractUserIdFromPrincipal(principal);
        if(userId == null) {
            throw new SecurityException("Anonymous user attempting to fetch exercises");
        }

        // ✅ SECURITY CHECK: Verify user has access to this dossier
        if (!dossierService.userHasAccessToDossier(userId, dossierId)) {
            log.error("User {} attempted to access pieces from unauthorized dossier {}", principal.getUsername(), dossierId);
            throw new SecurityException("This dossier " + dossierId + " not exist in your cabinet");
        }

        List<Exercise> exercises = exerciseService.getExercisesByDossier(dossierId);
        return ResponseEntity.ok(exercises);
    }

    @PostMapping("/dossier/{dossierId}")
    public ResponseEntity<List<Exercise>> createExercisesForDossier(
            @PathVariable Long dossierId,
            @RequestBody List<ExerciseRequest> exerciseRequests) {

        List<Exercise> createdExercises = exerciseService.createExercisesForDossier(dossierId, exerciseRequests);
        return ResponseEntity.ok(createdExercises);
    }

    private UUID extractUserIdFromPrincipal(org.springframework.security.core.userdetails.User principal) {
        if (principal == null) {
            log.error("Principal is null - user not authenticated");
            throw new SecurityException("User not authenticated");
        }

        String username = principal.getUsername();
        log.debug("Extracting user ID for username: {}", username);

        try {
            // Look up the user by username to get the UUID
            com.pacioli.core.models.User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        log.error("User not found for username: {}", username);
                        return new SecurityException("User not found");
                    });

            if (user.getId() == null) {
                log.error("User ID is null for user: {}", username);
                throw new SecurityException("User ID not found");
            }

            log.debug("Successfully extracted user ID: {} for user: {}", user.getId(), username);
            return user.getId();

        } catch (SecurityException e) {
            // Re-throw security exceptions
            throw e;
        } catch (Exception e) {
            log.error("Error extracting user ID for username {}: {}", username, e.getMessage(), e);
            throw new SecurityException("Error extracting user information: " + e.getMessage());
        }
    }
}