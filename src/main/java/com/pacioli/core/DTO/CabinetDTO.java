package com.pacioli.core.DTO;

import lombok.Data;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor // Add constructor for projection
@NoArgsConstructor  // No-args constructor for JPA
public class CabinetDTO {

    private Long id;
    private String name;
    private String address;
    private String phone;
    private String ice;
    private String ville;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;

    // List of users assigned to the cabinet, including their roles
    private List<UserDTO> users;

    public CabinetDTO(Long id, String name, String address, String phone, String ice, String ville,
                      LocalDate contractStartDate, LocalDate contractEndDate) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.ice = ice;
        this.ville = ville;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
    }
}

