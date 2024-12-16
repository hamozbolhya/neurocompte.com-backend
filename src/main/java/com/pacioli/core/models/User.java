package com.pacioli.core.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    private String username;
    @Column(name = "email", nullable = false, unique = true) // Unique email constraint
    private String email;

    @JsonIgnore  // This annotation hides the password field in API responses
    @Column(name = "password", nullable = false)
    private String password;

    private boolean active;
    @Column(name = "is_hold", nullable = false)
    private boolean isHold = false;
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "users_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @ToString.Exclude  // Prevent roles from being printed in toString
    private Set<Role> roles = new HashSet<>();

    // Add reference to Cabinet
    @ManyToOne
    @JoinColumn(name = "cabinet_id", nullable = false)
    @JsonBackReference("cabinet-users") // Match the reference in Cabinet
    @ToString.Exclude  // Prevent circular reference in toString
    private Cabinet cabinet;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.active = false;
        this.isHold = false;
        this.isDeleted = false;
    }
}
