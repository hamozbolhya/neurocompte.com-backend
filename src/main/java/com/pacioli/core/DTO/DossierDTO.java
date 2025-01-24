package com.pacioli.core.DTO;

import com.pacioli.core.models.Exercise;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
public class DossierDTO {
    private Long id;
    private String name;
    private String ICE;
    private String address;
    private String city;
    private String phone;
    private String email;
    private CabinetDTO cabinet;
    private List<Exercise> exercises = new ArrayList<>();

    @Data
    public static class CabinetDTO {
        private Long id;
    }

    // Constructor for JPQL queries
    public DossierDTO(Long id, String name, String ICE, String address, String city, String phone, String email) {
        this.id = id;
        this.name = name;
        this.ICE = ICE;
        this.address = address;
        this.city = city;
        this.phone = phone;
        this.email = email;
        // Create cabinet DTO with null ID - you'll need to set this after query if needed
        this.cabinet = new CabinetDTO();

    }

    // Default constructor
    public DossierDTO() {
        this.cabinet = new CabinetDTO();
    }
}