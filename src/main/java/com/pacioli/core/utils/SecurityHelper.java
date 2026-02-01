package com.pacioli.core.utils;

import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

@Component
public class SecurityHelper {

    /**
     * Vérifie si l'utilisateur connecté possède le rôle PACIOLI.
     * @param principal L'utilisateur authentifié
     * @return true si l'utilisateur est un administrateur Pacioli
     */
    public boolean isPacioli(User principal) {
        if (principal == null) return false;

        return principal.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("PACIOLI"));
    }

    /**
     * Vérifie si l'utilisateur est un administrateur de cabinet ou utilisateur standard.
     */
    public boolean isClient(User principal) {
        if (principal == null) return false;

        return principal.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("Adminstrateur")
                        || auth.getAuthority().equals("Utilisateur"));
    }
}