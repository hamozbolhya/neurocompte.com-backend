package com.pacioli.core.DTO;

import lombok.Data;

@Data
public class AccountDTO {
    private Long id;
    private String account; // Account number (e.g., "613000")
    private String label;   // Account description (e.g., "Location immobilière")
    private String type;
}
