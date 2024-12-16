package com.pacioli.core.services;

import com.pacioli.core.models.Role;

import java.util.List;
import java.util.UUID;

public interface RoleService {
    Role createRole(String roleName, List<String> permissionIds); // Check this line
    List<Role> getAllRoles();
    //TODO add this to the updateRole function if you need to update permission also List<String> permissionIds
    Role updateRole(UUID roleId, String roleName);
}
