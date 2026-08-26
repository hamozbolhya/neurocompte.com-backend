package com.pacioli.core.controllers;

import com.pacioli.core.models.AuditLog;
import com.pacioli.core.repositories.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@CrossOrigin(origins = "*")
public class AuditController {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @GetMapping
    @PreAuthorize("hasRole('PACIOLI')")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Long userCabinetId,
            @RequestParam(required = false) Long targetCabinetId,
            @RequestParam(required = false) Long dossierId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // ✅ Créer un Pageable SANS tri (le tri est déjà dans la requête native)
        Pageable pageable = PageRequest.of(page, size);

        // Ajuster les dates pour inclure toute la journée
        if (startDate != null) {
            startDate = startDate.with(LocalTime.MIN);
        }
        if (endDate != null) {
            endDate = endDate.with(LocalTime.MAX);
        }

        Page<AuditLog> logs = auditLogRepository.searchAuditLogsNative(
                userId, userCabinetId, targetCabinetId, dossierId, entityType, action,
                startDate, endDate, pageable);

        return ResponseEntity.ok(logs);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('PACIOLI')")
    public ResponseEntity<Page<AuditLog>> getUserLogs(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable));
    }

    // ✅ VERSION CORRIGÉE - Pour les logs par cabinet utilisateur
    @GetMapping("/user-cabinet/{userCabinetId}")
    @PreAuthorize("hasRole('PACIOLI')")
    public ResponseEntity<Page<AuditLog>> getUserCabinetLogs(
            @PathVariable Long userCabinetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(auditLogRepository.findByUserCabinetIdOrderByCreatedAtDesc(userCabinetId, pageable));
    }

    // ✅ VERSION CORRIGÉE - Pour les logs par cabinet cible
    @GetMapping("/target-cabinet/{targetCabinetId}")
    @PreAuthorize("hasRole('PACIOLI')")
    public ResponseEntity<Page<AuditLog>> getTargetCabinetLogs(
            @PathVariable Long targetCabinetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(auditLogRepository.findByTargetCabinetIdOrderByCreatedAtDesc(targetCabinetId, pageable));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasRole('PACIOLI')")
    public ResponseEntity<Page<AuditLog>> getEntityLogs(
            @PathVariable String entityType,
            @PathVariable String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                entityType, entityId, pageable));
    }
}