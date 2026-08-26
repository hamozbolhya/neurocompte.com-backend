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
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
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
    @Lazy
    private AuditService auditService;

    @Autowired
    private UserService userService;

    // ✅ AJOUT: Méthode utilitaire pour déterminer le cabinet cible
    private Long getTargetCabinetId(User user) {
        if (user == null) return null;

        // Pour les rôles, le cabinet cible est généralement celui de l'utilisateur
        // car les rôles sont globaux au système
        return user.getCabinet() != null ? user.getCabinet().getId() : null;
    }

    // ✅ AJOUT: Méthode utilitaire pour le nom du cabinet cible
    private String getTargetCabinetName(User user) {
        if (user == null) return null;
        return user.getCabinet() != null ? user.getCabinet().getName() : null;
    }

    @Override
    @Transactional
    public Role createRole(String roleName, @NonNull List<String> permissionIds) {
        User currentUser = userService.getCurrentUser();

        try {
            Role role = new Role();
            role.setName(roleName);

            // Fetch and associate permissions with the role
            Set<Permission> permissions = new HashSet<>();
            List<String> permissionNames = new ArrayList<>();

            for (String id : permissionIds) {
                String permissionId = Objects.requireNonNull(id, "permission id");
                Optional<Permission> permission = permissionRepository.findById(permissionId);
                permission.ifPresent(p -> {
                    permissions.add(p);
                    permissionNames.add(p.getName());
                });
            }
            role.setPermissions(permissions);

            Role savedRole = roleRepository.save(role);

            // ✅ MODIFICATION: Utilisation de logSuccessWithTargetCabinet
            Map<String, Object> newState = Map.of(
                    "roleName", savedRole.getName(),
                    "permissions", permissionNames,
                    "permissionCount", permissions.size()
            );

            auditService.logSuccessWithTargetCabinet(
                    currentUser,
                    "CREATE",
                    "Role",
                    savedRole.getId(),
                    savedRole.getName(),
                    null,
                    newState,
                    getTargetCabinetId(currentUser),
                    getTargetCabinetName(currentUser)
            );

            log.info("✅ Role created successfully: {} with {} permissions", roleName, permissions.size());

            return savedRole;

        } catch (Exception e) {
            // ✅ MODIFICATION: Utilisation de logFailureWithTargetCabinet
            auditService.logFailureWithTargetCabinet(
                    currentUser,
                    "CREATE",
                    "Role",
                    null,
                    roleName,
                    "Error creating role: " + e.getMessage(),
                    getTargetCabinetId(currentUser),
                    getTargetCabinetName(currentUser)
            );

            log.error("❌ Error creating role: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public List<Role> getAllRoles() {
        User currentUser = userService.getCurrentUser();

        // ✅ AJOUT: Audit de consultation
        List<Role> roles = roleRepository.findAll();

        auditService.logViewWithTargetCabinet(
                currentUser,
                "RoleList",
                null,
                "All Roles",
                getTargetCabinetId(currentUser),
                getTargetCabinetName(currentUser)
        );

        return roles;
    }

    @Override
    @Transactional
    public Role updateRole(@NonNull UUID roleId, String roleName) {
        User currentUser = userService.getCurrentUser();

        try {
            // Find the role by its ID
            Role role = roleRepository.findById(Objects.requireNonNull(String.valueOf(roleId), "roleId"))
                    .orElseThrow(() -> {
                        // ✅ MODIFICATION: Utilisation de logFailureWithTargetCabinet
                        auditService.logFailureWithTargetCabinet(
                                currentUser,
                                "UPDATE",
                                "Role",
                                roleId,
                                "Role-" + roleId,
                                "Role not found with id: " + roleId,
                                getTargetCabinetId(currentUser),
                                getTargetCabinetName(currentUser)
                        );
                        return new RuntimeException("Role not found with id: " + roleId);
                    });

            // Sauvegarder l'ancien état pour l'audit
            Map<String, Object> oldState = Map.of(
                    "roleName", role.getName(),
                    "permissionCount", role.getPermissions() != null ? role.getPermissions().size() : 0,
                    "permissions", role.getPermissions() != null ?
                            role.getPermissions().stream().map(Permission::getName).toList() : List.of()
            );

            // Update the role name
            String oldName = role.getName();
            role.setName(roleName);

            // Save and return the updated role
            Role updatedRole = roleRepository.save(role);

            // Audit: Mise à jour de rôle avec cabinet cible
            Map<String, Object> newState = Map.of(
                    "roleName", updatedRole.getName(),
                    "permissionCount", updatedRole.getPermissions() != null ? updatedRole.getPermissions().size() : 0,
                    "permissions", updatedRole.getPermissions() != null ?
                            updatedRole.getPermissions().stream().map(Permission::getName).toList() : List.of()
            );

            // ✅ MODIFICATION: Utilisation de logSuccessWithTargetCabinet
            auditService.logSuccessWithTargetCabinet(
                    currentUser,
                    "UPDATE",
                    "Role",
                    roleId,
                    updatedRole.getName(),
                    oldState,
                    newState,
                    getTargetCabinetId(currentUser),
                    getTargetCabinetName(currentUser)
            );

            log.info("✅ Role updated successfully: {} -> {}", oldName, roleName);

            return updatedRole;

        } catch (Exception e) {
            // ✅ MODIFICATION: Utilisation de logFailureWithTargetCabinet
            auditService.logFailureWithTargetCabinet(
                    currentUser,
                    "UPDATE",
                    "Role",
                    roleId,
                    "Role-" + roleId,
                    "Error updating role: " + e.getMessage(),
                    getTargetCabinetId(currentUser),
                    getTargetCabinetName(currentUser)
            );

            log.error("❌ Error updating role: {}", e.getMessage());
            throw e;
        }
    }
}