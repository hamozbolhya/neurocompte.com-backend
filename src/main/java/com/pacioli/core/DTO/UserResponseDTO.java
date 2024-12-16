package com.pacioli.core.DTO;


import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class UserResponseDTO {
    private UUID id;
    private String username;
    private String email;
    private boolean active;
    private Set<String> roles;
}
