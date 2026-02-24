package com.pacioli.core.repositories;

import com.pacioli.core.models.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Recherche par utilisateur
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // Recherche par cabinet
    Page<AuditLog> findByCabinetIdOrderByCreatedAtDesc(Long cabinetId, Pageable pageable);

    // Recherche par entité
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId, Pageable pageable);

    @Query(value = "SELECT * FROM audit_logs a WHERE " +
            "(cast(:userId as uuid) IS NULL OR a.user_id = cast(:userId as uuid)) AND " +
            "(cast(:cabinetId as bigint) IS NULL OR a.cabinet_id = cast(:cabinetId as bigint)) AND " +
            "(cast(:dossierId as bigint) IS NULL OR a.dossier_id = cast(:dossierId as bigint)) AND " +
            "(cast(:entityType as text) IS NULL OR a.entity_type = cast(:entityType as text)) AND " +
            "(cast(:action as text) IS NULL OR a.action = cast(:action as text)) AND " +
            "(cast(:startDate as timestamp) IS NULL OR a.created_at >= cast(:startDate as timestamp)) AND " +
            "(cast(:endDate as timestamp) IS NULL OR a.created_at <= cast(:endDate as timestamp)) " +
            "ORDER BY a.created_at DESC", // ← Supprimer le deuxième tri
            countQuery = "SELECT count(*) FROM audit_logs a WHERE " +
                    "(cast(:userId as uuid) IS NULL OR a.user_id = cast(:userId as uuid)) AND " +
                    "(cast(:cabinetId as bigint) IS NULL OR a.cabinet_id = cast(:cabinetId as bigint)) AND " +
                    "(cast(:dossierId as bigint) IS NULL OR a.dossier_id = cast(:dossierId as bigint)) AND " +
                    "(cast(:entityType as text) IS NULL OR a.entity_type = cast(:entityType as text)) AND " +
                    "(cast(:action as text) IS NULL OR a.action = cast(:action as text)) AND " +
                    "(cast(:startDate as timestamp) IS NULL OR a.created_at >= cast(:startDate as timestamp)) AND " +
                    "(cast(:endDate as timestamp) IS NULL OR a.created_at <= cast(:endDate as timestamp))",
            nativeQuery = true)
    Page<AuditLog> searchAuditLogsNative(
            @Param("userId") UUID userId,
            @Param("cabinetId") Long cabinetId,
            @Param("dossierId") Long dossierId,
            @Param("entityType") String entityType,
            @Param("action") String action,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

}