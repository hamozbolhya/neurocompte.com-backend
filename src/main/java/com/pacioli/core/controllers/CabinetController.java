package com.pacioli.core.controllers;

import com.pacioli.core.DTO.CabinetDTO;
import com.pacioli.core.DTO.CabinetStatsDTO;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.repositories.CabinetRepository;
import com.pacioli.core.repositories.DossierRepository;
import com.pacioli.core.repositories.PieceRepository;
import com.pacioli.core.services.CabinetService;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/cabinets")
public class CabinetController {
    @Autowired
    private CabinetService cabinetService;
    @Autowired
    private  PieceRepository pieceRepository;
    @Autowired
    private  CabinetRepository cabinetRepository;
    @Autowired
    private DossierRepository dossierRepository;

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
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<String> fetchAllCabinets() {
        try {
            List<Cabinet> cabinets = cabinetRepository.findAll();

            // Manual JSON serialization to avoid Hibernate proxy issues
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < cabinets.size(); i++) {
                Cabinet cabinet = cabinets.get(i);
                if (i > 0) json.append(",");
                json.append("{")
                        .append("\"id\":").append(cabinet.getId()).append(",")
                        .append("\"name\":\"").append(cabinet.getName() != null ? cabinet.getName().replace("\"", "\\\"") : "").append("\",")
                        .append("\"address\":").append(cabinet.getAddress() != null ? "\"" + cabinet.getAddress().replace("\"", "\\\"") + "\"" : "null").append(",")
                        .append("\"phone\":").append(cabinet.getPhone() != null ? "\"" + cabinet.getPhone() + "\"" : "null").append(",")
                        .append("\"ice\":").append(cabinet.getIce() != null ? "\"" + cabinet.getIce() + "\"" : "null").append(",")
                        .append("\"ville\":").append(cabinet.getVille() != null ? "\"" + cabinet.getVille().replace("\"", "\\\"") + "\"" : "null")
                        .append("}");
            }
            json.append("]");

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(json.toString());

        } catch (Exception e) {
            log.error("Error fetching cabinets: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("[]");
        }
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

    @GetMapping("/with-stats")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<List<Map<String, Object>>> getAllCabinetsWithStats() {
        try {
            List<Cabinet> cabinets = cabinetRepository.findAll();

            List<Map<String, Object>> cabinetList = cabinets.stream()
                    .map(cabinet -> {
                        Map<String, Object> cabinetInfo = new HashMap<>();
                        cabinetInfo.put("id", cabinet.getId());
                        cabinetInfo.put("name", cabinet.getName());

                        // Add some basic stats for the dropdown
                        Long totalPieces = pieceRepository.countByDossierCabinetId(cabinet.getId());
                        Long totalDossiers = dossierRepository.countByCabinetId(cabinet.getId());

                        cabinetInfo.put("totalPieces", totalPieces != null ? totalPieces : 0L);
                        cabinetInfo.put("totalDossiers", totalDossiers != null ? totalDossiers : 0L);

                        return cabinetInfo;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(cabinetList);

        } catch (Exception e) {
            log.error("Error fetching cabinets with stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
