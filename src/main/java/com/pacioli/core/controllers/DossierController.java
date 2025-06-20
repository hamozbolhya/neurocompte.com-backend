package com.pacioli.core.controllers;

import com.pacioli.core.DTO.DossierDTO;
import com.pacioli.core.DTO.DossierRequest;
import com.pacioli.core.DTO.PaysDTO;
import com.pacioli.core.Exceptions.CompanyAiException;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Exercise;
import com.pacioli.core.repositories.CountryRepository;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/dossiers")
public class DossierController {

    private final DossierService dossierService;
    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    public DossierController(DossierService dossierService) {
        this.dossierService = dossierService;
    }

    // Endpoint to add a new dossier

    /**
     * Endpoint to create a new dossier
     * If the Company AI service returns an error, the transaction will be rolled back and
     * the error will be propagated to the client
     */
    @Validated
    @PostMapping
    public ResponseEntity<DossierDTO> createDossier(@Valid @RequestBody DossierRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Creating new dossier. Request: {}", requestId, request);

        try {
            // Validate request
            if (request.getDossier() == null || request.getDossier().getCabinet() == null
                    || request.getDossier().getCabinet().getId() == null) {
                log.error("[{}] Validation failed: Cabinet ID must not be null", requestId);
                throw new IllegalArgumentException("Cabinet ID must not be null");
            }

            log.debug("[{}] Incoming Dossier: {}", requestId, request.getDossier());
            log.debug("[{}] Incoming Exercises: {}", requestId, request.getExercises());

            // Convert DTO to entity
            Dossier dossierEntity = convertToEntity(request.getDossier());

            // Create dossier
            Dossier createdDossier = dossierService.createDossier(dossierEntity, request.getExercises());
            log.info("[{}] Dossier created successfully with ID: {}", requestId, createdDossier.getId());

            // Convert back to DTO for response
            return ResponseEntity.ok(convertToDTO(createdDossier));

        } catch (CompanyAiException e) {
            // Company AI service error - propagate to client with appropriate status code
            log.error("[{}] Company AI service error: {}", requestId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Erreur lors de la création de la société dans le service AI: " + e.getMessage(), e);

        } catch (IllegalArgumentException e) {
            // Validation errors
            log.error("[{}] Validation error: {}", requestId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);

        } catch (Exception e) {
            // All other errors
            log.error("[{}] Unexpected error creating dossier: {}", requestId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Une erreur inattendue est survenue lors de la création du dossier: " + e.getMessage(), e);
        }
    }

    private Dossier convertToEntity(DossierDTO dto) {
        Dossier dossier = new Dossier();
        dossier.setName(dto.getName());
        dossier.setICE(dto.getICE());
        dossier.setAddress(dto.getAddress());
        dossier.setCity(dto.getCity());
        dossier.setPhone(dto.getPhone());
        dossier.setEmail(dto.getEmail());

        // Set decimal precision with validation
        if (dto.getDecimalPrecision() != null) {
            // Validate range (0-10)
            int precision = Math.max(0, Math.min(10, dto.getDecimalPrecision()));
            dossier.setDecimalPrecision(precision);
            log.info("Setting decimal precision to: {}", precision);
        } else {
            dossier.setDecimalPrecision(2); // Default
            log.info("Using default decimal precision: 2");
        }

        // Set country relationship from pays object
        if (dto.getPays() != null && dto.getPays().getCode() != null) {
            // Find country by code
            countryRepository.findByCode(dto.getPays().getCode())
                    .ifPresent(dossier::setCountry);
        }

        Cabinet cabinet = new Cabinet();
        cabinet.setId(dto.getCabinet().getId());
        dossier.setCabinet(cabinet);

        // Set exercises if they exist in the DTO
        if (dto.getExercises() != null) {
            dossier.setExercises(dto.getExercises());
        }

        return dossier;
    }

    private DossierDTO convertToDTO(Dossier entity) {
        DossierDTO dto = new DossierDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setICE(entity.getICE());
        dto.setAddress(entity.getAddress());
        dto.setCity(entity.getCity());
        dto.setPhone(entity.getPhone());
        dto.setEmail(entity.getEmail());

        // Set decimal precision
        dto.setDecimalPrecision(entity.getDecimalPrecision());

        // Set pays object from country relationship including currency
        if (entity.getCountry() != null) {
            PaysDTO paysDTO = new PaysDTO();
            paysDTO.setCountry(entity.getCountry().getName());
            paysDTO.setCode(entity.getCountry().getCode());

            // Add currency information if available
            if (entity.getCountry().getCurrency() != null) {
                PaysDTO.CurrencyDTO currencyDTO = new PaysDTO.CurrencyDTO();
                currencyDTO.setCode(entity.getCountry().getCurrency().getCode());
                currencyDTO.setName(entity.getCountry().getCurrency().getName());
                paysDTO.setCurrency(currencyDTO);
            }

            dto.setPays(paysDTO);
        }

        DossierDTO.CabinetDTO cabinetDTO = new DossierDTO.CabinetDTO();
        cabinetDTO.setId(entity.getCabinet().getId());
        dto.setCabinet(cabinetDTO);

        // Convert exercises
        if (entity.getExercises() != null) {
            dto.setExercises(entity.getExercises());
        }

        return dto;
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
            @RequestBody DossierDTO dossierDetails) {

        log.info("Updating dossier with ID: {} and details: {}", id, dossierDetails);

        // Convert DTO to entity for the update
        Dossier dossierEntity = convertToEntity(dossierDetails);
        dossierEntity.setId(id); // Ensure the ID is set for update

        // Update the dossier
        DossierDTO updatedDossier = dossierService.updateDossier(id, dossierEntity);

        return ResponseEntity.ok(updatedDossier);
    }

    @DeleteMapping("/{dossierId}")
    public ResponseEntity<?> deleteDossier(@PathVariable Long dossierId) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Deleting dossier with ID: {}", requestId, dossierId);

        try {
            // Call the service to delete the dossier and associated company in AI service
            dossierService.deleteDossier(dossierId);
            log.info("[{}] Dossier deleted successfully with ID: {}", requestId, dossierId);

            return ResponseEntity.noContent().build();
        } catch (RuntimeException ex) {
            log.error("[{}] Error deleting dossier: {}", requestId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        } catch (Exception ex) {
            log.error("[{}] Unexpected error during dossier deletion: {}", requestId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Une erreur inattendue est survenue lors de la suppression du dossier: " + ex.getMessage());
        }
    }
}