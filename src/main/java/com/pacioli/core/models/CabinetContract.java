package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;

@Entity
@Data
@Table(name = "cabinet_contract")
public class CabinetContract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id", nullable = false)
    @JsonBackReference("cabinet-contracts")
    @ToString.Exclude
    private Cabinet cabinet;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Integer normalStatementPieceQuota;

    @Column(nullable = false)
    private Integer bankStatementPageQuota;

    /** Normal (non-bank) pieces counted when status becomes PROCESSED. */
    @Column(name = "normal_pieces_consumed")
    private Long normalStatementPieceConsumption = 0L;

    /** Bank statement pages counted when status becomes PROCESSED. */
    @Column(name = "bank_pages_consumed")
    private Long bankStatementPageConsumption = 0L;

    private boolean active = true;

    private LocalDate createdAt = LocalDate.now();
}
