package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Account;
import com.pacioli.core.repositories.AccountRepository;
import com.pacioli.core.services.AccountService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    public AccountServiceImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    @Override
    public List<Account> getAccountsByDossierId(Long dossierId) {
        return accountRepository.findByDossierId(dossierId);
    }

    @Override
    public Account createAccount(Account account) {
        return accountRepository.save(account);
    }

    @Override
    public Account updateAccount(Long id, Account updatedAccount) {
        return accountRepository.findById(id).map(existingAccount -> {
            existingAccount.setLabel(updatedAccount.getLabel());
            existingAccount.setAccount(updatedAccount.getAccount());
            existingAccount.setHasEntries(updatedAccount.getHasEntries());
            existingAccount.setJournal(updatedAccount.getJournal());
            return accountRepository.save(existingAccount);
        }).orElseThrow(() -> new RuntimeException("Account not found with ID: " + id));
    }

    @Override
    public void deleteAccount(Long id) {
        if (!accountRepository.existsById(id)) {
            throw new RuntimeException("Account not found with ID: " + id);
        }
        accountRepository.deleteById(id);
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
