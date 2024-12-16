package com.pacioli.core.controllers;


import com.pacioli.core.DTO.RoleRequest;
import com.pacioli.core.models.Role;
import com.pacioli.core.services.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/roles")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @GetMapping
    public List<Role> fetchRoles(){
        return roleService.getAllRoles();
    }

    // Endpoint to create a new role with associated permissions
    @PostMapping
    public ResponseEntity<Role> createRole(@RequestBody RoleRequest roleRequest) {
        Role createdRole = roleService.createRole(roleRequest.getName(), roleRequest.getPermissionIds());
        return ResponseEntity.ok(createdRole);
    }

    // Endpoint to update an existing role by ID
    @PutMapping("/{id}")
    public ResponseEntity<Role> updateRole(
            @PathVariable UUID id,
            @RequestBody RoleRequest roleRequest) {
        //TODO WHEN YOU UPDATE ROLE AND PERMISSIONS ADD THE COMMENT LINE TO THE LOOP
        Role updatedRole = roleService.updateRole(id, roleRequest.getName());
                //, roleRequest.getPermissionIds()

        return ResponseEntity.ok(updatedRole);
    }

}
