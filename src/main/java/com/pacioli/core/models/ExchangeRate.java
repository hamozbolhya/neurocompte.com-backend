package com.pacioli.core.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "exchange_rates", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"date", "currency_code"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(name = "rate", nullable = false)
    private Double rate;

    @Column(name = "base_currency", nullable = false, length = 10)
    private String baseCurrency = "USD";
}
