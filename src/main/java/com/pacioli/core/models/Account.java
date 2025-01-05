package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "account", uniqueConstraints = @UniqueConstraint(columnNames = {"account", "dossier_id"}))
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;
    private String account;
    private Boolean hasEntries;

    @ManyToOne(optional = true) // Allow null values for journal
    @JoinColumn(name = "journal_id")
    @JsonBackReference("journal-accounts") // Match the reference in Journal
    private Journal journal;

    @ManyToOne(optional = false, fetch = FetchType.LAZY) // Each account belongs to a Dossier (required)
    @JoinColumn(name = "dossier_id", nullable = false) // Add dossier_id as foreign key
    @JsonBackReference("dossier-accounts") // Match the reference in Dossier
    private Dossier dossier;
}
