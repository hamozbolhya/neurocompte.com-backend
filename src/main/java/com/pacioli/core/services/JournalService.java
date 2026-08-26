package com.pacioli.core.services;

import com.pacioli.core.models.Journal;
import org.springframework.lang.NonNull;

import java.util.List;

public interface JournalService {
    Journal addJournal(@NonNull Journal journal, @NonNull Long dossierId);
    Journal updateJournal(@NonNull Long id, @NonNull Journal updatedJournal);
    void deleteJournal(@NonNull Long id);
    List<Journal> getAllJournals();
    List<Journal> getJournalsByDossierId(@NonNull Long dossierId);
    Journal getJournalById(@NonNull Long id);
    Journal findByName(String name, @NonNull Long dossierId);
}
