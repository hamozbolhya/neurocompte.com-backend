package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Account;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Journal;
import com.pacioli.core.repositories.AccountRepository;
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
                return savedAccount;

            } catch (DataIntegrityViolationException e) {
                log.warn("🔄 Account creation conflict detected on attempt {} for account: {}", attempt, accountNumber);

                if (attempt == maxRetries) {
                    log.error("❌ Failed to create account after {} attempts: {}", maxRetries, accountNumber);
                    throw new RuntimeException("Failed to create account after " + maxRetries + " attempts: " + accountNumber, e);
                }

                // Wait a bit before retry
                try {
                    Thread.sleep(50 * attempt); // Exponential backoff: 50ms, 100ms, 150ms
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted while waiting to retry account creation", ie);
                }

                // After waiting, try to find the account again (might have been created by another thread)
                Account retryAccount = accountRepository.findByAccountAndDossierId(accountNumber, dossier.getId());
                if (retryAccount != null) {
                    log.info("✅ Found account after conflict resolution: {}", accountNumber);
                    return retryAccount;
                }

                log.warn("Account still not found after conflict, retrying creation...");
            } catch (Exception e) {
                log.error("❌ Unexpected error creating account {}: {}", accountNumber, e.getMessage());
                if (attempt == maxRetries) {
                    throw new RuntimeException("Unexpected error creating account: " + accountNumber, e);
                }
            }
        }

        throw new RuntimeException("Failed to find or create account: " + accountNumber);
    }
}
