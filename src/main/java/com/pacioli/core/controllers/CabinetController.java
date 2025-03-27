package com.pacioli.core.controllers;

import com.pacioli.core.DTO.CabinetDTO;
import com.pacioli.core.DTO.CabinetStatsDTO;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.services.CabinetService;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/cabinets")
public class CabinetController {
    @Autowired
    private CabinetService cabinetService;

    @PostMapping
    public Cabinet addCabinet(@RequestBody Cabinet cabinet) {
        Optional<Cabinet> existingCabinet = cabinetService.findByIce(cabinet.getIce());
        if (existingCabinet.isPresent()) {
            throw new RuntimeException("Le cabinet avec l'ICE donné existe déjà.");
        }
        return cabinetService.addCabinet(cabinet);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Cabinet> updateCabinet(@PathVariable Long id, @RequestBody Cabinet cabinet) {
        return ResponseEntity.ok(cabinetService.updateCabinet(id, cabinet));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCabinet(@PathVariable Long id) {
        cabinetService.deleteCabinet(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CabinetDTO> fetchCabinetById(@PathVariable Long id) {
        return ResponseEntity.ok(cabinetService.fetchCabinetById(id));
    }

    @GetMapping
    public ResponseEntity<List<CabinetDTO>> fetchAllCabinets() {
        List<CabinetDTO> cabinets = cabinetService.fetchAllCabinets();
        return ResponseEntity.ok(cabinets);
    }

    @PostMapping("/{cabinetId}/assign-user/{userId}")
    public String assignCabinetToUser(
            @PathVariable Long cabinetId,
            @PathVariable UUID userId) {
        try {
            cabinetService.assignCabinetToUser(cabinetId, userId);
            return "Cabinet assigned to user successfully.";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @DeleteMapping("/{userId}/unassign-cabinet")
    public ResponseEntity<?> unassignCabinetFromUser(@PathVariable UUID userId) {
        try {
            cabinetService.unassignCabinetFromUser(userId);
            return ResponseEntity.ok("Cabinet unassigned successfully from user");
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @GetMapping("/{cabinetId}/stats/{userEmail}")
    public ResponseEntity<CabinetStatsDTO> getCabinetStatsForUser(
            @PathVariable Long cabinetId,
            @PathVariable String userEmail) {

        CabinetStatsDTO stats = cabinetService.getCabinetStatsForUser(cabinetId, userEmail);
        return ResponseEntity.ok(stats);
    }

}
