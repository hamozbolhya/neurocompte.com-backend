package com.pacioli.core.controllers;

import com.pacioli.core.DTO.EcritureDTO;
import com.pacioli.core.DTO.EcritureExportDTO;
import com.pacioli.core.DTO.LineDTO;
import com.pacioli.core.models.Ecriture;
import com.pacioli.core.models.Journal;
import com.pacioli.core.models.Line;
import com.pacioli.core.models.Account;
import com.pacioli.core.repositories.UserRepository;
import com.pacioli.core.services.DossierService;
import com.pacioli.core.services.EcritureService;
import com.pacioli.core.services.ExerciseService;
import com.pacioli.core.services.JournalService;
import com.pacioli.core.utils.SecurityHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/ecritures")
public class EcritureController {
    @Autowired
    private EcritureService ecritureService;
    @Autowired
    private ExerciseService exerciseService;
    @Autowired
    private JournalService journalService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DossierService dossierService;
    @Autowired
    private SecurityHelper securityHelper;
    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/filter")
    public ResponseEntity<Page<EcritureDTO>> getEcrituresWithExercisesByExerciseAndCabinet(
            @RequestParam(value = "exerciseId", required = false) Long exerciseId,
            @RequestParam("cabinetId") Long cabinetId,
            @RequestParam("dossierId") Long dossierId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} filtering ecritures for dossier: {}, cabinet: {}",
                principal.getUsername(), dossierId, cabinetId);

        UUID userId = Objects.requireNonNull(extractUserIdFromPrincipal(principal), "userId");
        Long did = Objects.requireNonNull(dossierId, "dossierId");
        Long cid = Objects.requireNonNull(cabinetId, "cabinetId");

        // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this dossier
        boolean hasAccess = securityHelper.isPacioli(principal)
                || dossierService.userHasAccessToDossier(userId, did);

        if (!hasAccess) {
            log.error("User {} attempted to access ecritures from unauthorized dossier {}",
                    principal.getUsername(), did);
            throw new SecurityException("User cannot access this dossier");
        }

        if (exerciseId != null) {
            boolean isValid = exerciseService.validateExerciseAndCabinet(exerciseId, cid);
            if (!isValid) {
                return ResponseEntity.badRequest().body(null);
            }
        }

        Page<EcritureDTO> ecritures;
        ecritures = ecritureService.getEcrituresByExerciseAndCabinet(
                exerciseId, cid, page, size);
        return ResponseEntity.ok(ecritures);
    }

    // Fetch Ecritures by Piece ID
    @GetMapping("/piece/{pieceId}")
    public ResponseEntity<List<Ecriture>> getEcrituresByPieceId(@PathVariable("pieceId") Long pieceId) {
        List<Ecriture> ecritures = ecritureService.getEcrituresByPieceId(
                Objects.requireNonNull(pieceId, "pieceId"));
        return ResponseEntity.ok(ecritures);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEcriture(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        try {
            // Fetch the existing Ecriture
            Ecriture existingEcriture = ecritureService.getEcritureById(Objects.requireNonNull(id, "id"));
            if (existingEcriture == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ecriture non trouvée avec ID: " + id);
            }

            // Update only the provided fields
            updates.forEach((key, value) -> {
                switch (key) {
                    case "journal":
                        Long dossierIdForJournal = Objects.requireNonNull(
                                Objects.requireNonNull(existingEcriture.getPiece(), "piece").getDossier(), "dossier")
                                .getId();
                        Journal journal = journalService.findByName((String) value,
                                Objects.requireNonNull(dossierIdForJournal, "dossierId"));
                        if (journal == null) {
                            throw new RuntimeException("Journal non trouvé avec le nom: " + value);
                        }
                        existingEcriture.setJournal(journal);
                        break;
                    case "line":
                        existingEcriture.setLines(
                                objectMapper.convertValue(value, new TypeReference<List<Line>>() {}));
                        break;
                    case "entryDate":
                        existingEcriture.setEntryDate(LocalDate.parse((String) value));
                        break;
                    default:
                        // Ignore fields that are not part of the Ecriture entity
                        System.out.println("Ignorer un champ invalide: " + key);
                }
            });

            // Save the updated Ecriture
            Ecriture updatedEcriture = ecritureService.updateEcriture(existingEcriture);
            return ResponseEntity.ok(updatedEcriture);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Une erreur s'est produite: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteEcritures(@RequestBody List<Long> ecritureIds) {
        try {
            ecritureService.deleteEcritures(Objects.requireNonNull(ecritureIds, "ecritureIds"));
            return ResponseEntity.ok("Ecritures deleted successfully");
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ecriture non trouvée avec ID: " + ecritureIds);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Une erreur s'est produite: " + ex.getMessage());
        }
    }

    @PutMapping("/update-compte")
    public ResponseEntity<String> updateCompte(
            @RequestParam("account") String account,
            @RequestBody List<Long> ecritureIds) {
        ecritureService.updateCompte(account, Objects.requireNonNull(ecritureIds, "ecritureIds"));
        return ResponseEntity.ok("Compte updated successfully");
    }

    @GetMapping("/ecritures/{ecritureId}")
    public ResponseEntity<EcritureDTO> getEcritureDetails(@PathVariable Long ecritureId) {
        EcritureDTO ecritureDetails = ecritureService.getEcritureDetails(
                Objects.requireNonNull(ecritureId, "ecritureId"));
        return ResponseEntity.ok(ecritureDetails);
    }

    @PutMapping("/lines/{ecritureId}")
    public ResponseEntity<String> updateEcriture(
            @PathVariable Long ecritureId,
            @RequestBody EcritureDTO ecritureRequest) {
        try {
            // log.debug("Received exchange rate update request for ecriture {}: {}",
            // ecritureId, ecritureRequest);

            // Log exchange rate information if present
            if (ecritureRequest.getExchangeRate() != null) {
                log.debug("Exchange rate information provided: rate={}, from={}, to={}, date={}",
                        ecritureRequest.getExchangeRate(),
                        ecritureRequest.getOriginalCurrency(),
                        ecritureRequest.getConvertedCurrency(),
                        ecritureRequest.getExchangeRateDate());
            }
            // Log the amountUpdated field if present
            if (ecritureRequest.getAmountUpdated() != null) {
                log.debug("Amount updated flag provided: {}", ecritureRequest.getAmountUpdated());
            }

            Ecriture updateRequest = mapUpdateRequest(ecritureRequest);
            ecritureService.updateEcriture(Objects.requireNonNull(ecritureId, "ecritureId"),
                    updateRequest);

            return ResponseEntity.ok("L'écriture a été mise à jour avec succès.");
        } catch (IllegalArgumentException ex) {
            log.error("Validation error during update: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error during update", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur interne du serveur.");
        }
    }

    private Ecriture mapUpdateRequest(EcritureDTO request) {
        Ecriture ecriture = new Ecriture();
        ecriture.setEntryDate(request.getEntryDate());
        ecriture.setExchangeRate(request.getExchangeRate());
        ecriture.setOriginalCurrency(request.getOriginalCurrency());
        ecriture.setConvertedCurrency(request.getConvertedCurrency());
        ecriture.setExchangeRateDate(request.getExchangeRateDate());
        ecriture.setAmountUpdated(request.getAmountUpdated());
        ecriture.setManuallyUpdated(request.getManuallyUpdated());
        ecriture.setManualUpdateDate(request.getManualUpdateDate());

        if (request.getJournal() != null) {
            Journal journal = new Journal();
            journal.setId(request.getJournal().getId());
            journal.setName(request.getJournal().getName());
            journal.setType(request.getJournal().getType());
            ecriture.setJournal(journal);
        }

        if (request.getLines() != null) {
            ecriture.setLines(request.getLines().stream()
                    .map(this::mapUpdateLine)
                    .toList());
        }

        return ecriture;
    }

    private Line mapUpdateLine(LineDTO request) {
        Line line = new Line();
        line.setId(request.getId());
        line.setLabel(request.getLabel());
        line.setDebit(request.getDebit());
        line.setCredit(request.getCredit());
        line.setManuallyUpdated(request.getManuallyUpdated());
        line.setManualUpdateDate(request.getManualUpdateDate());
        line.setOriginalDebit(request.getOriginalDebit());
        line.setOriginalCredit(request.getOriginalCredit());
        line.setOriginalCurrency(request.getOriginalCurrency());
        line.setExchangeRate(request.getExchangeRate());
        line.setConvertedCurrency(request.getConvertedCurrency());
        line.setExchangeRateDate(request.getExchangeRateDate() != null ? request.getExchangeRateDate().toString() : null);
        line.setUsdDebit(request.getUsdDebit());
        line.setUsdCredit(request.getUsdCredit());
        line.setConvertedDebit(request.getConvertedDebit());
        line.setConvertedCredit(request.getConvertedCredit());

        if (request.getAccount() != null) {
            Account account = new Account();
            account.setId(request.getAccount().getId());
            line.setAccount(account);
        }

        return line;
    }

    @GetMapping("/export")
    public List<EcritureExportDTO> exportEcritures(
            @RequestParam("dossierId") Long dossierId,
            @RequestParam(value = "exerciseId", required = false) Long exerciseId,
            @RequestParam(value = "journalId", required = false) Long journalId,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} exporting ecritures for dossier: {}", principal.getUsername(), dossierId);

        UUID userId = Objects.requireNonNull(extractUserIdFromPrincipal(principal), "userId");
        Long did = Objects.requireNonNull(dossierId, "dossierId");

        // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this dossier
        boolean hasAccess = securityHelper.isPacioli(principal)
                || dossierService.userHasAccessToDossier(userId, did);

        if (!hasAccess) {
            log.error("User {} attempted to export ecritures from unauthorized dossier {}",
                    principal.getUsername(), did);
            throw new SecurityException("This dossier " + did + " does not exist in your cabinet");
        }

        // Default `endDate` to `LocalDate.now()` if missing
        endDate = (endDate != null) ? endDate : LocalDate.now();

        List<EcritureExportDTO> exportData;
        exportData = ecritureService.exportEcritures(
                did, exerciseId, journalId, startDate, endDate);

        return exportData;
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
