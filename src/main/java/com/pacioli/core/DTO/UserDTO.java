package com.pacioli.core.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    private UUID id; // Use UUID since it's the primary key for User
    private String username;
    private String email;

    // List of roles associated with the user
    private List<RoleDTO> roles;
}
