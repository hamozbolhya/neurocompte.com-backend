package com.pacioli.core.DTO;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * DTO for representing a company in the AI service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    /**
     * ID of the company - should match the Dossier ID
     */
    private Long id;

    /**
     * Name of the company - should match the Dossier name
     */
    private String name;

    /**
     * Country code in ISO format (3 characters)
     */
    private String country;

    private String activity;
}