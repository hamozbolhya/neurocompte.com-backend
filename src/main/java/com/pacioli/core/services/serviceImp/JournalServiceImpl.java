package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Journal;
import com.pacioli.core.repositories.JournalRepository;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.JournalService;
import com.pacioli.core.services.UserService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JournalServiceImpl implements JournalService {
    private final JournalRepository journalRepository;
    private final AuditService auditService;
    private final UserService userService;

    public JournalServiceImpl(JournalRepository journalRepository, AuditService auditService, UserService userService) {
        this.journalRepository = journalRepository;
        this.auditService = auditService;
        this.userService = userService;
    }

    @Override
    public Journal addJournal(Journal journal, Long dossierId) {
        // Check if a journal with the same name already exists in the dossier
        if (journalRepository.existsByNameAndDossierId(journal.getName(), dossierId)) {
            auditService.logFailure(userService.getCurrentUser(), "CREATE", "Journal", null, journal.getName(), "Un journal avec le même nom existe déjà dans ce dossier.");
            throw new RuntimeException("Un journal avec le même nom existe déjà dans ce dossier.");
        }

        Journal savedJournal = journalRepository.save(journal);

        // Audit
        auditService.logSuccess(userService.getCurrentUser(), "CREATE", "Journal", savedJournal.getId(), savedJournal.getName(), null, savedJournal);

        return savedJournal;
    }

    @Override
    public Journal updateJournal(Long id, Journal updatedJournal) {
        return journalRepository.findById(id).map(existingJournal -> {
            // Sauvegarder l'ancien état pour l'audit
            Journal oldJournal = new Journal();
            oldJournal.setId(existingJournal.getId());
            oldJournal.setName(existingJournal.getName());
            oldJournal.setType(existingJournal.getType());
            oldJournal.setCabinet(existingJournal.getCabinet());
            oldJournal.setDossier(existingJournal.getDossier());

            // Mise à jour
            existingJournal.setName(updatedJournal.getName());
            existingJournal.setType(updatedJournal.getType());
            existingJournal.setCabinet(updatedJournal.getCabinet());

            Journal savedJournal = journalRepository.save(existingJournal);

            // Audit
            auditService.logSuccess(userService.getCurrentUser(), "UPDATE", "Journal", id, savedJournal.getName(), oldJournal, savedJournal);

            return savedJournal;
        }).orElseThrow(() -> {
            // Audit échec
            auditService.logFailure(userService.getCurrentUser(), "UPDATE", "Journal", id, "Journal-" + id, "Journal non trouvé avec ID: " + id);
            return new RuntimeException("Journal non trouvé avec ID: " + id);
        });
    }

    @Override
    @Transactional
    public void deleteJournal(Long id) {
        Journal journal = journalRepository.findById(id).orElseThrow(() -> {
            auditService.logFailure(userService.getCurrentUser(), "DELETE", "Journal", id, "Journal-" + id, "Journal non trouvé avec ID " + id);
            return new RuntimeException("Journal non trouvé avec ID " + id);
        });

        if (journalRepository.hasEntries(id)) {
            auditService.logFailure(userService.getCurrentUser(), "DELETE", "Journal", id, journal.getName(), "Impossible de supprimer le journal. Il contient des écritures comptables.");
            throw new RuntimeException("Impossible de supprimer le journal. Il contient des écritures comptables.");
        }

        // Audit avant suppression
        auditService.logSuccess(userService.getCurrentUser(), "DELETE", "Journal", id, journal.getName(), journal, null);

        journalRepository.deleteById(id);
    }

    @Override
    public List<Journal> getAllJournals() {
        List<Journal> journals = journalRepository.findAll();
        return journals;
    }

    @Override
    public List<Journal> getJournalsByDossierId(Long dossierId) {
        List<Journal> journals = journalRepository.findByDossierId(dossierId);
        return journals;
    }

    @Override
    public Journal getJournalById(Long id) {
        Journal journal = journalRepository.findById(id).orElseThrow(() -> {
            auditService.logFailure(userService.getCurrentUser(), "VIEW", "Journal", id, "Journal-" + id, "Journal non trouvé avec ID " + id);
            return new RuntimeException("Journal non trouvé avec ID " + id);
        });

        return journal;
    }

    public Journal findByName(String name, Long dossierId) {
        Journal journal = journalRepository.findByNameAndDossierId(name, dossierId).orElseThrow(() -> {
            return new RuntimeException("Journal non trouvé avec le nom: " + name + " pour le dossier ID: " + dossierId);
        });
        return journal;
    }
}