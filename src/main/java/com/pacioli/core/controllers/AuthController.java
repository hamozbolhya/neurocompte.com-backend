package com.pacioli.core.controllers;

import com.pacioli.core.DTO.LoginRequest;
import com.pacioli.core.DTO.UpdatePasswordRequest;
import com.pacioli.core.DTO.UserRegistrationRequest;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.models.User;
import com.pacioli.core.repositories.UserRepository;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@CrossOrigin("*")
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    @Lazy
    private AuditService auditService;

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest user) {
        Optional<User> optionalUser = userRepository.findByEmail(user.getEmail());

        if (optionalUser.isPresent() && passwordEncoder.matches(user.getPassword(), optionalUser.get().getPassword())) {
            User foundUser = optionalUser.get();

            // Check if the user's account is on hold
            if (foundUser.isHold()) {
                auditService.logFailure(
                        foundUser,
                        "LOGIN_FAILED",
                        "User",
                        foundUser.getId(),
                        foundUser.getUsername(),
                        "Compte suspendu"
                );
                return ResponseEntity.status(403).body("Votre compte est suspendu.");
            }

            // Check if the user's account is deleted
            if (foundUser.isDeleted()) {
                auditService.logFailure(
                        foundUser,
                        "LOGIN_FAILED",
                        "User",
                        foundUser.getId(),
                        foundUser.getUsername(),
                        "Compte supprimé"
                );
                return ResponseEntity.status(403).body("Ce compte n'existe plus.");
            }

            // Assuming your User entity has a getCabinet() method to fetch the related Cabinet
            Cabinet cabinet = foundUser.getCabinet();
            Long cabinetId = cabinet != null ? cabinet.getId() : null;
            String cabinetName = cabinet != null ? cabinet.getName() : null;

            // Fetch roles as a list of strings
            List<String> roles = foundUser.getRoles().stream()
                    .map(role -> role.getName())
                    .collect(Collectors.toList());

            // Generate token with additional information
            String token = jwtUtil.generateToken(
                    foundUser.getUsername(),
                    foundUser.getEmail(),
                    cabinetId,
                    cabinetName,
                    roles,
                    foundUser.isActive()
            );

            // Log successful login
            auditService.logSuccess(
                    foundUser,
                    "LOGIN",
                    "User",
                    foundUser.getId(),
                    foundUser.getUsername(),
                    null,
                    null
            );

            return ResponseEntity.ok(token);
        } else {
            // Log failed login attempt
            String errorMessage = "Identifiants invalides";
            auditService.logFailure(
                    null,
                    "LOGIN_FAILED",
                    "User",
                    null,
                    user.getEmail(),
                    errorMessage
            );
            return ResponseEntity.status(401).body(errorMessage);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody UserRegistrationRequest request) {
        if (request.getUsername() == null || request.getUsername().isEmpty()) {
            return ResponseEntity.badRequest().body("Username is required");
        }

        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            return ResponseEntity.badRequest().body("Password is required");
        }

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            auditService.logFailure(
                    null,
                    "REGISTER_FAILED",
                    "User",
                    null,
                    request.getUsername(),
                    "Username already taken"
            );
            return ResponseEntity.status(409).body("Username is already taken");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());

        // Save the user to the database
        User savedUser = userRepository.save(user);

        // Log successful registration
        auditService.logSuccess(
                savedUser,
                "REGISTER",
                "User",
                savedUser.getId(),
                savedUser.getUsername(),
                null,
                savedUser
        );

        return ResponseEntity.ok("User registered successfully");
    }

    @PutMapping("/change-password")
    public ResponseEntity<?> updatePassword(@RequestBody UpdatePasswordRequest request) {
        if (request.getCurrentPassword() == null || request.getNewPassword() == null) {
            String errorMessage = "Current password or new password cannot be null";
            auditService.logFailure(
                    null,
                    "PASSWORD_CHANGE_FAILED",
                    "User",
                    null,
                    request.getEmail(),
                    errorMessage
            );
            throw new IllegalArgumentException(errorMessage);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    String errorMessage = "Utilisateur non trouvé: " + request.getEmail();
                    auditService.logFailure(
                            null,
                            "PASSWORD_CHANGE_FAILED",
                            "User",
                            null,
                            request.getEmail(),
                            errorMessage
                    );
                    return new RuntimeException(errorMessage);
                });

        // Log received data for debugging
        System.out.println("Current Password: " + request.getCurrentPassword());
        System.out.println("New Password: " + request.getNewPassword());

        // Verify the current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            String errorMessage = "Mot de passe actuel incorrect";
            auditService.logFailure(
                    user,
                    "PASSWORD_CHANGE_FAILED",
                    "User",
                    user.getId(),
                    user.getUsername(),
                    errorMessage
            );
            return ResponseEntity.status(400).body(errorMessage);
        }

        // Store old password hash for audit (optional)
        String oldPasswordHash = user.getPassword();

        // Encrypt the new password and set it
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setActive(true);
        User updatedUser = userRepository.save(user);

        // Log successful password change
        auditService.logSuccess(
                user,
                "PASSWORD_CHANGE",
                "User",
                user.getId(),
                user.getUsername(),
                oldPasswordHash, // You might want to create a DTO with only necessary fields instead of the whole user
                updatedUser
        );

        return ResponseEntity.ok("Mot de passe changé avec succès.");
    }
}