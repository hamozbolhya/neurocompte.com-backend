package com.pacioli.core.beans;

import com.pacioli.core.models.Role;
import com.pacioli.core.models.User;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.repositories.RoleRepository;
import com.pacioli.core.repositories.UserRepository;
import com.pacioli.core.repositories.CabinetRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import java.util.Set;

@Configuration
public class RoleInitializer {

    @Bean
    CommandLineRunner initRolesAndUsers(RoleRepository roleRepository,
                                        UserRepository userRepository,
                                        CabinetRepository cabinetRepository,
                                        PasswordEncoder passwordEncoder) {
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

            // Check if the default user exists - check by EMAIL instead of username
            Optional<User> existingUser = userRepository.findByEmail("pacioli.plateform@pacioli.com");
            if (existingUser.isEmpty()) {
                // Also check by username as a safety measure
                Optional<User> existingUserByUsername = userRepository.findByUsername("pacioli.plateform@pacioli.com");
                if (existingUserByUsername.isEmpty()) {
                    // Get the first available cabinet or create a default one
                    Cabinet cabinet = cabinetRepository.findAll().stream()
                            .findFirst()
                            .orElseGet(() -> {
                                Cabinet newCabinet = new Cabinet();
                                newCabinet.setName("PACIOLI_SYSTEM");
                                return cabinetRepository.save(newCabinet);
                            });

                    // Create the default user
                    User pacioliUser = new User();
                    pacioliUser.setUsername("pacioli.plateform@pacioli.com");
                    pacioliUser.setEmail("pacioli.plateform@pacioli.com");
                    pacioliUser.setPassword(passwordEncoder.encode("pacioli12@BO"));
                    pacioliUser.setActive(false);
                    pacioliUser.setHold(false);
                    pacioliUser.setRoles(Set.of(pacioliRole));
                    pacioliUser.setCabinet(cabinet);

                    userRepository.save(pacioliUser);
                }
            }
        };
    }
}