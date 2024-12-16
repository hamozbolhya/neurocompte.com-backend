package com.pacioli.core.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Parameter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String key;
    private String value;
    @ManyToOne
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    @ManyToOne
    @JoinColumn(name = "dossier_id")
    private Dossier dossier;

}
