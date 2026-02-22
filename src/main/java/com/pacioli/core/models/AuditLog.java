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
    private UUID userId;  // L'utilisateur qui a effectué l'action

    @Column(name = "username", nullable = false)
    private String username;  // Nom d'utilisateur pour faciliter la recherche

    @Column(name = "action", nullable = false, length = 50)
    private String action;  // CREATE, UPDATE, DELETE, VIEW, LOGIN, LOGOUT, etc.

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;  // Dossier, Piece, Ecriture, User, Cabinet, etc.

    @Column(name = "entity_id", length = 255)
    private String entityId;  // ID de l'entité (peut être Long, UUID, etc.)

    @Column(name = "entity_name")
    private String entityName;  // Nom/libellé de l'entité pour faciliter l'identification

    @Column(name = "cabinet_id")
    private Long cabinetId;  // Pour filtrer par cabinet

    @Column(name = "cabinet_name")
    private String cabinetName;  // Nom du cabinet

    @Column(name = "dossier_id")
    private Long dossierId;  // Pour filtrer par dossier

    @Column(name = "dossier_name")
    private String dossierName;  // Nom du dossier

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;  // Ancienne valeur (au format JSON)

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;  // Nouvelle valeur (au format JSON)

    @Column(name = "changes", columnDefinition = "TEXT")
    private String changes;  // Résumé des changements (format JSON)

    @Column(name = "ip_address", length = 45)
    private String ipAddress;  // Adresse IP du client

    @Column(name = "user_agent", length = 500)
    private String userAgent;  // User-Agent du navigateur

    @Column(name = "request_url", length = 500)
    private String requestUrl;  // URL de la requête

    @Column(name = "http_method", length = 10)
    private String httpMethod;  // GET, POST, PUT, DELETE, etc.

    @Column(name = "execution_time_ms")
    private Long executionTime;  // Temps d'exécution en millisecondes

    @Column(name = "status", length = 20)
    private String status;  // SUCCESS, FAILURE

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;  // Message d'erreur en cas d'échec

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;  // Date de l'action

    // Index pour améliorer les performances des recherches
    @Column(name = "action_date", nullable = false)
    private LocalDateTime actionDate;
}