package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Account;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Journal;
import com.pacioli.core.repositories.AccountRepository;
import com.pacioli.core.services.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountCreationService accountCreationService;

    public AccountServiceImpl(AccountRepository accountRepository,
                              AccountCreationService accountCreationService) {
        this.accountRepository = accountRepository;
        this.accountCreationService = accountCreationService;
    }

    @Override
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    @Override
    public Account findById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + id));
    }

    @Override
    public List<Account> getAccountsByDossierId(Long dossierId) {
        return accountRepository.findByDossierId(dossierId);
    }

    @Override
    public Account createAccount(Account account) {
        // ⚠️ CRITICAL FIX: Use AccountCreationService instead of direct save
        if (account.getDossier() == null) {
            throw new IllegalArgumentException("Account must have a dossier");
        }

        return accountCreationService.findOrCreateAccount(
                account.getAccount(),
                account.getDossier(),
                account.getJournal(),
                account.getLabel()
        );
    }

    @Override
    public Account updateAccount(Long id, Account updatedAccount) {
        return accountRepository.findById(id).map(existingAccount -> {
            // Only update fields that don't affect uniqueness
            existingAccount.setLabel(updatedAccount.getLabel());
            existingAccount.setHasEntries(updatedAccount.getHasEntries());
            existingAccount.setJournal(updatedAccount.getJournal());

            // ⚠️ DO NOT update account number or dossier - they're part of unique constraint
            // If these need to change, delete and recreate via AccountCreationService

            return accountRepository.save(existingAccount);
        }).orElseThrow(() -> new RuntimeException("Account not found with ID: " + id));
    }

    @Override
    public void deleteAccounts(List<Long> ids) {
        for (Long id : ids) {
            if (!accountRepository.existsById(id)) {
                throw new RuntimeException("Account not found with ID: " + id);
            }
        }
        accountRepository.deleteAllById(ids);
    }

    @Override
    public Account findAccountById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found with ID: " + id));
    }

    @Override
    public List<Account> findAccountsByJournalId(Long journalId) {
        return accountRepository.findByJournalId(journalId);
    }
}