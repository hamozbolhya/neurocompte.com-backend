package com.pacioli.core.services;

import com.pacioli.core.models.Role;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.UUID;

public interface RoleService {
    Role createRole(String roleName, @NonNull List<String> permissionIds);
    List<Role> getAllRoles();
    Role updateRole(@NonNull UUID roleId, String roleName);
}
