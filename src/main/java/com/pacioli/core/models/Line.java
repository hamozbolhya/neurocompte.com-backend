package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.pacioli.core.Deserialize.AccountDeserializer;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Entity
@Data
public class Line {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String label;             // Libellé de l’écriture

    private Double debit;

    @JsonSetter("debit")
    public void setDebit(Object value) {
        this.debit = convertToDouble(value);
    }

    private Double credit;
    // Fields to track original values before conversion
    // Additional fields for conversion tracking
    private Double originalDebit;        // Original debit amount before conversion
    private Double originalCredit;       // Original credit amount before conversion
    private String originalCurrency;     // Original currency from AI response

    private Double convertedDebit;       // Converted debit to dossier currency
    private Double convertedCredit;      // Converted credit to dossier currency
    private String convertedCurrency;    // Dossier currency (what we converted to)

    private Double usdDebit;             // Optional: USD equivalent (if converted)
    private Double usdCredit;            // Optional: USD equivalent (if converted)

    private Double exchangeRate;         // Exchange rate used
    @JsonFormat(pattern = "yyyy-MM-dd") // Important: Match the format sent by the frontend
    @Column(name = "exchange_rate_date")
    private LocalDate exchangeRateDate;

    @JsonSetter("credit")
    public void setCredit(Object value) {
        this.credit = convertToDouble(value);
    }
    @ManyToOne
    @JoinColumn(name = "ecriture_id", nullable = false)
    @JsonBackReference("ecriture-lines") // Match the name in Ecriture
    @ToString.Exclude  // Prevent circular reference in toString
    private Ecriture ecriture;        // Associated Ecriture

    //@JsonDeserialize(using = AccountDeserializer.class)

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "account_id", nullable = true)
    @JsonBackReference("account-lines")
    private Account account;

    private Double convertToDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid double value: " + value);
            }
        }
        return null;
    }


    @JsonSetter("exchangeRateDate")
    public void setExchangeRateDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            this.exchangeRateDate = null;
            return;
        }

        try {
            // Attempt to parse with yyyy-MM-dd format first
            this.exchangeRateDate = LocalDate.parse(dateStr);
        } catch (Exception e) {
            // If the above fails, try dd/MM/yyyy format
            try {
                java.time.format.DateTimeFormatter formatter =
                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                this.exchangeRateDate = LocalDate.parse(dateStr, formatter);
            } catch (Exception e2) {
                // If all parsing fails, set to null
                this.exchangeRateDate = null;
            }
        }
    }
}
