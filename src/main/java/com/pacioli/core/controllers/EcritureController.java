package com.pacioli.core.controllers;

import com.pacioli.core.DTO.EcritureDTO;
import com.pacioli.core.DTO.EcritureExportDTO;
import com.pacioli.core.models.Account;
import com.pacioli.core.models.Ecriture;
import com.pacioli.core.models.Journal;
import com.pacioli.core.models.Line;
import com.pacioli.core.services.EcritureService;
import com.pacioli.core.services.ExerciseService;
import com.pacioli.core.services.JournalService;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.pacioli.core.services.AccountService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ecritures")
public class EcritureController {

    private final EcritureService ecritureService;
    private final ExerciseService exerciseService;
    private final JournalService journalService;
    private final AccountService accountService;

    @Autowired
    public EcritureController(EcritureService ecritureService, ExerciseService exerciseService, JournalService journalService, AccountService accountService) {
        this.ecritureService = ecritureService;
        this.exerciseService = exerciseService;
        this.journalService = journalService;
        this.accountService = accountService;
    }


    @GetMapping("/filter")
    public ResponseEntity<List<EcritureDTO>> getEcrituresWithExercisesByExerciseAndCabinet(
            @RequestParam(value = "exerciseId", required = false) Long exerciseId,
            @RequestParam("cabinetId") Long cabinetId) {

        if (exerciseId != null) {
            boolean isValid = exerciseService.validateExerciseAndCabinet(exerciseId, cabinetId);
            if (!isValid) {
                return ResponseEntity.badRequest().body(null);
            }
        }

        List<EcritureDTO> ecritures = ecritureService.getEcrituresByExerciseAndCabinet(exerciseId, cabinetId);
        return ResponseEntity.ok(ecritures);
    }




