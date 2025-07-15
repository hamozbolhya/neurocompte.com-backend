package com.pacioli.core.DTO;

import com.pacioli.core.models.Role;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
public class UserInfo {
    private UUID id;
    private Long cabinetId;
    private String username;
    private String email;
    private String password;

    private LocalDateTime createdAt;

    private Set<Role> roles = new HashSet<>();
    private boolean active;

    private boolean isHold;

    private List<String> roleIds;  // List of role IDs to assign to the user
}
