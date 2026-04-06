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
    private List<ContractDTO> contracts;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ContractDTO {
        private Long id;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer normalStatementPieceQuota;
        private Integer bankStatementPageQuota;
        private boolean active;
    }

    // List of users assigned to the cabinet, including their roles
    private List<UserDTO> users;

    public CabinetDTO(Long id, String name, String address, String phone, String ice, String ville) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.ice = ice;
        this.ville = ville;
    }
}

