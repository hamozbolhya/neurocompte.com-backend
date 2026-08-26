package com.pacioli.core.controllers;

import com.pacioli.core.DTO.JournalDTO;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Journal;
import com.pacioli.core.services.DossierService;
import com.pacioli.core.services.JournalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/journals")
public class JournalController {
    private final JournalService journalService;
    private final DossierService dossierService;

    public JournalController(JournalService journalService, DossierService dossierService) {
        this.journalService = journalService;
        this.dossierService = dossierService;
    }

    @PostMapping
    public ResponseEntity<Journal> addJournal(
            @RequestParam Long dossierId,
            @RequestBody Journal journal
    ) {
        Long did = Objects.requireNonNull(dossierId, "dossierId");
        Dossier dossier = dossierService.getDossierById(did);
        Journal j = Objects.requireNonNull(journal, "journal");
        j.setDossier(dossier); // Associate the journal with the dossier
        Journal createdJournal = journalService.addJournal(j, did);
        return ResponseEntity.ok(createdJournal);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Journal> updateJournal(
            @PathVariable Long id,
            @RequestBody Journal updatedJournal) {
        Journal journal = journalService.updateJournal(Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(updatedJournal, "updatedJournal"));
        return ResponseEntity.ok(journal);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJournal(@PathVariable Long id) {
        journalService.deleteJournal(Objects.requireNonNull(id, "id"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Journal>> getAllJournals() {
        List<Journal> journals = journalService.getAllJournals();
        return ResponseEntity.ok(journals);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Journal> getJournalById(@PathVariable Long id) {
        Journal journal = journalService.getJournalById(Objects.requireNonNull(id, "id"));
        return ResponseEntity.ok(journal);
    }


    @GetMapping("/dossier")
    public ResponseEntity<List<JournalDTO>> getJournalsByDossierId(@RequestParam Long dossierId) {
        List<Journal> journals = journalService.getJournalsByDossierId(
                Objects.requireNonNull(dossierId, "dossierId"));
        return ResponseEntity.ok(journals.stream()
                .map(this::toDTO)
                .toList());
    }

    private JournalDTO toDTO(Journal journal) {
        JournalDTO dto = new JournalDTO();
        dto.setId(journal.getId());
        dto.setName(journal.getName());
        dto.setType(journal.getType());
        return dto;
    }
}
