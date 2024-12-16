package com.pacioli.core.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor // Add this annotation so the constructor for all fields is created
@NoArgsConstructor
public class DossierDTO {
    private Long id;
    private String name;
    private String ICE;
    private String address;
    private String city;
    private String phone;
    private String email;
}