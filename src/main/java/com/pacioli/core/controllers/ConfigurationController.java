package com.pacioli.core.controllers;

import com.pacioli.core.services.ConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/configurations")
@RequiredArgsConstructor
@Tag(name = "Configuration Management", description = "Manage application configurations")
public class ConfigurationController {

    private final ConfigurationService configurationService;

    @GetMapping
    @PreAuthorize("hasRole('PACIOLI')") // Only PACIOLI can access
    @Operation(summary = "Get all configurations")
    public ResponseEntity<Map<String, String>> getAllConfigurations() {
        return ResponseEntity.ok(configurationService.getAllConfigurations());
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasRole('PACIOLI')") // Only PACIOLI can access
    public ResponseEntity<Map<String, String>> getConfiguration(@PathVariable String key) {
        String value = configurationService.getConfigValue(key, null);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("key", key, "value", value));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('PACIOLI')") // Only PACIOLI can access
    public ResponseEntity<?> updateConfiguration(
            @PathVariable String key,
            @RequestBody UpdateConfigRequest request) {

        configurationService.updateConfig(key, request.getValue(), request.getUpdatedBy());
        return ResponseEntity.ok(Map.of(
                "message", "Configuration updated successfully",
                "key", key,
                "value", request.getValue()
        ));
    }

    @PostMapping("/refresh-cache")
    @PreAuthorize("hasRole('PACIOLI')") // Only PACIOLI can access
    public ResponseEntity<?> refreshCache() {
        configurationService.forceRefreshCache();
        return ResponseEntity.ok(Map.of("message", "Configuration cache refreshed"));
    }

    // DTO for update requests
    public static class UpdateConfigRequest {
        private String value;
        private String updatedBy;

        // Getters and setters
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    }
}