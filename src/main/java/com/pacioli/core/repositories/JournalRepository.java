package com.pacioli.core.repositories;

import com.pacioli.core.models.Journal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JournalRepository extends JpaRepository<Journal, Long> {
    List<Journal> findByDossierId(Long dossierId);

    boolean existsByNameAndDossierId(String name, Long dossierId);
    Optional<Journal> findByNameAndDossierId(String name, Long dossierId);
    @Query("SELECT COUNT(e) > 0 FROM Ecriture e WHERE e.journal.id = :journalId")
    boolean hasEntries(@Param("journalId") Long journalId);

    /*@Query("SELECT COUNT(j) > 0 FROM Journal j WHERE j.name = :name AND j.dossier.id = :dossierId")
    boolean existsByNameAndDossierId(@Param("name") String name, @Param("dossierId") Long dossierId);*/
}
