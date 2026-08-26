package com.pacioli.core.DTO;

import lombok.Data;


@Data
public class UpdateUserInfoRequest {
    private String username;
    private String email;
    private String roleId;
}
