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

    // ✅ Remplacer l'ancienne méthode par les deux nouvelles
    // Page<AuditLog> findByCabinetIdOrderByCreatedAtDesc(Long cabinetId, Pageable pageable); ← À SUPPRIMER

    // ✅ Recherche par cabinet de l'utilisateur
    Page<AuditLog> findByUserCabinetIdOrderByCreatedAtDesc(Long userCabinetId, Pageable pageable);

    // ✅ Recherche par cabinet cible
    Page<AuditLog> findByTargetCabinetIdOrderByCreatedAtDesc(Long targetCabinetId, Pageable pageable);

    // Recherche par utilisateur
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // Recherche par dossier
    Page<AuditLog> findByDossierIdOrderByCreatedAtDesc(Long dossierId, Pageable pageable);

    // Recherche par entité
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId, Pageable pageable);

    // Recherche par type d'action
    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    // ✅ REQUÊTE NATIVE CORRIGÉE avec user_cabinet_id et target_cabinet_id
    @Query(value = "SELECT * FROM audit_logs a WHERE " +
            "(cast(:userId as uuid) IS NULL OR a.user_id = cast(:userId as uuid)) AND " +
            "(cast(:userCabinetId as bigint) IS NULL OR a.user_cabinet_id = cast(:userCabinetId as bigint)) AND " +
            "(cast(:targetCabinetId as bigint) IS NULL OR a.target_cabinet_id = cast(:targetCabinetId as bigint)) AND " +
            "(cast(:dossierId as bigint) IS NULL OR a.dossier_id = cast(:dossierId as bigint)) AND " +
            "(cast(:entityType as text) IS NULL OR a.entity_type = cast(:entityType as text)) AND " +
            "(cast(:action as text) IS NULL OR a.action = cast(:action as text)) AND " +
            "(cast(:startDate as timestamp) IS NULL OR a.created_at >= cast(:startDate as timestamp)) AND " +
            "(cast(:endDate as timestamp) IS NULL OR a.created_at <= cast(:endDate as timestamp)) " +
            "ORDER BY a.created_at DESC",
            countQuery = "SELECT count(*) FROM audit_logs a WHERE " +
                    "(cast(:userId as uuid) IS NULL OR a.user_id = cast(:userId as uuid)) AND " +
                    "(cast(:userCabinetId as bigint) IS NULL OR a.user_cabinet_id = cast(:userCabinetId as bigint)) AND " +
                    "(cast(:targetCabinetId as bigint) IS NULL OR a.target_cabinet_id = cast(:targetCabinetId as bigint)) AND " +
                    "(cast(:dossierId as bigint) IS NULL OR a.dossier_id = cast(:dossierId as bigint)) AND " +
                    "(cast(:entityType as text) IS NULL OR a.entity_type = cast(:entityType as text)) AND " +
                    "(cast(:action as text) IS NULL OR a.action = cast(:action as text)) AND " +
                    "(cast(:startDate as timestamp) IS NULL OR a.created_at >= cast(:startDate as timestamp)) AND " +
                    "(cast(:endDate as timestamp) IS NULL OR a.created_at <= cast(:endDate as timestamp))",
            nativeQuery = true)
    Page<AuditLog> searchAuditLogsNative(
            @Param("userId") UUID userId,
            @Param("userCabinetId") Long userCabinetId,
            @Param("targetCabinetId") Long targetCabinetId,
            @Param("dossierId") Long dossierId,
            @Param("entityType") String entityType,
            @Param("action") String action,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // ✅ REQUÊTE JPQL CORRIGÉE
    @Query("SELECT a FROM AuditLog a WHERE " +
            "(:userId IS NULL OR a.userId = :userId) AND " +
            "(:userCabinetId IS NULL OR a.userCabinetId = :userCabinetId) AND " +
            "(:targetCabinetId IS NULL OR a.targetCabinetId = :targetCabinetId) AND " +
            "(:dossierId IS NULL OR a.dossierId = :dossierId) AND " +
            "(:entityType IS NULL OR a.entityType = :entityType) AND " +
            "(:action IS NULL OR a.action = :action) AND " +
            "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
            "(:endDate IS NULL OR a.createdAt <= :endDate) " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> searchAuditLogs(
            @Param("userId") UUID userId,
            @Param("userCabinetId") Long userCabinetId,
            @Param("targetCabinetId") Long targetCabinetId,
            @Param("dossierId") Long dossierId,
            @Param("entityType") String entityType,
            @Param("action") String action,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // Statistiques par type d'action
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a " +
            "WHERE a.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY a.action ORDER BY COUNT(a) DESC")
    List<Object[]> getActionTypeStats(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Statistiques par utilisateur
    @Query("SELECT a.username, COUNT(a) as actionCount FROM AuditLog a " +
            "WHERE a.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY a.username ORDER BY actionCount DESC")
    List<Object[]> getUserActivityStats(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}