    // Fetch Ecritures by Piece ID
    @GetMapping("/piece/{pieceId}")
    public ResponseEntity<List<Ecriture>> getEcrituresByPieceId(@PathVariable("pieceId") Long pieceId) {
        List<Ecriture> ecritures = ecritureService.getEcrituresByPieceId(pieceId);
        return ResponseEntity.ok(ecritures);
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updateEcriture(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        try {
            // Fetch the existing Ecriture
            Ecriture existingEcriture = ecritureService.getEcritureById(id);
            if (existingEcriture == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ecriture non trouvée avec ID: " + id);
            }

            // Update only the provided fields
            updates.forEach((key, value) -> {
                switch (key) {
                    case "journal":
                        Journal journal = journalService.findByName((String) value, existingEcriture.getPiece().getDossier().getId());
                        if (journal == null) {
                            throw new RuntimeException("Journal non trouvé avec le nom: " + value);
                        }
                        existingEcriture.setJournal(journal);
                        break;
                    case "line":
                        existingEcriture.setLines((List<Line>) value);
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
            ecritureService.deleteEcritures(ecritureIds);
            return ResponseEntity.ok("Ecritures deleted successfully");
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ecriture non trouvée avec ID: " + ecritureIds);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Une erreur s'est produite: " + ex.getMessage());
        }
    }

    @PutMapping("/update-compte")
    public ResponseEntity<String> updateCompte(
            @RequestParam("account") String account,
            @RequestBody List<Long> ecritureIds
    ) {
        ecritureService.updateCompte(account, ecritureIds);
        return ResponseEntity.ok("Compte updated successfully");
    }

    @GetMapping("/ecritures/{ecritureId}")
    public ResponseEntity<EcritureDTO> getEcritureDetails(@PathVariable Long ecritureId) {
        EcritureDTO ecritureDetails = ecritureService.getEcritureDetails(ecritureId);
        return ResponseEntity.ok(ecritureDetails);
    }

    @PutMapping("/lines/{ecritureId}")
    public ResponseEntity<String> updateEcriture(
            @PathVariable Long ecritureId,
            @RequestBody Ecriture ecritureRequest
    ) {
        try {
            log.debug("Received exchange rate update request for ecriture {}: {}",
                    ecritureId, ecritureRequest);

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

            Ecriture updatedEcriture = ecritureService.updateEcriture(ecritureId, ecritureRequest);
            return ResponseEntity.ok("L'écriture a été mise à jour avec succès.");
        } catch (IllegalArgumentException ex) {
            log.error("Validation error during update: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error during update", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur interne du serveur.");
        }
    }

    // ADD THESE TWO METHODS HERE - RIGHT AFTER updateEcriture method

    private void updateLines(Ecriture ecriture, List<Map<String, Object>> linesData) {
        // Clear existing lines
        if (ecriture.getLines() != null) {
            ecriture.getLines().clear();
        } else {
            ecriture.setLines(new ArrayList<>());
        }

        // Add updated lines
        for (Map<String, Object> lineData : linesData) {
            Line line = new Line();

            // Set line ID if exists (for updates)
            if (lineData.containsKey("id") && lineData.get("id") != null) {
                line.setId(((Number) lineData.get("id")).longValue());
            }

            // Set account
            if (lineData.containsKey("account") && lineData.get("account") instanceof Map) {
                Map<String, Object> accountMap = (Map<String, Object>) lineData.get("account");
                if (accountMap.containsKey("id")) {
                    Long accountId = ((Number) accountMap.get("id")).longValue();
                    Account account = accountService.findById(accountId);
                    if (account != null) {
                        line.setAccount(account);
                    }
                }
            }

            // Set label
            if (lineData.containsKey("label")) {
                line.setLabel((String) lineData.get("label"));
            }

            // Set debit and credit
            line.setDebit(lineData.containsKey("debit") ? ((Number) lineData.get("debit")).doubleValue() : 0.0);
            line.setCredit(lineData.containsKey("credit") ? ((Number) lineData.get("credit")).doubleValue() : 0.0);

            // Set manually updated
            if (lineData.containsKey("manuallyUpdated")) {
                line.setManuallyUpdated((Boolean) lineData.get("manuallyUpdated"));
                if ((Boolean) lineData.get("manuallyUpdated")) {
                    line.setManualUpdateDate(LocalDate.now());
                }
            }

            // CRITICAL: Handle currency fields - set to NULL if not provided or invalid
            // Original Currency
            if (lineData.containsKey("originalCurrency")) {
                String originalCurrency = (String) lineData.get("originalCurrency");
                if (isValidCurrency(originalCurrency)) {
                    line.setOriginalCurrency(originalCurrency);
                } else {
                    line.setOriginalCurrency(null);  // Explicitly set to null
                }
            } else {
                line.setOriginalCurrency(null);  // Not in payload, set to null
            }

            // Converted Currency
            if (lineData.containsKey("convertedCurrency")) {
                String convertedCurrency = (String) lineData.get("convertedCurrency");
                if (isValidCurrency(convertedCurrency)) {
                    line.setConvertedCurrency(convertedCurrency);
                } else {
                    line.setConvertedCurrency(null);
                }
            } else {
                line.setConvertedCurrency(null);
            }

            // Exchange Rate
            if (lineData.containsKey("exchangeRate") && lineData.get("exchangeRate") != null) {
                try {
                    Double exchangeRate = ((Number) lineData.get("exchangeRate")).doubleValue();
                    if (exchangeRate > 0) {
                        line.setExchangeRate(exchangeRate);
                    } else {
                        line.setExchangeRate(null);
                    }
                } catch (Exception e) {
                    line.setExchangeRate(null);
                }
            } else {
                line.setExchangeRate(null);
            }

            // Original Debit/Credit
            if (lineData.containsKey("originalDebit") && lineData.get("originalDebit") != null) {
                try {
                    line.setOriginalDebit(((Number) lineData.get("originalDebit")).doubleValue());
                } catch (Exception e) {
                    line.setOriginalDebit(null);
                }
            } else {
                line.setOriginalDebit(null);
            }

            if (lineData.containsKey("originalCredit") && lineData.get("originalCredit") != null) {
                try {
                    line.setOriginalCredit(((Number) lineData.get("originalCredit")).doubleValue());
                } catch (Exception e) {
                    line.setOriginalCredit(null);
                }
            } else {
                line.setOriginalCredit(null);
            }

            // Converted Debit/Credit
            if (lineData.containsKey("convertedDebit") && lineData.get("convertedDebit") != null) {
                try {
                    line.setConvertedDebit(((Number) lineData.get("convertedDebit")).doubleValue());
                } catch (Exception e) {
                    line.setConvertedDebit(null);
                }
            } else {
                line.setConvertedDebit(null);
            }

            if (lineData.containsKey("convertedCredit") && lineData.get("convertedCredit") != null) {
                try {
                    line.setConvertedCredit(((Number) lineData.get("convertedCredit")).doubleValue());
                } catch (Exception e) {
                    line.setConvertedCredit(null);
                }
            } else {
                line.setConvertedCredit(null);
            }

            // Exchange Rate Date
            if (lineData.containsKey("exchangeRateDate") && lineData.get("exchangeRateDate") != null) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                    line.setExchangeRateDate(String.valueOf(LocalDate.parse((String) lineData.get("exchangeRateDate"), formatter)));
                } catch (Exception e) {
                    line.setExchangeRateDate(null);
                }
            } else {
                line.setExchangeRateDate(null);
            }

            // Set the relationship
            line.setEcriture(ecriture);
            ecriture.getLines().add(line);
        }
    }

    // Helper method to validate currency
    private boolean isValidCurrency(String currency) {
        return currency != null &&
                !currency.isEmpty() &&
                !currency.equals("NAN&") &&
                !currency.equals("null") &&
                !currency.equals("undefined") &&
                currency.matches("[A-Z]{3}");
    }


    @GetMapping("/export")
    public List<EcritureExportDTO> exportEcritures(
            @RequestParam("dossierId") Long dossierId,
            @RequestParam(value = "exerciseId", required = false) Long exerciseId,
            @RequestParam(value = "journalId", required = false) Long journalId,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        // Default `endDate` to `LocalDate.now()` if missing
        endDate = (endDate != null) ? endDate : LocalDate.now();
        return ecritureService.exportEcritures(dossierId, exerciseId, journalId, startDate, endDate);
    }

}

