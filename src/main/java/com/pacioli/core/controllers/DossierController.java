package com.pacioli.core.controllers;

import com.pacioli.core.DTO.DossierDTO;
import com.pacioli.core.DTO.DossierRequest;
import com.pacioli.core.DTO.PaysDTO;
import com.pacioli.core.Exceptions.CompanyAiException;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Exercise;
import com.pacioli.core.repositories.CountryRepository;
import com.pacioli.core.repositories.UserRepository;
import com.pacioli.core.services.DossierService;
import com.pacioli.core.utils.SecurityHelper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private UserRepository userRepository;

    @Autowired
    private SecurityHelper securityHelper;

    @Autowired
    public DossierController(DossierService dossierService) {
        this.dossierService = dossierService;
    }

    @Validated
    @PostMapping
    public ResponseEntity<DossierDTO> createDossier(@Valid @RequestBody DossierRequest request,
                                                    @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] User {} creating new dossier. Request: {}", requestId, principal.getUsername(), request);

        try {
            // Validate request
            if (request.getDossier() == null || request.getDossier().getCabinet() == null || request.getDossier().getCabinet().getId() == null) {
                log.error("[{}] Validation failed: Cabinet ID must not be null", requestId);
                throw new IllegalArgumentException("Cabinet ID must not be null");
            }

            log.debug("[{}] Incoming Dossier: {}", requestId, request.getDossier());
            log.debug("[{}] Incoming Exercises: {}", requestId, request.getExercises());

            // ✅ SECURITY CHECK: Verify PACIOLI or user has access to cabinet
            UUID userId = extractUserId(principal);
            Long cabinetId = request.getDossier().getCabinet().getId();

            boolean hasAccess = securityHelper.isPacioli(principal)
                    || dossierService.userHasAccessToCabinet(userId, cabinetId);

            if (!hasAccess) {
                log.error("[{}] Access denied for user {} to cabinet {}", requestId, principal.getUsername(), cabinetId);
                throw new SecurityException("User cannot access this cabinet");
            }

            // Convert DTO to entity
            Dossier dossierEntity = convertToEntity(request.getDossier());

            // country validation here
            if (dossierEntity.getCountry() == null || dossierEntity.getCountry().getCode() == null || dossierEntity.getCountry().getCode().isBlank()) {
                log.error("[{}] Validation failed: Country is required", requestId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le pays est requis pour créer un dossier.");
            }

            // Create dossier
            Dossier createdDossier = dossierService.createDossierSecure(dossierEntity, request.getExercises(), userId);

            log.info("[{}] Dossier created successfully with ID: {}", requestId, createdDossier.getId());

            // Convert back to DTO for response
            return ResponseEntity.ok(convertToDTO(createdDossier));

        } catch (CompanyAiException e) {
            // Company AI service error - propagate to client with appropriate status code
            log.error("[{}] Company AI service error: {}", requestId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erreur lors de la création de la société dans le service AI: " + e.getMessage(), e);

        } catch (IllegalArgumentException e) {
            // Validation errors
            log.error("[{}] Validation error: {}", requestId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);

        } catch (SecurityException e) {
            log.error("[{}] Security error: {}", requestId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé: " + e.getMessage(), e);

        } catch (Exception e) {
            // All other errors
            log.error("[{}] Unexpected error creating dossier: {}", requestId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur inattendue est survenue lors de la création du dossier: " + e.getMessage(), e);
        }
    }

    // Endpoint to get details of a dossier by ID
    @GetMapping("/{dossierId}")
    public ResponseEntity<DossierDTO> getDossierById(@PathVariable Long dossierId,
                                                     @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        log.info("User {} fetching dossier by ID: {}", principal.getUsername(), dossierId);
        try {
            UUID userId = extractUserId(principal);

            // ✅ SECURITY CHECK: Verify PACIOLI or user has access to dossier
            boolean hasAccess = securityHelper.isPacioli(principal)
                    || dossierService.userHasAccessToDossier(userId, dossierId);

            if (!hasAccess) {
                log.error("Access denied for user {} to dossier {}", principal.getUsername(), dossierId);
                throw new SecurityException("User cannot access this dossier");
            }

            // ✅ Si PACIOLI, on peut bypasser la vérification user/dossier
            DossierDTO dossier;
            if (securityHelper.isPacioli(principal)) {
                log.info("PACIOLI user bypassing user validation for dossier {}", dossierId);
                dossier = dossierService.getDossierForPacioli(dossierId);
            } else {
                dossier = dossierService.getDossierForUser(dossierId, userId);
            }

            return ResponseEntity.ok(dossier);
        } catch (SecurityException e) {
            log.error("User {} attempted to access unauthorized dossier {}", principal.getUsername(), dossierId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé: " + e.getMessage(), e);
        }
    }

    @GetMapping
    public Page<Dossier> getDossiers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} fetching all accessible dossiers", principal.getUsername());
        UUID userId = extractUserId(principal);

        // Sinon, retourner seulement les dossiers accessibles par l'utilisateur
        log.info("Regular user {} accessing only their accessible dossiers", principal.getUsername());
        return dossierService.getDossiersForUser(userId, PageRequest.of(page, size));
    }

    @GetMapping("/cabinet/{cabinetId}")
    public Page<DossierDTO> getDossiersByCabinetId(
            @PathVariable Long cabinetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} fetching dossiers for cabinet: {}", principal.getUsername(), cabinetId);

        try {
            UUID userId = extractUserId(principal);

            // ✅ Utilisation du Helper : PACIOLI ou Accès Cabinet standard
            boolean hasAccess = securityHelper.isPacioli(principal)
                    || dossierService.userHasAccessToCabinet(userId, cabinetId);

            if (!hasAccess) {
                log.error("Access denied for user {} to cabinet {}", principal.getUsername(), cabinetId);
                throw new SecurityException("User cannot access this cabinet");
            }

            // ✅ Si PACIOLI, retourner tous les dossiers du cabinet
            return dossierService.getDossiersByCabinetId(cabinetId, PageRequest.of(page, size));

        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé", e);
        }
    }

    @PutMapping("/{dossierId}/exercises")
    public ResponseEntity<Object> updateExercises(
            @PathVariable Long dossierId,
            @Valid @RequestBody List<Exercise> updatedExercises,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} updating exercises for dossier ID: {}", principal.getUsername(), dossierId);

        try {
            UUID userId = extractUserId(principal);

            // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this dossier
            boolean hasAccess = securityHelper.isPacioli(principal)
                    || dossierService.userHasAccessToDossier(userId, dossierId);

            if (!hasAccess) {
                log.error("User {} attempted to update unauthorized dossier {}", principal.getUsername(), dossierId);
                throw new SecurityException("User cannot access this dossier");
            }

            if (updatedExercises == null || updatedExercises.isEmpty()) {
                throw new IllegalArgumentException("The exercise list cannot be empty.");
            }

            // ✅ Si PACIOLI, on peut bypasser certaines vérifications internes
            Dossier updatedDossier;
            updatedDossier = dossierService.updateExercises(dossierId, updatedExercises);
            return ResponseEntity.ok(updatedDossier);
        } catch (SecurityException e) {
            log.error("Access denied for user {}: {}", principal.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("Validation failed: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @DeleteMapping("/{dossierId}/exercises")
    public ResponseEntity<?> deleteExercises(
            @PathVariable Long dossierId,
            @RequestBody List<Long> exerciseIds,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        try {
            log.info("User {} deleting exercises from dossier ID: {}, Exercises: {}", principal.getUsername(), dossierId, exerciseIds);

            UUID userId = extractUserId(principal);

            // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this dossier
            boolean hasAccess = securityHelper.isPacioli(principal)
                    || dossierService.userHasAccessToDossier(userId, dossierId);

            if (!hasAccess) {
                log.error("User {} attempted to delete from unauthorized dossier {}", principal.getUsername(), dossierId);
                throw new SecurityException("User cannot access this dossier");
            }

            if (exerciseIds == null || exerciseIds.isEmpty()) {
                throw new IllegalArgumentException("Les identifiants des exercices ne peuvent pas être vides");
            }
            dossierService.deleteExercises(dossierId, exerciseIds);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            log.error("Access denied for user {}: {}", principal.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("Validation failed: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error during deletion: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Une erreur inattendue est survenue : " + ex.getMessage());
        }
    }

    @PutMapping("/details/{id}")
    public ResponseEntity<DossierDTO> updateDossier(
            @PathVariable Long id,
            @RequestBody DossierDTO dossierDetails,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} updating dossier with ID: {}", principal.getUsername(), id);

        try {
            UUID userId = extractUserId(principal);

            // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this dossier
            boolean hasAccess = securityHelper.isPacioli(principal)
                    || dossierService.userHasAccessToDossier(userId, id);

            if (!hasAccess) {
                log.error("User {} attempted to update unauthorized dossier {}", principal.getUsername(), id);
                throw new SecurityException("User cannot access this dossier");
            }

            // Convert DTO to entity for the update
            Dossier dossierEntity = convertToEntity(dossierDetails);
            dossierEntity.setId(id); // Ensure the ID is set for update

            // ✅ SECURE: Use secure update method
            DossierDTO updatedDossier = dossierService.updateDossierSecure(id, dossierEntity, userId);
            return ResponseEntity.ok(updatedDossier);
        } catch (SecurityException e) {
            log.error("Access denied for user {}: {}", principal.getUsername(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé: " + e.getMessage(), e);
        }
    }

    @DeleteMapping("/{dossierId}")
    public ResponseEntity<?> deleteDossier(
            @PathVariable Long dossierId,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        String requestId = UUID.randomUUID().toString();
        log.info("[{}] User {} deleting dossier with ID: {}", requestId, principal.getUsername(), dossierId);

        try {
            UUID userId = extractUserId(principal);

            // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this dossier
            boolean hasAccess = securityHelper.isPacioli(principal)
                    || dossierService.userHasAccessToDossier(userId, dossierId);

            if (!hasAccess) {
                log.error("[{}] User {} attempted to delete unauthorized dossier {}", requestId, principal.getUsername(), dossierId);
                throw new SecurityException("User cannot access this dossier");
            }


            dossierService.deleteDossierSecure(dossierId, userId);
            log.info("[{}] Dossier deleted successfully with ID: {}", requestId, dossierId);
            return ResponseEntity.noContent().build();

        } catch (SecurityException e) {
            log.error("[{}] Access denied: {}", requestId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (RuntimeException ex) {
            log.error("[{}] Error deleting dossier: {}", requestId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        } catch (Exception ex) {
            log.error("[{}] Unexpected error during dossier deletion: {}", requestId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Une erreur inattendue est survenue lors de la suppression du dossier: " + ex.getMessage());
        }
    }

    @PutMapping("/{dossierId}/activity")
    public ResponseEntity<DossierDTO> updateActivity(
            @PathVariable Long dossierId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        String requestId = UUID.randomUUID().toString();
        log.info("[{}] User {} updating activity for dossier ID: {}", requestId, principal.getUsername(), dossierId);

        try {
            UUID userId = extractUserId(principal);

            // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this dossier
            boolean hasAccess = securityHelper.isPacioli(principal)
                    || dossierService.userHasAccessToDossier(userId, dossierId);

            if (!hasAccess) {
                log.error("[{}] User {} attempted to update unauthorized dossier {}", requestId, principal.getUsername(), dossierId);
                throw new SecurityException("User cannot access this dossier");
            }

            String activity = request.get("activity");
            DossierDTO updatedDossier;
            updatedDossier = dossierService.updateActivity(dossierId, activity);
            log.info("[{}] Activity updated successfully for dossier ID: {}", requestId, dossierId);
            return ResponseEntity.ok(updatedDossier);

        } catch (SecurityException e) {
            log.error("[{}] Access denied: {}", requestId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé: " + e.getMessage(), e);
        } catch (RuntimeException ex) {
            log.error("[{}] Error updating activity: {}", requestId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (Exception ex) {
            log.error("[{}] Unexpected error: {}", requestId, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur inattendue est survenue: " + ex.getMessage());
        }
    }

    private UUID extractUserId(org.springframework.security.core.userdetails.User principal) {
        if (principal == null) {
            log.error("Principal is null - user not authenticated");
            throw new SecurityException("User not authenticated");
        }

        String username = principal.getUsername();
        log.debug("Extracting user ID for username: {}", username);

        try {
            // Look up the user by the actual logged-in username
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
            throw e;
        } catch (Exception e) {
            log.error("Error extracting user ID for username {}: {}", username, e.getMessage(), e);
            throw new SecurityException("Error extracting user information: " + e.getMessage());
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
        dossier.setActivity(dto.getActivity());
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
            countryRepository.findByCode(dto.getPays().getCode()).ifPresent(dossier::setCountry);
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
        dto.setActivity(entity.getActivity());

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
}