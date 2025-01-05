package com.pacioli.core.repositories;


import com.pacioli.core.models.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByJournalId(Long journalId); // Find accounts by journal ID

   @Query("SELECT a FROM Account a WHERE a.dossier.id = :dossierId")
    List<Account> findByDossierId(@Param("dossierId") Long dossierId);

    @Query("SELECT a FROM Account a WHERE a.account = :account AND a.dossier.id = :dossierId")
    Account findByAccountAndDossierId(@Param("account") String account, @Param("dossierId") Long dossierId);


    //List<Account> findByDossierId(Long dossierId);


    @Query("SELECT a FROM Account a WHERE a.account IN :accounts AND a.dossier.id = :dossierId")
    List<Account> findByAccountInAndDossierId(@Param("accounts") List<String> accounts, @Param("dossierId") Long dossierId);

}
