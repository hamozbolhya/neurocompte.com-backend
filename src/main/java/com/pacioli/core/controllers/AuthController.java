package com.pacioli.core.controllers;

import com.pacioli.core.DTO.LoginRequest;
import com.pacioli.core.DTO.UpdatePasswordRequest;
import com.pacioli.core.DTO.UserRegistrationRequest;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.models.User;
import com.pacioli.core.repositories.UserRepository;
import com.pacioli.core.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
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
    private JwtUtil jwtUtil; // Inject JwtUtil

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest user) {
        Optional<User> optionalUser = userRepository.findByEmail(user.getEmail());
        if (optionalUser.isPresent() && passwordEncoder.matches(user.getPassword(), optionalUser.get().getPassword())) {
            User foundUser = optionalUser.get();
            // Check if the user's account is on hold
            if (foundUser.isHold()) {
                return ResponseEntity.status(403).body("Votre compte est suspendu.");
            }

            // Check if the user's account is deleted
            if (foundUser.isDeleted()) {
                return ResponseEntity.status(403).body("Ce compte n'existe plus.");
            }
            // Assuming your User entity has a getCabinet() method to fetch the related Cabinet
            Cabinet cabinet = foundUser.getCabinet();
            Long cabinetId = cabinet != null ? cabinet.getId() : null;
            String cabinetName = cabinet != null ? cabinet.getName() : null;

            // Fetch roles as a list of strings
            List<String> roles = foundUser.getRoles().stream()
                    .map(role -> role.getName()) // Assuming Role has getName() method
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

            return ResponseEntity.ok(token); // Return the token
        } else {
            return ResponseEntity.status(401).body("Invalid credentials");
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
            return ResponseEntity.status(409).body("Username is already taken");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());

        // Save the user to the database
        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully");
    }

    @PutMapping("/change-password")
    public String updatePassword(@RequestBody UpdatePasswordRequest request) {
        if (request.getCurrentPassword() == null || request.getNewPassword() == null) {
            throw new IllegalArgumentException("Current password or new password cannot be null");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé."));

        // Log received data for debugging
        System.out.println("Current Password: " + request.getCurrentPassword());
        System.out.println("New Password: " + request.getNewPassword());

        // Verify the current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Mot de passe actuel incorrect.");
        }

        // Encrypt the new password and set it
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setActive(true);
        userRepository.save(user);

        return "Mot de passe changé avec succès.";
    }




}