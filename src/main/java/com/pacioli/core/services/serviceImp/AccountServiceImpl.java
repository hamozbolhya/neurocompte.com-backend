package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Account;
import com.pacioli.core.repositories.AccountRepository;
import com.pacioli.core.services.AccountService;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.UserService;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

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

    // ✅ Méthode utilitaire pour récupérer le cabinet cible à partir d'un compte
    private Long getTargetCabinetId(Account account) {
        if (account != null && account.getDossier() != null && account.getDossier().getCabinet() != null) {
            return account.getDossier().getCabinet().getId();
        }
        return null;
    }

    private String getTargetCabinetName(Account account) {
        if (account != null && account.getDossier() != null && account.getDossier().getCabinet() != null) {
            return account.getDossier().getCabinet().getName();
        }
        return null;
    }

    // ✅ GET - PAS D'AUDIT (inchangé)
    @Override
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    // ✅ GET - PAS D'AUDIT
    @Override
    public Account findById(@NonNull Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + id));
    }

    // ✅ GET - PAS D'AUDIT
    @Override
    public List<Account> getAccountsByDossierId(Long dossierId) {
        return accountRepository.findByDossierId(dossierId);
    }

    // ✅ CREATE - AVEC AUDIT
    @Override
    @Transactional
    public Account createAccount(@NonNull Account account) {
        Account savedAccount = accountRepository.save(account);

        // ✅ Récupérer le cabinet cible
        Long targetCabinetId = getTargetCabinetId(savedAccount);
        String targetCabinetName = getTargetCabinetName(savedAccount);

        // Audit succès création avec cabinet cible
        auditService.logSuccessWithTargetCabinet(
                userService.getCurrentUser(),
                "CREATE",
                "Account",
                savedAccount.getId(),
                savedAccount.getAccount() + " - " + savedAccount.getLabel(),
                null,
                savedAccount,
                targetCabinetId,
                targetCabinetName
        );

        return savedAccount;
    }

    // ✅ UPDATE - AVEC AUDIT
    @Override
    @Transactional
    public Account updateAccount(@NonNull Long id, @NonNull Account updatedAccount) {
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

            // ✅ Récupérer le cabinet cible
            Long targetCabinetId = getTargetCabinetId(savedAccount);
            String targetCabinetName = getTargetCabinetName(savedAccount);

            // Audit succès mise à jour avec cabinet cible
            auditService.logSuccessWithTargetCabinet(
                    userService.getCurrentUser(),
                    "UPDATE",
                    "Account",
                    id,
                    savedAccount.getAccount() + " - " + savedAccount.getLabel(),
                    oldAccount,
                    savedAccount,
                    targetCabinetId,
                    targetCabinetName
            );

            return savedAccount;
        }).orElseThrow(() -> {
            // Audit échec mise à jour (pas de cabinet cible car compte non trouvé)
            auditService.logFailure(
                    userService.getCurrentUser(),
                    "UPDATE",
                    "Account",
                    id,
                    "Account-" + id,
                    "Account not found with ID: " + id
            );
            return new RuntimeException("Account not found with ID: " + id);
        });
    }

    // ✅ DELETE - AVEC AUDIT
    @Override
    @Transactional
    public void deleteAccounts(@NonNull List<Long> ids) {
        // Valider que tous les comptes existent avant suppression
        for (Long id : ids) {
            Long nid = Objects.requireNonNull(id, "null id in ids");
            if (!accountRepository.existsById(nid)) {
                // Audit échec suppression
                auditService.logFailure(
                        userService.getCurrentUser(),
                        "DELETE",
                        "Account",
                        nid,
                        "Account-" + nid,
                        "Account not found with ID: " + nid
                );
                throw new RuntimeException("Account not found with ID: " + nid);
            }
        }

        // Récupérer les comptes pour les détails d'audit avant suppression
        List<Account> accountsToDelete = accountRepository.findAllById(ids);

        // Audit avant suppression pour chaque compte avec cabinet cible
        for (Account account : accountsToDelete) {
            Long targetCabinetId = getTargetCabinetId(account);
            String targetCabinetName = getTargetCabinetName(account);

            auditService.logSuccessWithTargetCabinet(
                    userService.getCurrentUser(),
                    "DELETE",
                    "Account",
                    account.getId(),
                    account.getAccount() + " - " + account.getLabel(),
                    account,
                    null,
                    targetCabinetId,
                    targetCabinetName
            );
        }

        accountRepository.deleteAllById(ids);
    }

    // ✅ GET - PAS D'AUDIT
    @Override
    public Account findAccountById(@NonNull Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found with ID: " + id));
    }

    // ✅ GET - PAS D'AUDIT
    @Override
    public List<Account> findAccountsByJournalId(Long journalId) {
        return accountRepository.findByJournalId(journalId);
    }
}