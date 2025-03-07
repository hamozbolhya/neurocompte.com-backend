package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Entity
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Dossier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cabinet_id", nullable = false)
    @JsonBackReference("cabinet-dossiers") // Match the name in Cabinet
    @ToString.Exclude  // Prevent circular reference in toString
    private Cabinet cabinet;

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL)
    @JsonManagedReference("dossier-journals") // Unique reference for Dossier-Journals
    @ToString.Exclude  // Prevent circular reference in toString
    private List<Journal> journals;

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL)
    @ToString.Exclude  // Prevent circular reference in toString
    @JsonManagedReference("dossier-pieces") // Unique reference for Dossier-Pieces
    private List<Piece> pieces;

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL)
    @JsonManagedReference("dossier-exercises") // Corrected reference for Exercises
    @ToString.Exclude  // Prevent circular reference in toString
    private List<Exercise> exercises;

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("dossier-accounts") // Unique reference for Dossier-Accounts
    @ToString.Exclude  // Prevent circular reference in toString
    private List<Account> accounts;

    @Column(unique = true)
    private String name;
    private String ICE;
    private String address;
    private String city;
    private String phone;
    private String email;

    // Add country information as columns in the Dossier table
    @Column(name = "country")
    private String country;

    @Column(name = "code")
    private String code;

    // Transient getter for pays as a nested object (not persisted in DB)
    @Transient
    public Pays getPays() {
        Pays pays = new Pays();
        pays.setCountry(this.country);
        pays.setCode(this.code);
        return pays;
    }

    // Transient setter for pays as a nested object
    public void setPays(Pays pays) {
        if (pays != null) {
            this.country = pays.getCountry();
            this.code = pays.getCode();
        }
    }

    // Helper class for the transient pays property
    @Data
    public static class Pays {
        private String country;
        private String code;
    }
}
