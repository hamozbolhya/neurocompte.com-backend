package com.pacioli.core.services.serviceImp;

import com.pacioli.core.DTO.UpdateUserInfoRequest;
import com.pacioli.core.DTO.UserInfo;
import com.pacioli.core.models.Role;
import com.pacioli.core.models.User;
import com.pacioli.core.repositories.RoleRepository;
import com.pacioli.core.repositories.UserRepository;
import com.pacioli.core.services.UserService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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


    @Override
    public UserInfo createUser(UserInfo userInfo) {
        // Encrypt the password using BCrypt
        String encodedPassword = passwordEncoder.encode(userInfo.getPassword());

        // Create a new User object and set the properties
        User newUser = new User();
        newUser.setUsername(userInfo.getUsername());
        newUser.setEmail(userInfo.getEmail());
        newUser.setPassword(encodedPassword); // Save the encoded password
        newUser.setActive(true); // Default to active
        newUser.setRoles(new HashSet<>()); // Initialize empty roles

        // Retrieve the roles by roleIds and assign them to the user
        if (userInfo.getRoleIds() != null && !userInfo.getRoleIds().isEmpty()) {
            Set<Role> roles = new HashSet<>();
            for (String roleId : userInfo.getRoleIds()) {
                Optional<Role> roleOptional = roleRepository.findById(roleId);
                roleOptional.ifPresent(roles::add);  // Add the role to the set if found
            }
            newUser.setRoles(roles);  // Assign the roles to the user
        }

        // Save the new user to the repository
        newUser = userRepository.save(newUser);

        // Convert the saved user to UserInfo DTO
        UserInfo savedUserInfo = new UserInfo();
        savedUserInfo.setId(newUser.getId());
        savedUserInfo.setUsername(newUser.getUsername());
        savedUserInfo.setEmail(newUser.getEmail());
        savedUserInfo.setActive(newUser.isActive());
        savedUserInfo.setRoleIds(userInfo.getRoleIds());  // Set the roleIds in the DTO

        return savedUserInfo;
    }




    @Override
    public UserInfo assignRolesToUser(String userId, List<String> roleIds) {
        Optional<User> userOptional = userRepository.findById(UUID.fromString(userId));
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            Set<Role> roles = new HashSet<>();

            for (String roleId : roleIds) {
                Optional<Role> roleOptional = roleRepository.findById(String.valueOf(UUID.fromString(roleId)));
                roleOptional.ifPresent(roles::add);
            }

            user.getRoles().addAll(roles);
            user = userRepository.save(user); // Save updated user

            // Convert User to UserInfo DTO
            UserInfo userInfo = new UserInfo();
            userInfo.setId(user.getId());
            userInfo.setUsername(user.getUsername());
            userInfo.setEmail(user.getEmail());
            userInfo.setActive(user.isActive());
            userInfo.setRoles(user.getRoles()); // Set roles

            return userInfo;
        }
        throw new RuntimeException("Utilisateur introuvable avec l'identifiant: " + userId);
    }


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


    @Override
    public User assignRoleToUser(String userId, String roleId) {
        Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));
        Optional<Role> roleOpt = roleRepository.findById(roleId);

        if (userOpt.isPresent() && roleOpt.isPresent()) {
            User user = userOpt.get();
            Role role = roleOpt.get();

            user.getRoles().add(role);  // Add the role to the user's roles
            return userRepository.save(user);  // Save the updated user
        } else {
            throw new RuntimeException("Utilisateur ou rôle introuvable");
        }
    }

    @Override
    public User removeRoleFromUser(String userId, String roleId) {
        Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));
        Optional<Role> roleOpt = roleRepository.findById(roleId);

        if (userOpt.isPresent() && roleOpt.isPresent()) {
            User user = userOpt.get();
            Role role = roleOpt.get();

            user.getRoles().remove(role);  // Remove the role from the user's roles
            return userRepository.save(user);  // Save the updated user
        } else {
            throw new RuntimeException("Utilisateur ou rôle introuvable");
        }
    }

    @Override
    public List<User> getUsersByCabinetId(Long cabinetId) {
        return userRepository.findByCabinetId(cabinetId);
    }

    @Transactional
    @Override
    public void updateUserHoldStatus(UUID userId, boolean isHold) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            throw new RuntimeException("Utilisateur non trouvé.");
        }
        user.get().setHold(isHold); // Update the isHold field
        userRepository.save(user.get()); // Save the updated user
    }
    @Transactional
    @Override
    public void updateUserDeleteStatus(UUID userId, boolean isDeleted) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            throw new RuntimeException("Utilisateur non trouvé.");
        }
        user.get().setDeleted(isDeleted); // Update the isHold field
        userRepository.save(user.get()); // Save the updated user
    }


    @Transactional
    @Override
    public void updateUserPassword(UUID userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé."));

        String encryptedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encryptedPassword);
        userRepository.save(user);
    }


    @Override
    public void updateUserInfo(UUID userId, UpdateUserInfoRequest request) {
        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());

            // Fetch the Role based on roleId (String)
            Role role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Rôle non trouvé"));

            // Create a mutable Set and add the role to it
            Set<Role> roleSet = new HashSet<>();
            roleSet.add(role);
            user.setRoles(roleSet);

            userRepository.save(user);
        } else {
            throw new RuntimeException("Utilisateur non trouvé");
        }
    }
}
