package com.pacioli.core.DTO;

import lombok.Data;

import java.util.Set;

@Data
public class UserRegistrationRequest {
    private String username;
    private String email;
    private String password;
    private Set<String> roles;
}
