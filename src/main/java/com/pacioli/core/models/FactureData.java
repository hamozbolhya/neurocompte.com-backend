package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.Date;

@Entity
@Data
public class FactureData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "piece_id", nullable = false)
    @JsonBackReference("piece-facturedata") // Match the name in Piece
    @ToString.Exclude  // Prevent circular reference in toString
    private Piece piece;

    private String invoiceNumber; // Numéro de la facture
    private Date invoiceDate;     // Date de la facture
    private Double totalTTC;      // Montant TTC
    private Double totalHT;       // Montant HT
    private Double totalTVA;      // Montant TVA
    private Double taxRate;       // Taux TVA
    private String tier;          // Tiers (client ou fournisseur)
    private String ice;           // ICE
}

