package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Entity
@Data
public class Journal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cabinet_id", nullable = false)
    @JsonBackReference("cabinet-journals") // Match the name in Cabinet
    private Cabinet cabinet;

    @ManyToOne
    @JoinColumn(name = "dossier_id", nullable = false)
    @JsonBackReference("dossier-journals") // Match the name in Dossier
    private Dossier dossier;

    private String name;
    private String type;

    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("journal-accounts") // Unique reference for Journal-Accounts
    @ToString.Exclude  // Prevent circular reference in toString
    private List<Account> accounts;

    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL)
    @JsonManagedReference("journal-ecritures") // Unique reference for Ecritures
    @ToString.Exclude  // Prevent circular reference in toString
    private List<Ecriture> ecritures;

    // **Add constructor for easier creation of Journal objects**
    public Journal(String name, String type, Cabinet cabinet, Dossier dossier) {
        this.name = name;
        this.type = type;
        this.cabinet = cabinet;
        this.dossier = dossier;
    }

    public Journal() {
        // Default constructor for JPA
    }
}
