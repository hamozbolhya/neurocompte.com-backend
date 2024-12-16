package com.pacioli.core.controllers;

import com.pacioli.core.DTO.DossierDTO;
import com.pacioli.core.DTO.DossierRequest;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Exercise;
import com.pacioli.core.services.DossierService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/dossiers")
public class DossierController {

    private final DossierService dossierService;

    @Autowired
    public DossierController(DossierService dossierService) {
        this.dossierService = dossierService;
    }

    // Endpoint to add a new dossier
    @Validated
    @PostMapping
    public ResponseEntity<Dossier> createDossier(@Valid @RequestBody DossierRequest request) {
        System.out.println("Incoming Dossier: " + request.getDossier());
        System.out.println("Incoming Exercises: " + request.getExercises());
        if (request.getDossier() == null) {
            throw new RuntimeException("Dossier is null in the request payload");
        }
        Dossier createdDossier = dossierService.createDossier(request.getDossier(), request.getExercises());
        return ResponseEntity.ok(createdDossier);
    }

    // Endpoint to get details of a dossier by ID
    @GetMapping("/{dossierId}")
    public ResponseEntity<DossierDTO> getDossierById(@PathVariable Long dossierId) {
        log.info("START FETCH DOSSIER BY ID -----> {}", dossierId);
        DossierDTO dossier = dossierService.getTheDossierById(dossierId);
        return ResponseEntity.ok(dossier);
    }

    @GetMapping
    public Page<Dossier> getDossiers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return dossierService.getDossiers(PageRequest.of(page, size));
    }

    @GetMapping("/cabinet/{cabinetId}")
    public Page<DossierDTO> getDossiersByCabinetId(
            @PathVariable Long cabinetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return dossierService.getDossiersByCabinetId(cabinetId, PageRequest.of(page, size));
    }

    @PutMapping("/{dossierId}/exercises")
    public ResponseEntity<Object> updateExercises(
            @PathVariable Long dossierId,
            @Valid @RequestBody List<Exercise> updatedExercises) {
        try {
            log.info("Request Body: {}", updatedExercises);

            if (updatedExercises == null || updatedExercises.isEmpty()) {
                throw new IllegalArgumentException("The exercise list cannot be empty.");
            }

            Dossier updatedDossier = dossierService.updateExercises(dossierId, updatedExercises);
            return ResponseEntity.ok(updatedDossier);
        } catch (IllegalArgumentException ex) {
            log.error("Validation failed: {}", ex.getMessage());
            // Return the error message in the response body
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error: {}", ex.getMessage(), ex);
            // Return the unexpected error message in the response body
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ex.getMessage());
        }
    }

    @DeleteMapping("/{dossierId}/exercises")
    public ResponseEntity<?> deleteExercises(
            @PathVariable Long dossierId,
            @RequestBody List<Long> exerciseIds) {
        try {
            log.info("Delete Request for Dossier ID: {}, Exercises: {}", dossierId, exerciseIds);

            if (exerciseIds == null || exerciseIds.isEmpty()) {
                throw new IllegalArgumentException("Les identifiants des exercices ne peuvent pas être vides");
            }

            dossierService.deleteExercises(dossierId, exerciseIds);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            log.error("Validation failed: {}", ex.getMessage());
            // Return a detailed error response
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error during deletion: {}", ex.getMessage(), ex);
            // Return a general error response
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Une erreur inattendue est survenue : " + ex.getMessage());
        }
    }

    @PutMapping("/details/{id}")
    public ResponseEntity<DossierDTO> updateDossier(
            @PathVariable Long id,
            @RequestBody Dossier dossierDetails) {

        // Update the dossier
        DossierDTO updatedDossier = dossierService.updateDossier(id, dossierDetails);

        return ResponseEntity.ok(updatedDossier);
    }
}