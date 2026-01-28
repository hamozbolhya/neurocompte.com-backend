package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "countries")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Country {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 3)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate createdDate;

    @Column(nullable = false)
    private boolean active;

    @ManyToOne
    @JoinColumn(name = "currency_id")
    private Currency currency;

    @OneToMany(mappedBy = "country")
    @ToString.Exclude
    @JsonIgnore
    private List<Dossier> dossiers;

    @PrePersist
    public void prePersist() {
        if (createdDate == null) {
            createdDate = LocalDate.now();
        }
        if (active == false) {
            active = true; // Default to active
        }
    }
}