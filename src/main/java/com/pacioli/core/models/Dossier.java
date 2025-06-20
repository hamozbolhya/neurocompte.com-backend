package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.List;
import java.util.Objects;

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

    // Replace the country string fields with a proper ManyToOne relationship
    @ManyToOne
    @JoinColumn(name = "country_id")
    private Country country;

    // NEW: Decimal precision field for formatting numbers
    @Column(name = "decimal_precision", nullable = false, columnDefinition = "INTEGER DEFAULT 2")
    private Integer decimalPrecision = 2;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dossier dossier = (Dossier) o;
        return Objects.equals(id, dossier.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // For backward compatibility - Deprecated but maintained for transition
    @Deprecated
    @Transient
    public Pays getPays() {
        if (this.country == null) {
            return null;
        }

        Pays pays = new Pays();
        pays.setCountry(this.country.getName());
        pays.setCode(this.country.getCode());
        return pays;
    }

    // Helper class for the transient pays property
    @Data
    public static class Pays {
        private String country;
        private String code;
    }

    // Convenience methods to access country currency
    @Transient
    public Currency getCurrency() {
        return country != null ? country.getCurrency() : null;
    }

    @Transient
    public String getCurrencyCode() {
        Currency currency = getCurrency();
        return currency != null ? currency.getCode() : null;
    }

    // Helper method to get decimal precision with validation
    public Integer getDecimalPrecision() {
        // Ensure decimal precision is within reasonable bounds (0-10)
        if (decimalPrecision == null) {
            return 2; // Default
        }
        return Math.max(0, Math.min(10, decimalPrecision));
    }
}