package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.pacioli.core.Deserialize.AccountDeserializer;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

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

    @ManyToOne(fetch = FetchType.LAZY)
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
}
