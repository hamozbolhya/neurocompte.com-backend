package com.pacioli.core.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "currencies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Currency {

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