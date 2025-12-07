package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Account;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Journal;
import com.pacioli.core.repositories.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
public class AccountCreationService {

    @Autowired
    private AccountRepository accountRepository;

    /**
     * Thread-safe method to find or create account
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Account findOrCreateAccount(String accountNumber, Dossier dossier,
                                       Journal journal, String accountLabel) {

        // Validate inputs
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Account number cannot be empty");
        }
        if (dossier == null || dossier.getId() == null) {
            throw new IllegalArgumentException("Dossier is required");
        }

        // Clean inputs
        accountNumber = accountNumber.trim();
        Long dossierId = dossier.getId();

        log.debug("Processing account: {} for dossier: {}", accountNumber, dossierId);

        // 1. First, try to find existing account
        Account existingAccount = accountRepository.findByAccountAndDossierId(accountNumber, dossierId);
        if (existingAccount != null) {
            log.debug("✅ Found existing account: {} for dossier {}", accountNumber, dossierId);
            return existingAccount;
        }

        log.info("🔄 Creating account: {} for dossier {}", accountNumber, dossierId);

        // 2. Try UPSERT with retry logic
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Long journalId = journal != null ? journal.getId() : null;
                String label = accountLabel != null ? accountLabel : accountNumber;

                // Execute UPSERT (returns int for rows affected)
                int rowsAffected = accountRepository.upsertAccount(
                        accountNumber, dossierId, journalId, label, false
                );

                log.debug("UPSERT affected {} rows for account {}", rowsAffected, accountNumber);

                // Now find the account (either newly created or existing)
                Account account = accountRepository.findByAccountAndDossierId(accountNumber, dossierId);
                if (account != null) {
                    log.info("✅ Account created/found: {} (ID: {})", accountNumber, account.getId());
                    return account;
                } else {
                    log.warn("Account not found after UPSERT, attempt {}", attempt);
                }

            } catch (DataIntegrityViolationException e) {
                // Concurrent creation - check if another thread created it
                log.debug("Data integrity violation, checking if account exists (attempt {}): {}",
                        attempt, accountNumber);

                existingAccount = accountRepository.findByAccountAndDossierId(accountNumber, dossierId);
                if (existingAccount != null) {
                    log.info("✅ Account was created by another thread: {}", accountNumber);
                    return existingAccount;
                }

            } catch (Exception e) {
                log.error("❌ Error creating account {} (attempt {}): {}",
                        accountNumber, attempt, e.getMessage());

                if (attempt == maxRetries) {
                    // Last attempt failed, try one final check
                    existingAccount = accountRepository.findByAccountAndDossierId(accountNumber, dossierId);
                    if (existingAccount != null) {
                        log.info("✅ Found account in final fallback: {}", accountNumber);
                        return existingAccount;
                    }

                    throw new AccountCreationException("Failed to create account after " + maxRetries + " attempts: " + accountNumber, e);
                }
            }

            // Wait before retrying (exponential backoff)
            if (attempt < maxRetries) {
                try {
                    long waitTime = 100L * (1L << (attempt - 1)); // 100, 200, 400 ms
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AccountCreationException("Thread interrupted while creating account: " + accountNumber, ie);
                }
            }
        }

        throw new AccountCreationException("Unexpected error creating account: " + accountNumber);
    }

    /**
     * Alternative implementation using separate native UPSERT and find
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Account findOrCreateAccountAlternative(String accountNumber, Dossier dossier,
                                                  Journal journal, String accountLabel) {

        // Validate and clean inputs
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Account number cannot be empty");
        }
        if (dossier == null || dossier.getId() == null) {
            throw new IllegalArgumentException("Dossier is required");
        }

        accountNumber = accountNumber.trim();
        Long dossierId = dossier.getId();

        // 1. Try to find existing account first
        Account existingAccount = accountRepository.findByAccountAndDossierId(accountNumber, dossierId);
        if (existingAccount != null) {
            log.debug("Found existing account: {} for dossier {}", accountNumber, dossierId);
            return existingAccount;
        }

        log.info("Creating new account: {} for dossier {}", accountNumber, dossierId);

        try {
            // 2. Execute UPSERT (native query returns void)
            Long journalId = journal != null ? journal.getId() : null;
            String label = accountLabel != null ? accountLabel : accountNumber;

            accountRepository.upsertAccountNative(
                    accountNumber, dossierId, journalId, label, false
            );

            // 3. Find the account after UPSERT
            Account account = accountRepository.findByAccountAndDossierId(accountNumber, dossierId);
            if (account != null) {
                log.info("Account created successfully: {} (ID: {})", accountNumber, account.getId());
                return account;
            }

            throw new AccountCreationException("Account not found after UPSERT: " + accountNumber);

        } catch (DataIntegrityViolationException e) {
            // Concurrent creation - check if account exists now
            log.debug("Concurrent creation detected, checking for account: {}", accountNumber);
            existingAccount = accountRepository.findByAccountAndDossierId(accountNumber, dossierId);
            if (existingAccount != null) {
                log.info("Account was created concurrently: {}", accountNumber);
                return existingAccount;
            }
            throw new AccountCreationException("Failed to create account due to data integrity violation: " + accountNumber, e);

        } catch (Exception e) {
            log.error("Error creating account {}: {}", accountNumber, e.getMessage());

            // Final fallback check
            existingAccount = accountRepository.findByAccountAndDossierId(accountNumber, dossierId);
            if (existingAccount != null) {
                log.info("Found account in fallback: {}", accountNumber);
                return existingAccount;
            }

            throw new AccountCreationException("Failed to create account: " + accountNumber, e);
        }
    }

    /**
     * Simple version without retry logic
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Account findOrCreateAccountSimple(String accountNumber, Dossier dossier,
                                             Journal journal, String accountLabel) {

        // Validate
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Account number cannot be empty");
        }
        if (dossier == null || dossier.getId() == null) {
            throw new IllegalArgumentException("Dossier is required");
        }

        accountNumber = accountNumber.trim();
        Long dossierId = dossier.getId();

        // Try to find first
        Account account = accountRepository.findByAccountAndDossierId(accountNumber, dossierId);
        if (account != null) {
            return account;
        }

        log.info("Creating account: {} for dossier {}", accountNumber, dossierId);

        try {
            // Execute UPSERT
            Long journalId = journal != null ? journal.getId() : null;
            String label = accountLabel != null ? accountLabel : accountNumber;

            accountRepository.upsertAccountNative(
                    accountNumber, dossierId, journalId, label, false
            );

            // Find after UPSERT
            account = accountRepository.findByAccountAndDossierId(accountNumber, dossierId);
            if (account != null) {
                return account;
            }

            throw new AccountCreationException("Account not found after UPSERT: " + accountNumber);

        } catch (Exception e) {
            log.error("UPSERT failed, checking for account: {}", accountNumber);

            // Check one more time
            account = accountRepository.findByAccountAndDossierId(accountNumber, dossierId);
            if (account != null) {
                return account;
            }

            throw new AccountCreationException("Failed to create account: " + accountNumber, e);
        }
    }

    /**
     * Custom exception for account creation failures
     */
    public static class AccountCreationException extends RuntimeException {
        public AccountCreationException(String message) {
            super(message);
        }

        public AccountCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}