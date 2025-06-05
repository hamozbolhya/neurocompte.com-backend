package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;
import java.util.Date;
import java.util.Objects;

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

    // Original invoice data
    private String invoiceNumber;    // Numéro de la facture
    private Date invoiceDate;        // Date de la facture
    private Double totalTTC;         // Montant TTC
    private Double totalHT;          // Montant HT
    private Double totalTVA;         // Montant TVA
    private Double taxRate;          // Taux TVA
    private String tier;             // Tiers (client ou fournisseur)
    private String ice;              // ICE
    private String devise;           // Original currency

    // Exchange rate information
    private Double exchangeRate;             // Exchange rate used for conversion
    private String originalCurrency;         // Original currency code
    private String convertedCurrency;        // Converted currency code
    private LocalDate exchangeRateDate;      // Date of the exchange rate

    // Converted amounts
    private Double convertedTotalTTC;        // Converted Montant TTC
    private Double convertedTotalHT;         // Converted Montant HT
    private Double convertedTotalTVA;        // Converted Montant TVA

    // USD equivalents (optional)
    private Double usdTotalTTC;              // USD equivalent of TTC
    private Double usdTotalHT;               // USD equivalent of HT
    private Double usdTotalTVA;              // USD equivalent of TVA

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FactureData that = (FactureData) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}