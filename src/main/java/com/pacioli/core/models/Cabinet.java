package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Entity
@Data
public class Cabinet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private String name;
    private String address;
    private String phone;
    private String ice;
    private String ville;

    @OneToMany(mappedBy = "cabinet")
    @JsonManagedReference("cabinet-dossiers") // Unique reference for Cabinet-Dossiers
    @ToString.Exclude  // Prevent circular reference in toString
    private List<Dossier> dossiers;

    @OneToMany(mappedBy = "cabinet")
    @JsonManagedReference("cabinet-journals") // Unique reference for Cabinet-Journals
    @ToString.Exclude  // Prevent circular reference in toString
    private List<Journal> journals;

    @OneToMany(mappedBy = "cabinet", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference("cabinet-users") // Match the reference in User
    @ToString.Exclude  // Prevent circular reference in toString
    private List<User> users;

}
