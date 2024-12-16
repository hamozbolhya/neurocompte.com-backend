package com.pacioli.core.DTO;

import lombok.Data;

@Data
public class LineDTO {
    private Long id;
    private String label;
    private Double debit;
    private Double credit;

    private AccountDTO account; // Full account object instead of just the name
}
