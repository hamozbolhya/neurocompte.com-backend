package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Account;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Journal;
import com.pacioli.core.repositories.AccountRepository;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class AccountCreationService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserService userService;

    // Thread-safe locks for account creation per dossier
    private final Map<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

    /**
     * Thread-safe method to find or create account with retry logic
     */
    @Transactional
    public Account findOrCreateAccount(String accountNumber, Dossier dossier, Journal journal, String accountLabel) {
        String lockKey = dossier.getId() + "-" + accountNumber;
        ReentrantLock lock = accountLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());

        lock.lock();
        try {
            return findOrCreateAccountWithRetry(accountNumber, dossier, journal, accountLabel);
        } finally {
            lock.unlock();
            // Clean up lock if no longer needed
            accountLocks.remove(lockKey, lock);
        }
    }

    /**
     * Internal method with retry logic for concurrent account creation
     */
    private Account findOrCreateAccountWithRetry(String accountNumber, Dossier dossier, Journal journal, String accountLabel) {
        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // First, try to find existing account
                Account existingAccount = accountRepository.findByAccountAndDossierId(accountNumber, dossier.getId());
                if (existingAccount != null) {
                    log.debug("✅ Found existing account: {} for dossier {}", accountNumber, dossier.getId());

                    // Audit - compte existant trouvé (pas une création)
                    if (attempt > 1) {
                        auditService.logSuccess(userService.getCurrentUser(), "FOUND_EXISTING", "Account", existingAccount.getId(), accountNumber + " - " + accountLabel, null, Map.of("accountNumber", accountNumber, "dossierId", dossier.getId(), "dossierName", dossier.getName(), "journalId", journal != null ? journal.getId() : null, "journalName", journal != null ? journal.getName() : null, "afterConflict", true, "attempt", attempt));
                    }

                    return existingAccount;
                }

                // Account doesn't exist, create new one
                Account newAccount = new Account();
                newAccount.setAccount(accountNumber);
                newAccount.setLabel(accountLabel);
                newAccount.setDossier(dossier);
                newAccount.setJournal(journal);
                newAccount.setHasEntries(true);

                log.info("Creating new Account (attempt {}): {}", attempt, accountNumber);
                Account savedAccount = accountRepository.save(newAccount);
                log.info("✅ Successfully created account: {} for dossier {}", accountNumber, dossier.getId());

                // Audit - création de compte réussie
                auditService.logSuccess(userService.getCurrentUser(), "CREATE", "Account", savedAccount.getId(), accountNumber + " - " + accountLabel, null, Map.of("accountNumber", accountNumber, "accountLabel", accountLabel, "dossierId", dossier.getId(), "dossierName", dossier.getName(), "journalId", journal != null ? journal.getId() : null, "journalName", journal != null ? journal.getName() : null, "hasEntries", true, "attempt", attempt));

                return savedAccount;

            } catch (DataIntegrityViolationException e) {
                log.warn("🔄 Account creation conflict detected on attempt {} for account: {}", attempt, accountNumber);

                // Audit - conflit de création
                auditService.logFailure(userService.getCurrentUser(), "CREATE", "Account", null, accountNumber + " - " + accountLabel, "Data integrity violation on attempt " + attempt + ": " + e.getMessage());

                if (attempt == maxRetries) {
                    log.error("❌ Failed to create account after {} attempts: {}", maxRetries, accountNumber);

                    // Audit - échec final après tous les essais
                    auditService.logFailure(userService.getCurrentUser(), "CREATE", "Account", null, accountNumber + " - " + accountLabel, "Failed to create account after " + maxRetries + " attempts");

                    throw new RuntimeException("Failed to create account after " + maxRetries + " attempts: " + accountNumber, e);
                }

                // Wait a bit before retry
                try {
                    long waitTime = 50 * attempt;
                    Thread.sleep(waitTime); // Exponential backoff: 50ms, 100ms, 150ms
                    log.debug("Waited {}ms before retry {}", waitTime, attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();

                    // Audit - interruption
                    auditService.logFailure(userService.getCurrentUser(), "CREATE", "Account", null, accountNumber + " - " + accountLabel, "Thread interrupted while waiting to retry account creation");

                    throw new RuntimeException("Thread interrupted while waiting to retry account creation", ie);
                }

                // After waiting, try to find the account again (might have been created by another thread)
                Account retryAccount = accountRepository.findByAccountAndDossierId(accountNumber, dossier.getId());
                if (retryAccount != null) {
                    log.info("✅ Found account after conflict resolution: {}", accountNumber);

                    // Audit - trouvé après conflit
                    auditService.logSuccess(userService.getCurrentUser(), "FOUND_AFTER_CONFLICT", "Account", retryAccount.getId(), accountNumber + " - " + accountLabel, null, Map.of("accountNumber", accountNumber, "dossierId", dossier.getId(), "dossierName", dossier.getName(), "attempt", attempt, "resolution", "found after conflict"));

                    return retryAccount;
                }

                log.warn("Account still not found after conflict, retrying creation...");

            } catch (Exception e) {
                log.error("❌ Unexpected error creating account {}: {}", accountNumber, e.getMessage());

                // Audit - erreur inattendue
                auditService.logFailure(userService.getCurrentUser(), "CREATE", "Account", null, accountNumber + " - " + accountLabel, "Unexpected error on attempt " + attempt + ": " + e.getMessage());

                if (attempt == maxRetries) {
                    auditService.logFailure(userService.getCurrentUser(), "CREATE", "Account", null, accountNumber + " - " + accountLabel, "Failed after " + maxRetries + " attempts due to unexpected error");
                    throw new RuntimeException("Unexpected error creating account: " + accountNumber, e);
                }
            }
        }

        // Audit - échec final
        auditService.logFailure(userService.getCurrentUser(), "CREATE", "Account", null, accountNumber + " - " + accountLabel, "Failed to find or create account after all retries");

        throw new RuntimeException("Failed to find or create account: " + accountNumber);
    }
}