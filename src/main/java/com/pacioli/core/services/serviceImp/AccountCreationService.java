package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Account;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Journal;
import com.pacioli.core.repositories.AccountRepository;
import com.pacioli.core.repositories.JournalRepository;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class AccountCreationService {

    private final AccountRepository accountRepository;
    private final JournalRepository journalRepository;
    private final AuditService auditService;
    private final UserService userService;
    private final TransactionTemplate requiresNewTemplate;

    // Thread-safe locks for account creation per dossier
    private final Map<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

    public AccountCreationService(AccountRepository accountRepository,
                                  JournalRepository journalRepository,
                                  @Lazy AuditService auditService,
                                  UserService userService,
                                  @NonNull PlatformTransactionManager transactionManager) {
        this.accountRepository = accountRepository;
        this.journalRepository = journalRepository;
        this.auditService = auditService;
        this.userService = userService;
        this.requiresNewTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Thread-safe method to find or create account with retry logic.
     * Each attempt runs in its own transaction so a failed insert (e.g. invalid FK) does not poison retries.
     */
    public Account findOrCreateAccount(String accountNumber, Dossier dossier, Journal journal, String accountLabel) {
        String lockKey = dossier.getId() + "-" + accountNumber;
        ReentrantLock lock = accountLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());

        lock.lock();
        try {
            return findOrCreateAccountWithRetry(accountNumber, dossier, journal, accountLabel);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Internal method with retry logic for concurrent account creation
     */
    private Account findOrCreateAccountWithRetry(String accountNumber, Dossier dossier, Journal journal, String accountLabel) {
        int maxRetries = 3;
        Journal journalForInsert = resolveJournalForInsert(journal);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            final int currentAttempt = attempt;
            try {
                return requiresNewTemplate.execute(status ->
                        createOrFindInNewTransaction(accountNumber, dossier, journalForInsert, accountLabel, currentAttempt, maxRetries));
            } catch (DataIntegrityViolationException e) {
                log.warn("🔄 Account creation conflict detected on attempt {} for account: {}", attempt, accountNumber);

                auditService.logFailure(userService.getCurrentUser(), "CREATE", "Account", null, accountNumber + " - " + accountLabel, "Data integrity violation on attempt " + attempt + ": " + e.getMessage());

                if (attempt == maxRetries) {
                    log.error("❌ Failed to create account after {} attempts: {}", maxRetries, accountNumber);

                    auditService.logFailure(userService.getCurrentUser(), "CREATE", "Account", null, accountNumber + " - " + accountLabel, "Failed to create account after " + maxRetries + " attempts");

                    throw new RuntimeException("Failed to create account after " + maxRetries + " attempts: " + accountNumber, e);
                }

                try {
                    long waitTime = 50L * attempt;
                    Thread.sleep(waitTime);
                    log.debug("Waited {}ms before retry {}", waitTime, attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();

                    auditService.logFailure(userService.getCurrentUser(), "CREATE", "Account", null, accountNumber + " - " + accountLabel, "Thread interrupted while waiting to retry account creation");

                    throw new RuntimeException("Thread interrupted while waiting to retry account creation", ie);
                }

                Account retryAccount = requiresNewTemplate.execute(status ->
                        accountRepository.findByAccountAndDossierId(accountNumber, dossier.getId()));
                if (retryAccount != null) {
                    log.info("✅ Found account after conflict resolution: {}", accountNumber);

                    auditService.logSuccess(userService.getCurrentUser(), "FOUND_AFTER_CONFLICT", "Account", retryAccount.getId(), accountNumber + " - " + accountLabel, null, Map.of("accountNumber", accountNumber, "dossierId", dossier.getId(), "dossierName", dossier.getName(), "attempt", attempt, "resolution", "found after conflict"));

                    return retryAccount;
                }

                log.warn("Account still not found after conflict, retrying creation...");

            } catch (Exception e) {
                log.error("❌ Unexpected error creating account {}: {}", accountNumber, e.getMessage());

                auditService.logFailure(userService.getCurrentUser(), "CREATE", "Account", null, accountNumber + " - " + accountLabel, "Unexpected error on attempt " + attempt + ": " + e.getMessage());

                if (attempt == maxRetries) {
                    auditService.logFailure(userService.getCurrentUser(), "CREATE", "Account", null, accountNumber + " - " + accountLabel, "Failed after " + maxRetries + " attempts due to unexpected error");
                    throw new RuntimeException("Unexpected error creating account: " + accountNumber, e);
                }
            }
        }

        auditService.logFailure(userService.getCurrentUser(), "CREATE", "Account", null, accountNumber + " - " + accountLabel, "Failed to find or create account after all retries");

        throw new RuntimeException("Failed to find or create account: " + accountNumber);
    }

    private Journal resolveJournalForInsert(Journal journal) {
        if (journal == null) {
            return null;
        }
        Long journalId = journal.getId();
        if (journalId == null) {
            return journal;
        }
        if (journalRepository.existsById(Objects.requireNonNull(journalId))) {
            return journal;
        }
        log.warn("Journal id {} not found in database; creating account without journal reference", journalId);
        return null;
    }

    private Account createOrFindInNewTransaction(String accountNumber, Dossier dossier, Journal journal,
                                                 String accountLabel, int attempt, int maxRetries) {
        Account existingAccount = accountRepository.findByAccountAndDossierId(accountNumber, dossier.getId());
        if (existingAccount != null) {
            log.debug("✅ Found existing account: {} for dossier {}", accountNumber, dossier.getId());

            if (attempt > 1) {
                auditService.logSuccess(userService.getCurrentUser(), "FOUND_EXISTING", "Account", existingAccount.getId(), accountNumber + " - " + accountLabel, null, Map.of("accountNumber", accountNumber, "dossierId", dossier.getId(), "dossierName", dossier.getName(), "journalId", journal != null ? journal.getId() : null, "journalName", journal != null ? journal.getName() : null, "afterConflict", true, "attempt", attempt));
            }

            return existingAccount;
        }

        Account newAccount = new Account();
        newAccount.setAccount(accountNumber);
        newAccount.setLabel(accountLabel);
        newAccount.setDossier(dossier);
        newAccount.setJournal(journal);
        newAccount.setHasEntries(true);

        log.info("Creating new Account (attempt {}): {}", attempt, accountNumber);
        Account savedAccount = accountRepository.saveAndFlush(newAccount);
        log.info("✅ Successfully created account: {} for dossier {}", accountNumber, dossier.getId());

        auditService.logSuccess(userService.getCurrentUser(), "CREATE", "Account", savedAccount.getId(), accountNumber + " - " + accountLabel, null, Map.of("accountNumber", accountNumber, "accountLabel", accountLabel, "dossierId", dossier.getId(), "dossierName", dossier.getName(), "journalId", journal != null ? journal.getId() : null, "journalName", journal != null ? journal.getName() : null, "hasEntries", true, "attempt", attempt));

        return savedAccount;
    }
}
