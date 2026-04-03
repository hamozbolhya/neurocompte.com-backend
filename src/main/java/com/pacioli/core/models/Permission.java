package com.pacioli.core.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "permissions")
@Data
public class Permission {
    @Id
    @UuidGenerator
    private String id;

    @Column(nullable = false, unique = true)
    private String name;
}
