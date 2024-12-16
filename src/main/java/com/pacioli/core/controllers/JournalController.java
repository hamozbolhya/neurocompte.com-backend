package com.pacioli.core.controllers;

import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Journal;
import com.pacioli.core.services.DossierService;
import com.pacioli.core.services.JournalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        Dossier dossier = dossierService.getDossierById(dossierId);
        journal.setDossier(dossier); // Associate the journal with the dossier
        Journal createdJournal = journalService.addJournal(journal, dossierId);
        return ResponseEntity.ok(createdJournal);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Journal> updateJournal(
            @PathVariable Long id,
            @RequestBody Journal updatedJournal) {
        Journal journal = journalService.updateJournal(id, updatedJournal);
        return ResponseEntity.ok(journal);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJournal(@PathVariable Long id) {
        journalService.deleteJournal(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Journal>> getAllJournals() {
        List<Journal> journals = journalService.getAllJournals();
        return ResponseEntity.ok(journals);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Journal> getJournalById(@PathVariable Long id) {
        Journal journal = journalService.getJournalById(id);
        return ResponseEntity.ok(journal);
    }


    @GetMapping("/dossier")
    public ResponseEntity<List<Journal>> getJournalsByDossierId(@RequestParam Long dossierId) {
        List<Journal> journals = journalService.getJournalsByDossierId(dossierId);
        return ResponseEntity.ok(journals);
    }
}
