package com.pacioli.core.services;

import com.pacioli.core.models.Account;

import java.util.List;

public interface AccountService {
    Account findById(Long id);
    List<Account> getAllAccounts();
    Account createAccount(Account account);
    Account updateAccount(Long id, Account updatedAccount);
    void deleteAccounts(List<Long> ids);
    Account findAccountById(Long id);
    List<Account> findAccountsByJournalId(Long journalId);

    List<Account> getAccountsByDossierId(Long dossierId);
}
