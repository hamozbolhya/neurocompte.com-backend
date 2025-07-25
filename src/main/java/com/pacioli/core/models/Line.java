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
import java.util.Objects;
import java.math.BigDecimal;

@Entity
@Data
public class Line {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String label;             // Libellé de l'écriture

    private Double debit;
    private Double credit;

    // ADD THESE NEW FIELDS FOR EXACT PRECISION
    @Column(name = "debit_exact", precision = 20, scale = 6)
    private String debitExact;        // Store exact string from AI

    @Column(name = "credit_exact", precision = 20, scale = 6)
    private String creditExact;       // Store exact string from AI

    @JsonSetter("debit")
    public void setDebit(Object value) {
        this.debit = convertToDouble(value);
    }

    // Fields to track original values before conversion
    // Additional fields for conversion tracking
    private Double originalDebit;        // Original debit amount before conversion
    private Double originalCredit;       // Original credit amount before conversion
    private String originalCurrency;     // Original currency from AI response

    // ADD THESE NEW FIELDS FOR EXACT PRECISION IN ORIGINAL AMOUNTS
    @Column(name = "original_debit_exact")
    private String originalDebitExact;

    @Column(name = "original_credit_exact")
    private String originalCreditExact;

    private Double convertedDebit;       // Converted debit to dossier currency
    private Double convertedCredit;      // Converted credit to dossier currency
    private String convertedCurrency;    // Dossier currency (what we converted to)

    // ADD THESE NEW FIELDS FOR EXACT PRECISION IN CONVERTED AMOUNTS
    @Column(name = "converted_debit_exact")
    private String convertedDebitExact;

    @Column(name = "converted_credit_exact")
    private String convertedCreditExact;

    private Double usdDebit;             // Optional: USD equivalent (if converted)
    private Double usdCredit;            // Optional: USD equivalent (if converted)

    // ADD THESE NEW FIELDS FOR EXACT PRECISION IN USD AMOUNTS
    @Column(name = "usd_debit_exact")
    private String usdDebitExact;

    @Column(name = "usd_credit_exact")
    private String usdCreditExact;

    private Double exchangeRate;         // Exchange rate used

    // ADD THIS NEW FIELD FOR EXACT EXCHANGE RATE
    @Column(name = "exchange_rate_exact")
    private String exchangeRateExact;

    @JsonFormat(pattern = "yyyy-MM-dd") // Important: Match the format sent by the frontend
    @Column(name = "exchange_rate_date")
    private LocalDate exchangeRateDate;

    @Column(name = "manually_updated", nullable = false)
    private Boolean manuallyUpdated = false; // Default to false

    @Column(name = "manual_update_date")
    private LocalDate manualUpdateDate;

    @JsonSetter("credit")
    public void setCredit(Object value) {
        this.credit = convertToDouble(value);
    }
    @ManyToOne
    @JoinColumn(name = "ecriture_id", nullable = false)
    @JsonBackReference("ecriture-lines") // Match the name in Ecriture
    @ToString.Exclude  // Prevent circular reference in toString
    private Ecriture ecriture;        // Associated Ecriture


    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "account_id", nullable = true)
    @JsonBackReference("account-lines")
    private Account account;

    // HELPER METHODS TO GET BIGDECIMAL VALUES
    public BigDecimal getDebitAsBigDecimal() {
        return debitExact != null ? new BigDecimal(debitExact) : BigDecimal.ZERO;
    }

    public BigDecimal getCreditAsBigDecimal() {
        return creditExact != null ? new BigDecimal(creditExact) : BigDecimal.ZERO;
    }

    public Line() {
        this.manuallyUpdated = false;
    }

    // Getter/Setter with null safety
    public Boolean getManuallyUpdated() {
        return manuallyUpdated != null ? manuallyUpdated : false;
    }

    public void setManuallyUpdated(Boolean manuallyUpdated) {
        this.manuallyUpdated = manuallyUpdated != null ? manuallyUpdated : false;
    }
    // In Line class
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Line line = (Line) o;
        return Objects.equals(id, line.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

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