package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Permission;
import com.pacioli.core.models.Role;
import com.pacioli.core.repositories.PermissionRepository;
import com.pacioli.core.repositories.RoleRepository;
import com.pacioli.core.services.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RoleServiceImpl implements RoleService {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Override
    public Role createRole(String roleName, List<String> permissionIds) { // This must match the interface
        Role role = new Role();
        role.setName(roleName);

        // Fetch and associate permissions with the role
        Set<Permission> permissions = new HashSet<>();
        for (String id : permissionIds) {
            Optional<Permission> permission = permissionRepository.findById(id);
            permission.ifPresent(permissions::add);
        }
        role.setPermissions(permissions);

        return roleRepository.save(role);
    }

    @Override
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }


    @Override
    public Role updateRole(UUID roleId, String roleName) {
        //TODO ADD THIS List<String> permissionIds TO updateRole FUNCTION PARAMS WHEN YOU NEED TO UPDATE PERMISSIONS
        // Find the role by its ID
        Role role = roleRepository.findById(String.valueOf(roleId))
                .orElseThrow(() -> new RuntimeException("Role not found"));

        // Update the role name
        role.setName(roleName);

        // Update the permissions
        //TODO remove Block comment below if you need to update permissions also
        /*Set<Permission> permissions = new HashSet<>();
        for (String id : permissionIds) {
            Optional<Permission> permission = permissionRepository.findById(id);
            permission.ifPresent(permissions::add);
        }
        role.setPermissions(permissions);*/

        // Save and return the updated role
        return roleRepository.save(role);
    }
}

