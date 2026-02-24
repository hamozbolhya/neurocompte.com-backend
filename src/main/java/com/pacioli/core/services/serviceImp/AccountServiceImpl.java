package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Account;
import com.pacioli.core.repositories.AccountRepository;
import com.pacioli.core.services.AccountService;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AuditService auditService;
    private final UserService userService;

    public AccountServiceImpl(AccountRepository accountRepository, AuditService auditService, UserService userService) {
        this.accountRepository = accountRepository;
        this.auditService = auditService;
        this.userService = userService;
    }

    // ✅ GET - PAS D'AUDIT (comme demandé)
    @Override
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    // ✅ GET - PAS D'AUDIT
    @Override
    public Account findById(Long id) {
        return accountRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + id));
    }

    // ✅ GET - PAS D'AUDIT
    @Override
    public List<Account> getAccountsByDossierId(Long dossierId) {
        return accountRepository.findByDossierId(dossierId);
    }

    // ✅ CREATE - AVEC AUDIT
    @Override
    @Transactional
    public Account createAccount(Account account) {
        Account savedAccount = accountRepository.save(account);

        // Audit succès création
        auditService.logSuccess(userService.getCurrentUser(), "CREATE", "Account", savedAccount.getId(), savedAccount.getAccount() + " - " + savedAccount.getLabel(), null, savedAccount);

        return savedAccount;
    }

    // ✅ UPDATE - AVEC AUDIT
    @Override
    @Transactional
    public Account updateAccount(Long id, Account updatedAccount) {
        return accountRepository.findById(id).map(existingAccount -> {
            // Sauvegarder l'ancien état pour l'audit
            Account oldAccount = new Account();
            oldAccount.setId(existingAccount.getId());
            oldAccount.setLabel(existingAccount.getLabel());
            oldAccount.setAccount(existingAccount.getAccount());
            oldAccount.setHasEntries(existingAccount.getHasEntries());
            oldAccount.setJournal(existingAccount.getJournal());
            oldAccount.setDossier(existingAccount.getDossier());

            // Mise à jour
            existingAccount.setLabel(updatedAccount.getLabel());
            existingAccount.setAccount(updatedAccount.getAccount());
            existingAccount.setHasEntries(updatedAccount.getHasEntries());
            existingAccount.setJournal(updatedAccount.getJournal());

            Account savedAccount = accountRepository.save(existingAccount);

            // Audit succès mise à jour
            auditService.logSuccess(userService.getCurrentUser(), "UPDATE", "Account", id, savedAccount.getAccount() + " - " + savedAccount.getLabel(), oldAccount, savedAccount);

            return savedAccount;
        }).orElseThrow(() -> {
            // Audit échec mise à jour
            auditService.logFailure(userService.getCurrentUser(), "UPDATE", "Account", id, "Account-" + id, "Account not found with ID: " + id);
            return new RuntimeException("Account not found with ID: " + id);
        });
    }

    // ✅ DELETE - AVEC AUDIT
    @Override
    @Transactional
    public void deleteAccounts(List<Long> ids) {
        // Valider que tous les comptes existent avant suppression
        for (Long id : ids) {
            if (!accountRepository.existsById(id)) {
                // Audit échec suppression
                auditService.logFailure(userService.getCurrentUser(), "DELETE", "Account", id, "Account-" + id, "Account not found with ID: " + id);
                throw new RuntimeException("Account not found with ID: " + id);
            }
        }

        // Récupérer les comptes pour les détails d'audit avant suppression
        List<Account> accountsToDelete = accountRepository.findAllById(ids);

        // Audit avant suppression pour chaque compte
        for (Account account : accountsToDelete) {
            auditService.logSuccess(userService.getCurrentUser(), "DELETE", "Account", account.getId(), account.getAccount() + " - " + account.getLabel(), account, null);
        }

        accountRepository.deleteAllById(ids);
    }

    // ✅ GET - PAS D'AUDIT
    @Override
    public Account findAccountById(Long id) {
        return accountRepository.findById(id).orElseThrow(() -> new RuntimeException("Account not found with ID: " + id));
    }

    // ✅ GET - PAS D'AUDIT
    @Override
    public List<Account> findAccountsByJournalId(Long journalId) {
        return accountRepository.findByJournalId(journalId);
    }
}