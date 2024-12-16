package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;
import java.util.List;

@Entity
@Data
public class Ecriture {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "piece_id", nullable = false)
    @JsonBackReference("piece-ecritures") // Match the name in Piece
    @ToString.Exclude  // Prevent circular reference in toString
    private Piece piece;

    private String uniqueEntryNumber; // Numéro d’écriture unique
    private LocalDate entryDate;           // Date
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "journal_id", nullable = false)
    @JsonBackReference("journal-ecritures") // New back-reference for Journal
    @ToString.Exclude  // Prevent circular reference in toString
    private Journal journal;           // Journal
    @OneToMany(mappedBy = "ecriture", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("ecriture-lines") // Unique reference for Lines
    @ToString.Exclude  // Prevent circular reference in toString
    private List<Line> lines;         // Associated lines of the Ecriture
}
