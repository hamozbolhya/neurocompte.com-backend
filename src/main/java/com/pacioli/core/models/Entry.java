package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.Date;

@Entity
@Data
public class Entry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "journal_id", nullable = false)
    @JsonBackReference("journal-entries") // Match the reference in Journal
    @ToString.Exclude  // Prevent circular reference in toString
    private Journal journal;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    private Date date;
    private String label;
    private Double debit;
    private Double credit;
}

