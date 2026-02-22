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

    // Recherche par dossier
    Page<AuditLog> findByDossierIdOrderByCreatedAtDesc(Long dossierId, Pageable pageable);

    // Recherche par entité
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId, Pageable pageable);

    // Recherche par type d'action
    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    // Recherche par période
    Page<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // Recherche combinée
    @Query("SELECT a FROM AuditLog a WHERE " +
            "(:userId IS NULL OR a.userId = :userId) AND " +
            "(:cabinetId IS NULL OR a.cabinetId = :cabinetId) AND " +
            "(:dossierId IS NULL OR a.dossierId = :dossierId) AND " +
            "(:entityType IS NULL OR a.entityType = :entityType) AND " +
            "(:action IS NULL OR a.action = :action) AND " +
            "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
            "(:endDate IS NULL OR a.createdAt <= :endDate) " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> searchAuditLogs(
            @Param("userId") UUID userId,
            @Param("cabinetId") Long cabinetId,
            @Param("dossierId") Long dossierId,
            @Param("entityType") String entityType,
            @Param("action") String action,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // Statistiques par utilisateur
    @Query("SELECT a.username, COUNT(a) as actionCount FROM AuditLog a " +
            "WHERE a.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY a.username ORDER BY actionCount DESC")
    List<Object[]> getUserActivityStats(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Statistiques par type d'action
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a " +
            "WHERE a.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY a.action ORDER BY COUNT(a) DESC")
    List<Object[]> getActionTypeStats(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}