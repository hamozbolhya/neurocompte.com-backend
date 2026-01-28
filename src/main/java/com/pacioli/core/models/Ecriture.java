package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.pacioli.core.JsonParser.FlexibleLocalDateDeserializer;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

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
    @JsonFormat(pattern = "dd/MM/yyyy")  // Specify the expected date format
    private LocalDate entryDate;            // Date
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "journal_id", nullable = false)
    @JsonBackReference("journal-ecritures") // New back-reference for Journal
    @ToString.Exclude  // Prevent circular reference in toString
    private Journal journal;           // Journal
    @OneToMany(mappedBy = "ecriture", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("ecriture-lines") // Unique reference for Lines
    @ToString.Exclude  // Prevent circular reference in toString
    private List<Line> lines;         // Associated lines of the Ecriture

    // Exchange rate fields - Adding these at the Ecriture level allows setting default values for all lines
    private Double exchangeRate;        // Exchange rate used for all lines
    private String originalCurrency;    // Original currency (e.g., EUR)
    private String convertedCurrency;   // Converted currency (e.g., MAD)

    @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
    private LocalDate exchangeRateDate; // Date of the exchange rate
    private Boolean amountUpdated = false; // Default to false

    @Column(name = "manually_updated", nullable = false)
    private Boolean manuallyUpdated = false; // Default to false

    @Column(name = "manual_update_date")
    private LocalDate manualUpdateDate;

    // In Ecriture class
    @Transient
    public String getJournalName() {
        return journal != null ? journal.getName() : null;
    }

    @Transient
    public String getJournalType() {
        return journal != null ? journal.getType() : null;
    }

    public Ecriture() {
        this.manuallyUpdated = false;
    }

    // Getter/Setter with null safety
    public Boolean getManuallyUpdated() {
        return manuallyUpdated != null ? manuallyUpdated : false;
    }

    public void setManuallyUpdated(Boolean manuallyUpdated) {
        this.manuallyUpdated = manuallyUpdated != null ? manuallyUpdated : false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ecriture ecriture = (Ecriture) o;
        return Objects.equals(id, ecriture.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
