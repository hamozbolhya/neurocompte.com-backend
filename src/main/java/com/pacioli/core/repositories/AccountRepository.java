package com.pacioli.core.repositories;

import com.pacioli.core.models.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

 List<Account> findByJournalId(Long journalId);

 @Query("SELECT a FROM Account a WHERE a.dossier.id = :dossierId")
 List<Account> findByDossierId(@Param("dossierId") Long dossierId);

 @Query("SELECT a FROM Account a WHERE a.account = :account AND a.dossier.id = :dossierId")
 Account findByAccountAndDossierId(@Param("account") String account,
                                   @Param("dossierId") Long dossierId);

 /**
  * UPSERT method 1: Returns void, we'll handle ID differently
  */
 @Modifying
 @Transactional
 @Query(value = "INSERT INTO account (account, dossier_id, journal_id, label, has_entries) " +
         "VALUES (:account, :dossierId, :journalId, :label, :hasEntries) " +
         "ON CONFLICT (account, dossier_id) DO UPDATE SET " +
         "label = EXCLUDED.label, " +
         "journal_id = EXCLUDED.journal_id, " +
         "has_entries = EXCLUDED.has_entries",
         nativeQuery = true)
 void upsertAccountNative(@Param("account") String account,
                          @Param("dossierId") Long dossierId,
                          @Param("journalId") Long journalId,
                          @Param("label") String label,
                          @Param("hasEntries") Boolean hasEntries);

 /**
  * Alternative: Use a simple query to get the ID after UPSERT
  */
 @Query(value = "SELECT id FROM account WHERE account = :account AND dossier_id = :dossierId",
         nativeQuery = true)
 Optional<Long> findAccountId(@Param("account") String account,
                              @Param("dossierId") Long dossierId);

 /**
  * UPSERT with CTE that returns void (Spring Data JPA compliant)
  */
 @Modifying
 @Transactional
 @Query(value = """
        WITH upsert AS (
          INSERT INTO account (account, dossier_id, journal_id, label, has_entries) 
          VALUES (:account, :dossierId, :journalId, :label, :hasEntries) 
          ON CONFLICT (account, dossier_id) DO UPDATE SET 
            label = EXCLUDED.label, 
            journal_id = EXCLUDED.journal_id, 
            has_entries = EXCLUDED.has_entries
          RETURNING id
        ) 
        SELECT 1
        """, nativeQuery = true)
 int upsertAccount(@Param("account") String account,
                   @Param("dossierId") Long dossierId,
                   @Param("journalId") Long journalId,
                   @Param("label") String label,
                   @Param("hasEntries") Boolean hasEntries);
}