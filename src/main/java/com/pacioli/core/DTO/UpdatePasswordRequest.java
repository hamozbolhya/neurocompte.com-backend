package com.pacioli.core.DTO;

import lombok.Data;

@Data
public class UpdatePasswordRequest {
    private String email;
    private String currentPassword;
    private String newPassword;
}

