package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Entity
@Data
@Table(name = "account", uniqueConstraints = @UniqueConstraint(columnNames = {"account", "dossier_id"}))
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;

    @Column(name = "account")
    private String account; // Account number

    @Column(name = "has_entries")
    private Boolean hasEntries = false;

    // Add if you need these fields
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt = new Date();

    @ManyToOne(optional = true)
    @JoinColumn(name = "journal_id")
    @JsonBackReference("journal-accounts")
    private Journal journal;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    @JsonBackReference("dossier-accounts")
    private Dossier dossier;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = new Date();
        }
        updatedAt = new Date();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }
}