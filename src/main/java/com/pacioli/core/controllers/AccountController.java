package com.pacioli.core.controllers;


import com.pacioli.core.models.Account;
import com.pacioli.core.services.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // Get All Accounts
    @GetMapping
    public ResponseEntity<List<Account>> getAllAccounts() {
        List<Account> accounts = accountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/by-dossier/{dossierId}")
    public ResponseEntity<List<Account>> getAccountsByDossierId(@PathVariable Long dossierId) {
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
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        accountService.deleteAccount(id);
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
}
