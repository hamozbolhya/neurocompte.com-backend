package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.Date;
import java.util.List;

@Entity
@Data
public class Piece {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "dossier_id", nullable = false)
    @JsonBackReference("dossier-pieces") // Match the name in Dossier
    private Dossier dossier;

    @Column(unique = true, nullable = false)
    private String filename;
    private String type;
    private Date uploadDate;
    private Double amount;

    @Transient
    private String filePath;

    @OneToOne(mappedBy = "piece", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("piece-facturedata") // Unique reference for FactureData
    @ToString.Exclude  // Prevent circular reference in toString
    private FactureData factureData;

    @OneToMany(mappedBy = "piece", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("piece-ecritures") // Unique reference for Ecritures
    @ToString.Exclude  // Prevent circular reference in toString

    private List<Ecriture> ecritures;
}
