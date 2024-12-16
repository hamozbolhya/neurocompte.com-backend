package com.pacioli.core.DTO;

import lombok.Data;

@Data
public class AccountDTO {
    private Long id;
    private String label;
    private String account; // Account code
}
