package com.pacioli.core.config;

import com.pacioli.core.models.Permission;
import com.pacioli.core.repositories.PermissionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Configuration
public class PermissionConfig {

    @Bean
    @Transactional
    CommandLineRunner initializePermissions(PermissionRepository permissionRepository) {
        return args -> {
            // Define all permissions
            List<String> permissions = Arrays.asList(
                    "Modification du nom de cabinet",
                    "Ajout de collaborateur",
                    "modification de collaborateur",
                    "suppression de collaborateur",
                    "Suppression de pièces uploadées",
                    "Modification des dates d’exercices",
                    "Ajout de dossier"
            );

            // Save each permission if it doesn't already exist
            permissions.forEach(permissionName -> {
                permissionRepository.findByName(permissionName)
                        .orElseGet(() -> {
                            Permission permission = new Permission();
                            permission.setName(permissionName);
                            return permissionRepository.save(permission);
                        });
            });
        };
    }
}
