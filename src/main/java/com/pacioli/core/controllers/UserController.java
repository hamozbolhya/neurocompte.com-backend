package com.pacioli.core.controllers;

import com.pacioli.core.DTO.UpdatePasswordRequest;
import com.pacioli.core.DTO.UpdateUserInfoRequest;
import com.pacioli.core.DTO.UserInfo;
import com.pacioli.core.models.User;
import com.pacioli.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserService userService;


    @GetMapping
    public List<UserInfo> fetchUser() {
        return userService.getAllUsers();
    }

    @PostMapping("/create")
    public ResponseEntity<UserInfo> createUser(@RequestBody UserInfo userInfo) {
        UserInfo createdUserInfo = userService.createUser(userInfo);
        return ResponseEntity.ok(createdUserInfo);
    }

    @PostMapping("/{userId}/roles")
    public ResponseEntity<UserInfo> assignRolesToUser(@PathVariable String userId, @RequestBody List<String> roleIds) {
        UserInfo updatedUserInfo = userService.assignRolesToUser(userId, roleIds);
        return ResponseEntity.ok(updatedUserInfo);
    }

    @PostMapping("/{userId}/roles/{roleId}")
    public User assignRoleToUser(@PathVariable String userId, @PathVariable String roleId) {
        return userService.assignRoleToUser(userId, roleId);
    }

    // Remove a role from a user
    @PostMapping("/{userId}/roles/{roleId}/remove")
    public User removeRoleFromUser(@PathVariable String userId, @PathVariable String roleId) {
        return userService.removeRoleFromUser(userId, roleId);
    }

    @GetMapping("/by-cabinet/{cabinetId}")
    public List<User> getUsersByCabinetId(@PathVariable Long cabinetId) {
        return userService.getUsersByCabinetId(cabinetId);
    }


    @PutMapping("/{userId}/hold")
    public ResponseEntity<String> updateHoldStatus(
            @PathVariable UUID userId,
            @RequestParam boolean isHold
    ) {
        userService.updateUserHoldStatus(userId, isHold);
        return ResponseEntity.ok("Le statut de l'utilisateur a été mis à jour avec succès.");
    }
    @PutMapping("/{userId}/delete")
    public ResponseEntity<String> updateDeleteStatus(
            @PathVariable UUID userId,
            @RequestParam boolean isDelete
    ) {
        userService.updateUserDeleteStatus(userId, isDelete);
        return ResponseEntity.ok("L'utilisateur est supprimé.");
    }

    @PutMapping("/{userId}/password")
    public ResponseEntity<String> updatePassword(
            @PathVariable UUID userId,
            @RequestBody UpdatePasswordRequest request
    ) {
        userService.updateUserPassword(userId, request.getNewPassword());
        return ResponseEntity.ok("Mot de passe mis à jour avec succès.");
    }

    @PutMapping("/update/{userId}")
    public ResponseEntity<?> updateUserInfo(
            @PathVariable UUID userId,
            @RequestBody UpdateUserInfoRequest request) {
        try {
            log.info("Received request to update user with ID: {}", userId);
            log.info("Update details: Username={}, Email={}, RoleId={}", request.getUsername(), request.getEmail(), request.getRoleId());

            userService.updateUserInfo(userId, request);
            return ResponseEntity.ok().body("User updated successfully.");
        } catch (Exception e) {
            log.error("Error updating user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating user: " + e.getMessage());
        }
    }
}
