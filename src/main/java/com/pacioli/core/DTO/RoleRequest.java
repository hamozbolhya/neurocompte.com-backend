package com.pacioli.core.DTO;

import lombok.Data;

import java.util.List;

@Data
public class RoleRequest {
    private String name; // Name of the role
    private List<String> permissionIds; // List of permission IDs to associate with the role
}
