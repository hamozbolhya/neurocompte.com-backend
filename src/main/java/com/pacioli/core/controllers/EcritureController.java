package com.pacioli.core.controllers;

import com.pacioli.core.DTO.EcritureDTO;
import com.pacioli.core.DTO.EcritureExportDTO;
import com.pacioli.core.models.Ecriture;
import com.pacioli.core.models.Journal;
import com.pacioli.core.models.Line;
import com.pacioli.core.services.EcritureService;
import com.pacioli.core.services.ExerciseService;
import com.pacioli.core.services.JournalService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ecritures")
public class EcritureController {

    private final EcritureService ecritureService;
    private final ExerciseService exerciseService;
    private final JournalService journalService;

    @Autowired
    public EcritureController(EcritureService ecritureService, ExerciseService exerciseService, JournalService journalService) {
        this.ecritureService = ecritureService;
        this.exerciseService = exerciseService;
        this.journalService = journalService;
    }

    // Fetch all Ecritures
   /* @GetMapping
    public ResponseEntity<List<Ecriture>> getAllEcritures() {
        List<Ecriture> ecritures = ecritureService.getAllEcritures();
        return ResponseEntity.ok(ecritures);
    }*/

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
            Ecriture updatedEcriture = ecritureService.updateEcriture(ecritureId, ecritureRequest);
            return ResponseEntity.ok("L'écriture a été mise à jour avec succès.");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur interne du serveur.");
        }
    }

    @GetMapping("/export")
    public ResponseEntity<List<EcritureExportDTO>> exportEcritures(
            @RequestParam Long dossierId,
            @RequestParam(required = false) Long exerciseId,
            @RequestParam(required = false) Long journalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<EcritureExportDTO> ecritures = ecritureService.exportEcritures(dossierId, exerciseId, journalId, startDate, endDate);
        return ResponseEntity.ok(ecritures);
    }

}

