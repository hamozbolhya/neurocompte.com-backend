package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Entity
@Data
@Table(name = "dossier",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"name", "cabinet_id"})
        })
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Dossier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cabinet_id", nullable = false)
    @JsonBackReference("cabinet-dossiers")
    @ToString.Exclude
    private Cabinet cabinet;

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL)
    @JsonManagedReference("dossier-journals")
    @ToString.Exclude
    private List<Journal> journals;

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL)
    @ToString.Exclude
    @JsonManagedReference("dossier-pieces")
    private List<Piece> pieces;

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL)
    @JsonManagedReference("dossier-exercises")
    @ToString.Exclude
    private List<Exercise> exercises;

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("dossier-accounts")
    @ToString.Exclude
    private List<Account> accounts;

    private String name;
    private String ICE;
    private String address;
    private String city;
    private String phone;
    private String email;
    private String activity;

    @ManyToOne
    @JoinColumn(name = "country_id")
    private Country country;

    @Column(name = "decimal_precision", nullable = false, columnDefinition = "INTEGER DEFAULT 2")
    private Integer decimalPrecision = 2;

    // ✅ ADDED: Created date field
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

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

    @Data
    public static class Pays {
        private String country;
        private String code;
    }

    @Transient
    public Currency getCurrency() {
        return country != null ? country.getCurrency() : null;
    }

    @Transient
    public String getCurrencyCode() {
        Currency currency = getCurrency();
        return currency != null ? currency.getCode() : null;
    }

    public Integer getDecimalPrecision() {
        if (decimalPrecision == null) {
            return 2;
        }
        return Math.max(0, Math.min(10, decimalPrecision));
    }
}