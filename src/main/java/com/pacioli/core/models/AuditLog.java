package com.pacioli.core.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Data
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "username", nullable = false)
    private String username;

    // ✅ Cabinet de l'utilisateur (son cabinet d'attache)
    @Column(name = "user_cabinet_id")
    private Long userCabinetId;

    @Column(name = "user_cabinet_name")
    private String userCabinetName;

    // ✅ Cabinet cible de l'action (celui sur lequel on agit)
    @Column(name = "target_cabinet_id")
    private Long targetCabinetId;

    @Column(name = "target_cabinet_name")
    private String targetCabinetName;

    // Informations sur le dossier (contexte supplémentaire)
    @Column(name = "dossier_id")
    private Long dossierId;

    @Column(name = "dossier_name")
    private String dossierName;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 255)
    private String entityId;

    @Column(name = "entity_name")
    private String entityName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "changes", columnDefinition = "TEXT")
    private String changes;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "request_url", length = 500)
    private String requestUrl;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "execution_time_ms")
    private Long executionTime;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "action_date", nullable = false)
    private LocalDateTime actionDate;
}