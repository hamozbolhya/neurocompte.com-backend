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

    // ✅ Méthode utilitaire pour récupérer le cabinet cible
    private Long getTargetCabinetId(Journal journal) {
        if (journal != null && journal.getCabinet() != null) {
            return journal.getCabinet().getId();
        }
        return null;
    }

    // ✅ Méthode utilitaire pour récupérer le nom du cabinet cible
    private String getTargetCabinetName(Journal journal) {
        if (journal != null && journal.getCabinet() != null) {
            return journal.getCabinet().getName();
        }
        return null;
    }

    @Override
    public Journal addJournal(Journal journal, Long dossierId) {
        // Check if a journal with the same name already exists in the dossier
        if (journalRepository.existsByNameAndDossierId(journal.getName(), dossierId)) {
            auditService.logFailure(
                    userService.getCurrentUser(),
                    "CREATE",
                    "Journal",
                    null,
                    journal.getName(),
                    "Un journal avec le même nom existe déjà dans ce dossier."
            );
            throw new RuntimeException("Un journal avec le même nom existe déjà dans ce dossier.");
        }

        Journal savedJournal = journalRepository.save(journal);

        // ✅ Audit avec cabinet cible
        Long targetCabinetId = getTargetCabinetId(savedJournal);
        String targetCabinetName = getTargetCabinetName(savedJournal);

        auditService.logSuccessWithTargetCabinet(
                userService.getCurrentUser(),
                "CREATE",
                "Journal",
                savedJournal.getId(),
                savedJournal.getName(),
                null,
                savedJournal,
                targetCabinetId,
                targetCabinetName
        );

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

            // ✅ Audit avec cabinet cible
            Long targetCabinetId = getTargetCabinetId(savedJournal);
            String targetCabinetName = getTargetCabinetName(savedJournal);

            auditService.logSuccessWithTargetCabinet(
                    userService.getCurrentUser(),
                    "UPDATE",
                    "Journal",
                    id,
                    savedJournal.getName(),
                    oldJournal,
                    savedJournal,
                    targetCabinetId,
                    targetCabinetName
            );

            return savedJournal;
        }).orElseThrow(() -> {
            // Audit échec (pas de cabinet cible car journal non trouvé)
            auditService.logFailure(
                    userService.getCurrentUser(),
                    "UPDATE",
                    "Journal",
                    id,
                    "Journal-" + id,
                    "Journal non trouvé avec ID: " + id
            );
            return new RuntimeException("Journal non trouvé avec ID: " + id);
        });
    }

    @Override
    @Transactional
    public void deleteJournal(Long id) {
        Journal journal = journalRepository.findById(id).orElseThrow(() -> {
            auditService.logFailure(
                    userService.getCurrentUser(),
                    "DELETE",
                    "Journal",
                    id,
                    "Journal-" + id,
                    "Journal non trouvé avec ID " + id
            );
            return new RuntimeException("Journal non trouvé avec ID " + id);
        });

        if (journalRepository.hasEntries(id)) {
            auditService.logFailure(
                    userService.getCurrentUser(),
                    "DELETE",
                    "Journal",
                    id,
                    journal.getName(),
                    "Impossible de supprimer le journal. Il contient des écritures comptables."
            );
            throw new RuntimeException("Impossible de supprimer le journal. Il contient des écritures comptables.");
        }

        // ✅ Audit avant suppression avec cabinet cible
        Long targetCabinetId = getTargetCabinetId(journal);
        String targetCabinetName = getTargetCabinetName(journal);

        auditService.logSuccessWithTargetCabinet(
                userService.getCurrentUser(),
                "DELETE",
                "Journal",
                id,
                journal.getName(),
                journal,
                null,
                targetCabinetId,
                targetCabinetName
        );

        journalRepository.deleteById(id);
    }

    @Override
    public List<Journal> getAllJournals() {
        List<Journal> journals = journalRepository.findAll();

        // ✅ Audit de consultation
        auditService.logView(
                userService.getCurrentUser(),
                "JournalList",
                null,
                "All Journals"
        );

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
            auditService.logFailure(
                    userService.getCurrentUser(),
                    "VIEW",
                    "Journal",
                    id,
                    "Journal-" + id,
                    "Journal non trouvé avec ID " + id
            );
            return new RuntimeException("Journal non trouvé avec ID " + id);
        });

        // ✅ Audit de consultation
        auditService.logView(
                userService.getCurrentUser(),
                "Journal",
                id,
                journal.getName()
        );

        return journal;
    }

    public Journal findByName(String name, Long dossierId) {
        Journal journal = journalRepository.findByNameAndDossierId(name, dossierId).orElseThrow(() -> {
            auditService.logFailure(
                    userService.getCurrentUser(),
                    "VIEW",
                    "Journal",
                    null,
                    name,
                    "Journal non trouvé avec le nom: " + name + " pour le dossier ID: " + dossierId
            );
            return new RuntimeException("Journal non trouvé avec le nom: " + name + " pour le dossier ID: " + dossierId);
        });

        // ✅ Audit de consultation
        auditService.logView(
                userService.getCurrentUser(),
                "Journal",
                journal.getId(),
                journal.getName()
        );

        return journal;
    }
}