package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Journal;
import com.pacioli.core.repositories.JournalRepository;
import com.pacioli.core.services.JournalService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JournalServiceImpl implements JournalService {
    private final JournalRepository journalRepository;

    public JournalServiceImpl(JournalRepository journalRepository) {
        this.journalRepository = journalRepository;
    }

    @Override
    public Journal addJournal(Journal journal, Long dossierId) {
        // Check if a journal with the same name already exists in the dossier
        if (journalRepository.existsByNameAndDossierId(journal.getName(), dossierId)) {
            throw new RuntimeException("Un journal avec le même nom existe déjà dans ce dossier.");
        }
        return journalRepository.save(journal);
    }

    @Override
    public Journal updateJournal(Long id, Journal updatedJournal) {
        return journalRepository.findById(id).map(existingJournal -> {
            existingJournal.setName(updatedJournal.getName());
            existingJournal.setType(updatedJournal.getType());
            existingJournal.setCabinet(updatedJournal.getCabinet());
            return journalRepository.save(existingJournal);
        }).orElseThrow(() -> new RuntimeException("Journal non trouvé avec ID: " + id));
    }

    @Override
    @Transactional
    public void deleteJournal(Long id) {
        if (!journalRepository.existsById(id)) {
            throw new RuntimeException("Journal non trouvé avec ID " + id);
        }

        if (journalRepository.hasEntries(id)) {
            throw new RuntimeException("Impossible de supprimer le journal. Il contient des écritures comptables.");
        }

        journalRepository.deleteById(id);
    }

    @Override
    public List<Journal> getAllJournals() {
        return journalRepository.findAll();
    }
    @Override
    public List<Journal> getJournalsByDossierId(Long dossierId) {
        return journalRepository.findByDossierId(dossierId);
    }

    @Override
    public Journal getJournalById(Long id) {
        return journalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Journal non trouvé avec ID " + id));
    }

    public Journal findByName(String name, Long dossierId) {
        return journalRepository.findByNameAndDossierId(name, dossierId)
                .orElseThrow(() -> new RuntimeException("Journal non trouvé avec le nom: " + name + " pour le dossier ID: " + dossierId));
    }
}
