package com.pacioli.core.DTO;

import lombok.Data;

@Data
public class UpdatePasswordRequest {
    private String email;
    private String currentPassword;
    private String newPassword;

    // Optionnel: ajouter des champs pour l'audit
    private String ipAddress;
    private String userAgent;
}

