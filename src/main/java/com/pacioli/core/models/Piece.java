package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.pacioli.core.enums.PieceStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Entity
@Data
@JsonIgnoreProperties(value = { "uploadDate" }, allowGetters = true)
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
    @Column(name = "original_file_name", nullable = true)
    private String originalFileName;
    private String type;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "upload_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "UTC")
    private Date uploadDate;
    private Double amount;
    @Enumerated(EnumType.STRING)
    private PieceStatus status; // New Status field

    // AI currency and amount fields
    @Column(name = "ai_currency", nullable = true)
    private String aiCurrency;

    @Column(name = "ai_amount", nullable = true)
    private Double aiAmount;

    @Column(name = "exchange_rate", nullable = true)
    private Double exchangeRate;

    @Column(name = "converted_currency", nullable = true)
    private String convertedCurrency;

    @Column(name = "exchange_rate_date", nullable = true)
    private LocalDate exchangeRateDate;
    @Column(name = "exchange_rate_updated", nullable = true)
    private Boolean exchangeRateUpdated = false;
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
