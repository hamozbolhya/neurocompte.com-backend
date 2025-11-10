package com.pacioli.core.controllers;


import com.pacioli.core.models.Account;
import com.pacioli.core.repositories.UserRepository;
import com.pacioli.core.services.AccountService;
import com.pacioli.core.services.DossierService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private  AccountService accountService;
    @Autowired
    private DossierService dossierService;

    // Get All Accounts
    @GetMapping
    public ResponseEntity<List<Account>> getAllAccounts() {
        List<Account> accounts = accountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/by-dossier/{dossierId}")
    public ResponseEntity<List<Account>> getAccountsByDossierId(@PathVariable Long dossierId, @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        UUID userId = extractUserIdFromPrincipal(principal);

        // ✅ SECURITY CHECK: Verify user has access to this dossier
        if (!dossierService.userHasAccessToDossier(userId, dossierId)) {
            log.error("User {} attempted to access pieces from unauthorized dossier {}", principal.getUsername(), dossierId);
            throw new SecurityException("User cannot access this dossier");
        }

        List<Account> accounts = accountService.getAccountsByDossierId(dossierId);
        return ResponseEntity.ok(accounts);
    }
    // Create Account
    @PostMapping
    public ResponseEntity<Account> createAccount(@RequestBody Account account) {
        Account createdAccount = accountService.createAccount(account);
        return ResponseEntity.ok(createdAccount);
    }

    // Update Account
    @PutMapping("/{id}")
    public ResponseEntity<Account> updateAccount(
            @PathVariable Long id,
            @RequestBody Account updatedAccount) {
        Account account = accountService.updateAccount(id, updatedAccount);
        return ResponseEntity.ok(account);
    }

    // Delete Account
    @DeleteMapping
    public ResponseEntity<Void> deleteAccounts(@RequestBody List<Long> ids) {
        accountService.deleteAccounts(ids);
        return ResponseEntity.noContent().build();
    }

    // Find Account by ID
    @GetMapping("/{id}")
    public ResponseEntity<Account> findAccountById(@PathVariable Long id) {
        Account account = accountService.findAccountById(id);
        return ResponseEntity.ok(account);
    }

    // Find Accounts by Journal ID
    @GetMapping("/journal/{journalId}")
    public ResponseEntity<List<Account>> findAccountsByJournalId(@PathVariable Long journalId) {
        List<Account> accounts = accountService.findAccountsByJournalId(journalId);
        return ResponseEntity.ok(accounts);
    }

    private UUID extractUserIdFromPrincipal(org.springframework.security.core.userdetails.User principal) {
        if (principal == null) {
            log.error("Principal is null - user not authenticated");
            throw new SecurityException("User not authenticated");
        }

        String username = principal.getUsername();
        log.debug("Extracting user ID for username: {}", username);

        try {
            // Look up the user by username to get the UUID
            com.pacioli.core.models.User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        log.error("User not found for username: {}", username);
                        return new SecurityException("User not found");
                    });

            if (user.getId() == null) {
                log.error("User ID is null for user: {}", username);
                throw new SecurityException("User ID not found");
            }

            log.debug("Successfully extracted user ID: {} for user: {}", user.getId(), username);
            return user.getId();

        } catch (SecurityException e) {
            // Re-throw security exceptions
            throw e;
        } catch (Exception e) {
            log.error("Error extracting user ID for username {}: {}", username, e.getMessage(), e);
            throw new SecurityException("Error extracting user information: " + e.getMessage());
        }
    }
}
