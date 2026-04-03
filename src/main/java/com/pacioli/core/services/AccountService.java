package com.pacioli.core.services;

import com.pacioli.core.models.Account;
import org.springframework.lang.NonNull;

import java.util.List;

public interface AccountService {
    Account findById(@NonNull Long id);
    List<Account> getAllAccounts();
    Account createAccount(@NonNull Account account);
    Account updateAccount(@NonNull Long id, @NonNull Account updatedAccount);
    void deleteAccounts(@NonNull List<Long> ids);
    Account findAccountById(@NonNull Long id);
    List<Account> findAccountsByJournalId(Long journalId);

    List<Account> getAccountsByDossierId(Long dossierId);
}
