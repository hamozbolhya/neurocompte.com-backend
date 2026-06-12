package com.pacioli.core.controllers;

import com.pacioli.core.models.Permission;
import com.pacioli.core.services.serviceImp.PermissionServiceImpl;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin("*")
@RequestMapping("/permissions")
public class PermissionsController {

    private final PermissionServiceImpl permissionService;

    PermissionsController(PermissionServiceImpl permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<Permission> getAllPermissions() {
        return permissionService.getAllPermissions();
    }
}
