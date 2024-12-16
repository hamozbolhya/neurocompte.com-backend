package com.pacioli.core.services;

import com.pacioli.core.models.Journal;

import java.util.List;

public interface JournalService {
    Journal addJournal(Journal journal, Long dossierId);
    Journal updateJournal(Long id, Journal updatedJournal);
    void deleteJournal(Long id);
    List<Journal> getAllJournals();
    List<Journal> getJournalsByDossierId(Long dossierId);
    Journal getJournalById(Long id);
    Journal findByName(String name, Long dossierId);
}
