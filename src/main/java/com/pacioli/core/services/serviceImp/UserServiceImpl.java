package com.pacioli.core.services.serviceImp;

import com.pacioli.core.DTO.UpdateUserInfoRequest;
import com.pacioli.core.DTO.UserInfo;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.models.Role;
import com.pacioli.core.models.User;
import com.pacioli.core.repositories.RoleRepository;
import com.pacioli.core.repositories.UserRepository;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.UserService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    @Lazy
    private AuditService auditService;

    // ✅ GET - PAS D'AUDIT
    @Override
    public User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                log.debug("No authenticated user found in security context");
                return null;
            }

            Object principal = authentication.getPrincipal();

            if (principal instanceof User) {
                return (User) principal;
            } else if (principal instanceof org.springframework.security.core.userdetails.User) {
                // Si c'est un UserDetails standard, récupérer depuis la DB
                String username = ((org.springframework.security.core.userdetails.User) principal).getUsername();
                return userRepository.findByEmail(username)
                        .orElseGet(() -> userRepository.findByUsername(username).orElse(null));
            }
        } catch (Exception e) {
            log.error("Error getting current user: {}", e.getMessage());
        }

        return null;
    }

    // ✅ CREATE - AVEC AUDIT
    @Override
    public UserInfo createUser(UserInfo userInfo) {
        // Check if username already exists
        Optional<User> existingUserByUsername = userRepository.findByUsername(userInfo.getUsername());
        if (existingUserByUsername.isPresent()) {
            // Audit échec - username déjà utilisé
            auditService.logFailure(
                    getCurrentUser(),
                    "CREATE",
                    "User",
                    null,
                    userInfo.getUsername(),
                    "Ce nom d'utilisateur est déjà utilisé"
            );
            throw new IllegalArgumentException("Ce nom d'utilisateur est déjà utilisé");
        }

        // Check if email already exists
        Optional<User> existingUserByEmail = userRepository.findByEmail(userInfo.getEmail());
        if (existingUserByEmail.isPresent()) {
            // Audit échec - email déjà utilisé
            auditService.logFailure(
                    getCurrentUser(),
                    "CREATE",
                    "User",
                    null,
                    userInfo.getEmail(),
                    "Cette adresse email est déjà utilisée"
            );
            throw new IllegalArgumentException("Cette adresse email est déjà utilisée");
        }

        // Encrypt the password using BCrypt
        String encodedPassword = passwordEncoder.encode(userInfo.getPassword());

        // Create a new User object and set the properties
        User newUser = new User();
        newUser.setUsername(userInfo.getUsername());
        newUser.setEmail(userInfo.getEmail());
        newUser.setPassword(encodedPassword);
        newUser.setActive(true);
        newUser.setRoles(new HashSet<>());

        // Set the cabinet if cabinetId is provided
        if (userInfo.getCabinetId() != null) {
            Cabinet cabinet = new Cabinet();
            cabinet.setId(userInfo.getCabinetId());
            newUser.setCabinet(cabinet);
        }

        // Retrieve the roles by roleIds and assign them to the user
        Set<String> roleNames = new HashSet<>();
        if (userInfo.getRoleIds() != null && !userInfo.getRoleIds().isEmpty()) {
            Set<Role> roles = new HashSet<>();
            for (String roleId : userInfo.getRoleIds()) {
                Optional<Role> roleOptional = roleRepository.findById(roleId);
                roleOptional.ifPresent(role -> {
                    roles.add(role);
                    roleNames.add(role.getName());
                });
            }
            newUser.setRoles(roles);
        }

        // Save the new user to the repository
        newUser = userRepository.save(newUser);

        // Convert the saved user to UserInfo DTO
        UserInfo savedUserInfo = new UserInfo();
        savedUserInfo.setId(newUser.getId());
        savedUserInfo.setUsername(newUser.getUsername());
        savedUserInfo.setEmail(newUser.getEmail());
        savedUserInfo.setActive(newUser.isActive());
        savedUserInfo.setRoleIds(userInfo.getRoleIds());

        // Audit succès création
        auditService.logSuccess(
                getCurrentUser(),
                "CREATE",
                "User",
                newUser.getId(),
                newUser.getUsername(),
                null,
                Map.of(
                        "username", newUser.getUsername(),
                        "email", newUser.getEmail(),
                        "cabinetId", userInfo.getCabinetId(),
                        "roles", roleNames,
                        "active", true
                )
        );

        return savedUserInfo;
    }

    // ✅ ASSIGN ROLES - AVEC AUDIT
    @Override
    public UserInfo assignRolesToUser(String userId, List<String> roleIds) {
        Optional<User> userOptional = userRepository.findById(UUID.fromString(userId));
        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // Sauvegarder l'ancien état pour l'audit
            Set<String> oldRoleNames = user.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.toSet());

            Set<Role> roles = new HashSet<>();
            Set<String> newRoleNames = new HashSet<>();

            for (String roleId : roleIds) {
                Optional<Role> roleOptional = roleRepository.findById(String.valueOf(UUID.fromString(roleId)));
                roleOptional.ifPresent(role -> {
                    roles.add(role);
                    newRoleNames.add(role.getName());
                });
            }

            user.getRoles().addAll(roles);
            user = userRepository.save(user); // Save updated user

            // Convert User to UserInfo DTO
            UserInfo userInfo = new UserInfo();
            userInfo.setId(user.getId());
            userInfo.setUsername(user.getUsername());
            userInfo.setEmail(user.getEmail());
            userInfo.setActive(user.isActive());
            userInfo.setRoles(user.getRoles());

            // Audit succès assignation
            auditService.logSuccess(
                    getCurrentUser(),
                    "ASSIGN_ROLES",
                    "User",
                    user.getId(),
                    user.getUsername(),
                    Map.of("oldRoles", oldRoleNames),
                    Map.of("newRoles", newRoleNames, "addedRoles", newRoleNames)
            );

            return userInfo;
        }

        // Audit échec - utilisateur non trouvé
        auditService.logFailure(
                getCurrentUser(),
                "ASSIGN_ROLES",
                "User",
                userId,
                "User-" + userId,
                "Utilisateur introuvable avec l'identifiant: " + userId
        );
        throw new RuntimeException("Utilisateur introuvable avec l'identifiant: " + userId);
    }

    // ✅ GET - PAS D'AUDIT
    @Override
    public List<UserInfo> getAllUsers() {
        List<User> users = userRepository.findAll();
        // Convert each User to UserInfo DTO
        return users.stream().map(user -> {
            UserInfo userInfo = new UserInfo();
            userInfo.setId(user.getId());
            userInfo.setUsername(user.getUsername());
            userInfo.setEmail(user.getEmail());
            userInfo.setRoles(user.getRoles());
            userInfo.setActive(user.isActive());
            return userInfo;
        }).collect(Collectors.toList());
    }

    // ✅ ASSIGN ROLE - AVEC AUDIT
    @Override
    public User assignRoleToUser(String userId, String roleId) {
        Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));
        Optional<Role> roleOpt = roleRepository.findById(roleId);

        if (userOpt.isPresent() && roleOpt.isPresent()) {
            User user = userOpt.get();
            Role role = roleOpt.get();

            // Sauvegarder l'ancien état
            Set<String> oldRoleNames = user.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.toSet());

            user.getRoles().add(role);
            User savedUser = userRepository.save(user);

            // Audit succès
            auditService.logSuccess(
                    getCurrentUser(),
                    "ASSIGN_ROLE",
                    "User",
                    user.getId(),
                    user.getUsername(),
                    Map.of("oldRoles", oldRoleNames),
                    Map.of("addedRole", role.getName(), "newRoles",
                            user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
            );

            return savedUser;
        } else {
            // Audit échec
            auditService.logFailure(
                    getCurrentUser(),
                    "ASSIGN_ROLE",
                    "User",
                    userId,
                    "User-" + userId,
                    "Utilisateur ou rôle introuvable"
            );
            throw new RuntimeException("Utilisateur ou rôle introuvable");
        }
    }

    // ✅ REMOVE ROLE - AVEC AUDIT
    @Override
    public User removeRoleFromUser(String userId, String roleId) {
        Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));
        Optional<Role> roleOpt = roleRepository.findById(roleId);

        if (userOpt.isPresent() && roleOpt.isPresent()) {
            User user = userOpt.get();
            Role role = roleOpt.get();

            // Sauvegarder l'ancien état
            Set<String> oldRoleNames = user.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.toSet());

            user.getRoles().remove(role);
            User savedUser = userRepository.save(user);

            // Audit succès
            auditService.logSuccess(
                    getCurrentUser(),
                    "REMOVE_ROLE",
                    "User",
                    user.getId(),
                    user.getUsername(),
                    Map.of("oldRoles", oldRoleNames),
                    Map.of("removedRole", role.getName(), "newRoles",
                            user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
            );

            return savedUser;
        } else {
            // Audit échec
            auditService.logFailure(
                    getCurrentUser(),
                    "REMOVE_ROLE",
                    "User",
                    userId,
                    "User-" + userId,
                    "Utilisateur ou rôle introuvable"
            );
            throw new RuntimeException("Utilisateur ou rôle introuvable");
        }
    }

    // ✅ GET - PAS D'AUDIT
    @Override
    public List<User> getUsersByCabinetId(Long cabinetId) {
        return userRepository.findByCabinetId(cabinetId);
    }

    // ✅ UPDATE HOLD STATUS - AVEC AUDIT
    @Transactional
    @Override
    public void updateUserHoldStatus(UUID userId, boolean isHold) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            // Audit échec
            auditService.logFailure(
                    getCurrentUser(),
                    "UPDATE_HOLD",
                    "User",
                    userId,
                    "User-" + userId,
                    "Utilisateur non trouvé"
            );
            throw new RuntimeException("Utilisateur non trouvé.");
        }

        User user = userOptional.get();
        boolean oldHoldStatus = user.isHold();
        user.setHold(isHold);
        userRepository.save(user);

        // Audit succès
        auditService.logSuccess(
                getCurrentUser(),
                "UPDATE_HOLD",
                "User",
                user.getId(),
                user.getUsername(),
                Map.of("oldHoldStatus", oldHoldStatus),
                Map.of("newHoldStatus", isHold)
        );
    }

    // ✅ UPDATE DELETE STATUS - AVEC AUDIT
    @Transactional
    @Override
    public void updateUserDeleteStatus(UUID userId, boolean isDeleted) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            // Audit échec
            auditService.logFailure(
                    getCurrentUser(),
                    "UPDATE_DELETE",
                    "User",
                    userId,
                    "User-" + userId,
                    "Utilisateur non trouvé"
            );
            throw new RuntimeException("Utilisateur non trouvé.");
        }

        User user = userOptional.get();
        boolean oldDeleteStatus = user.isDeleted();
        user.setDeleted(isDeleted);
        userRepository.save(user);

        // Audit succès
        auditService.logSuccess(
                getCurrentUser(),
                "UPDATE_DELETE",
                "User",
                user.getId(),
                user.getUsername(),
                Map.of("oldDeleteStatus", oldDeleteStatus),
                Map.of("newDeleteStatus", isDeleted)
        );
    }

    // ✅ UPDATE PASSWORD - AVEC AUDIT
    @Transactional
    @Override
    public void updateUserPassword(UUID userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    // Audit échec
                    auditService.logFailure(
                            getCurrentUser(),
                            "UPDATE_PASSWORD",
                            "User",
                            userId,
                            "User-" + userId,
                            "Utilisateur non trouvé"
                    );
                    return new RuntimeException("Utilisateur non trouvé.");
                });

        String encryptedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encryptedPassword);
        userRepository.save(user);

        // Audit succès (ne pas inclure le mot de passe)
        auditService.logSuccess(
                getCurrentUser(),
                "UPDATE_PASSWORD",
                "User",
                user.getId(),
                user.getUsername(),
                null,
                Map.of("passwordChanged", true, "timestamp", new Date())
        );
    }

    // ✅ UPDATE USER INFO - AVEC AUDIT
    @Override
    public void updateUserInfo(UUID userId, UpdateUserInfoRequest request) {
        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // Sauvegarder l'ancien état
            User oldUser = new User();
            oldUser.setId(user.getId());
            oldUser.setUsername(user.getUsername());
            oldUser.setEmail(user.getEmail());
            oldUser.setRoles(new HashSet<>(user.getRoles()));

            // Mise à jour
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());

            // Fetch the Role based on roleId (String)
            Role role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> {
                        auditService.logFailure(
                                getCurrentUser(),
                                "UPDATE",
                                "User",
                                userId,
                                request.getUsername(),
                                "Rôle non trouvé"
                        );
                        return new RuntimeException("Rôle non trouvé");
                    });

            // Create a mutable Set and add the role to it
            Set<Role> roleSet = new HashSet<>();
            roleSet.add(role);
            user.setRoles(roleSet);

            userRepository.save(user);

            // Audit succès
            auditService.logSuccess(
                    getCurrentUser(),
                    "UPDATE",
                    "User",
                    user.getId(),
                    user.getUsername(),
                    oldUser,
                    user
            );

        } else {
            // Audit échec
            auditService.logFailure(
                    getCurrentUser(),
                    "UPDATE",
                    "User",
                    userId,
                    "User-" + userId,
                    "Utilisateur non trouvé"
            );
            throw new RuntimeException("Utilisateur non trouvé");
        }
    }
}