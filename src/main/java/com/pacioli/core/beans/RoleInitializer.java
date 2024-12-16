package com.pacioli.core.beans;

import com.pacioli.core.models.Role;
import com.pacioli.core.models.User;
import com.pacioli.core.repositories.RoleRepository;
import com.pacioli.core.repositories.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.Set;

@Configuration
public class RoleInitializer {


    @Bean
    CommandLineRunner initRolesAndUsers(RoleRepository roleRepository, UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        return args -> {
            // Create default roles
            Role adminRole = roleRepository.findByName("Adminstrateur").orElseGet(() -> {
                Role role = new Role();
                role.setName("Adminstrateur");
                return roleRepository.save(role);
            });

            Role utilisateurRole = roleRepository.findByName("Utilisateur").orElseGet(() -> {
                Role role = new Role();
                role.setName("Utilisateur");
                return roleRepository.save(role);
            });

            Role pacioliRole = roleRepository.findByName("PACIOLI").orElseGet(() -> {
                Role role = new Role();
                role.setName("PACIOLI");
                return roleRepository.save(role);
            });

            // Check if the default user exists
            Optional<User> existingUser = userRepository.findByUsername("pacioli.plateform@pacioli.com");
            if (existingUser.isEmpty()) {
                // Create the default user
                User pacioliUser = new User();
                pacioliUser.setUsername("pacioli.plateform@pacioli.com");
                pacioliUser.setEmail("pacioli.plateform@pacioli.com");
                pacioliUser.setPassword(passwordEncoder.encode("pacioli12@BO"));
                pacioliUser.setActive(false);
                pacioliUser.setHold(false);
                pacioliUser.setRoles(Set.of(pacioliRole));

                userRepository.save(pacioliUser);
            }
        };
    }
}
