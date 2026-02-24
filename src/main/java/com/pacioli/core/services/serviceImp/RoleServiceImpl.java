package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Permission;
import com.pacioli.core.models.Role;
import com.pacioli.core.models.User;
import com.pacioli.core.repositories.PermissionRepository;
import com.pacioli.core.repositories.RoleRepository;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.RoleService;
import com.pacioli.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
public class RoleServiceImpl implements RoleService {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private AuditService auditService;
    @Autowired
    private UserService userService;


    @Override
    @Transactional
    public Role createRole(String roleName, List<String> permissionIds) {
        User currentUser = userService.getCurrentUser();

        try {
            Role role = new Role();
            role.setName(roleName);

            // Fetch and associate permissions with the role
            Set<Permission> permissions = new HashSet<>();
            List<String> permissionNames = new ArrayList<>();

            for (String id : permissionIds) {
                Optional<Permission> permission = permissionRepository.findById(id);
                permission.ifPresent(p -> {
                    permissions.add(p);
                    permissionNames.add(p.getName());
                });
            }
            role.setPermissions(permissions);

            Role savedRole = roleRepository.save(role);

            // Audit: Création de rôle
            auditService.logSuccess(currentUser, "CREATE", "Role", savedRole.getId(), savedRole.getName(), null, Map.of("roleName", savedRole.getName(), "permissions", permissionNames, "permissionCount", permissions.size()));

            log.info("✅ Role created successfully: {} with {} permissions", roleName, permissions.size());

            return savedRole;

        } catch (Exception e) {
            // Audit: Échec création
            auditService.logFailure(currentUser, "CREATE", "Role", null, roleName, "Error creating role: " + e.getMessage());
            log.error("❌ Error creating role: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }


    @Override
    @Transactional
    public Role updateRole(UUID roleId, String roleName) {
        User currentUser = userService.getCurrentUser();

        try {
            // Find the role by its ID
            Role role = roleRepository.findById(String.valueOf(roleId)).orElseThrow(() -> {
                // Audit: Échec mise à jour - rôle non trouvé
                auditService.logFailure(currentUser, "UPDATE", "Role", roleId, "Role-" + roleId, "Role not found with id: " + roleId);
                return new RuntimeException("Role not found with id: " + roleId);
            });

            // Sauvegarder l'ancien état pour l'audit
            Map<String, Object> oldState = Map.of("roleName", role.getName(), "permissionCount", role.getPermissions() != null ? role.getPermissions().size() : 0, "permissions", role.getPermissions() != null ? role.getPermissions().stream().map(Permission::getName).toList() : List.of());

            // Update the role name
            String oldName = role.getName();
            role.setName(roleName);

            // Update the permissions
            //TODO remove Block comment below if you need to update permissions also
            /*Set<Permission> permissions = new HashSet<>();
            List<String> permissionNames = new ArrayList<>();
            for (String id : permissionIds) {
                Optional<Permission> permission = permissionRepository.findById(id);
                permission.ifPresent(p -> {
                    permissions.add(p);
                    permissionNames.add(p.getName());
                });
            }
            role.setPermissions(permissions);*/

            // Save and return the updated role
            Role updatedRole = roleRepository.save(role);

            // Audit: Mise à jour de rôle
            Map<String, Object> newState = Map.of("roleName", updatedRole.getName(), "permissionCount", updatedRole.getPermissions() != null ? updatedRole.getPermissions().size() : 0, "permissions", updatedRole.getPermissions() != null ? updatedRole.getPermissions().stream().map(Permission::getName).toList() : List.of());

            auditService.logSuccess(currentUser, "UPDATE", "Role", roleId, updatedRole.getName(), oldState, newState);

            log.info("✅ Role updated successfully: {} -> {}", oldName, roleName);

            return updatedRole;

        } catch (Exception e) {
            // Audit: Échec mise à jour
            auditService.logFailure(currentUser, "UPDATE", "Role", roleId, "Role-" + roleId, "Error updating role: " + e.getMessage());
            log.error("❌ Error updating role: {}", e.getMessage());
            throw e;
        }
    }
}

