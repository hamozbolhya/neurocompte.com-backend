package com.pacioli.core.controllers;

import com.pacioli.core.DTO.CabinetDTO;
import com.pacioli.core.DTO.CabinetStatsDTO;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.models.CabinetContract;
import com.pacioli.core.repositories.CabinetRepository;
import com.pacioli.core.repositories.DossierRepository;
import com.pacioli.core.repositories.PieceRepository;
import com.pacioli.core.services.CabinetService;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public Cabinet addCabinet(@RequestBody CabinetRequest request) {
        Optional<Cabinet> existingCabinet = cabinetService.findByIce(request.getIce());
        if (existingCabinet.isPresent()) {
            throw new RuntimeException("Le cabinet avec l'ICE donné existe déjà.");
        }

        Cabinet cabinet = new Cabinet();
        cabinet.setName(request.getName());
        cabinet.setAddress(request.getAddress());
        cabinet.setPhone(request.getPhone());
        cabinet.setIce(request.getIce());
        cabinet.setVille(request.getVille());

        return cabinetService.addCabinet(cabinet, request);
    }

    @Data
    public static class CabinetRequest {
        private String name;
        private String address;
        private String phone;
        private String ice;
        private String ville;
        private LocalDate contractStartDate;
        private LocalDate contractEndDate;
        private Integer normalStatementPieceQuota;
        private Integer bankStatementPageQuota;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Cabinet> updateCabinet(@PathVariable Long id, @RequestBody Cabinet cabinet) {
        return ResponseEntity.ok(cabinetService.updateCabinet(Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(cabinet, "cabinet")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCabinet(@PathVariable Long id) {
        cabinetService.deleteCabinet(Objects.requireNonNull(id, "id"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CabinetDTO> fetchCabinetById(@PathVariable Long id) {
        return ResponseEntity.ok(cabinetService.fetchCabinetById(Objects.requireNonNull(id, "id")));
    }

    @GetMapping("/{id}/contracts")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Page<CabinetContract>> fetchContractsByCabinetId(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(cabinetService.fetchContractsByCabinetId(Objects.requireNonNull(id, "id"), page, size));
    }

    @PostMapping("/{id}/renew")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<String> renewContract(@PathVariable Long id, @RequestBody @NonNull CabinetRequest renewalRequest) {
        cabinetService.renewContract(Objects.requireNonNull(id, "id"), renewalRequest);
        return ResponseEntity.ok("Le contrat a été renouvelé avec succès.");
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
                        .append("\"ville\":").append(cabinet.getVille() != null ? "\"" + cabinet.getVille().replace("\"", "\\\"") + "\"" : "null").append(",")
                        .append("\"contracts\":").append(jsonContracts(cabinet.getContracts()))
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
            cabinetService.assignCabinetToUser(Objects.requireNonNull(cabinetId, "cabinetId"),
                    Objects.requireNonNull(userId, "userId"));
            return "Cabinet assigned to user successfully.";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @DeleteMapping("/{userId}/unassign-cabinet")
    public ResponseEntity<?> unassignCabinetFromUser(@PathVariable UUID userId) {
        try {
            cabinetService.unassignCabinetFromUser(Objects.requireNonNull(userId, "userId"));
            return ResponseEntity.ok("Cabinet unassigned successfully from user");
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @GetMapping("/{cabinetId}/stats/{userEmail}")
    public ResponseEntity<CabinetStatsDTO> getCabinetStatsForUser(
            @PathVariable Long cabinetId,
            @PathVariable String userEmail) {

        CabinetStatsDTO stats = cabinetService.getCabinetStatsForUser(Objects.requireNonNull(cabinetId, "cabinetId"),
                userEmail);
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
                        
                        if (cabinet.getContracts() != null && !cabinet.getContracts().isEmpty()) {
                            // Find active contract or latest
                            com.pacioli.core.models.CabinetContract active = cabinet.getContracts().stream()
                                    .filter(com.pacioli.core.models.CabinetContract::isActive)
                                    .findFirst()
                                    .orElse(cabinet.getContracts().get(cabinet.getContracts().size() - 1));
                            
                            cabinetInfo.put("contractStartDate", active.getStartDate());
                            cabinetInfo.put("contractEndDate", active.getEndDate());
                            cabinetInfo.put("normalStatementPieceQuota", active.getNormalStatementPieceQuota());
                            cabinetInfo.put("bankStatementPageQuota", active.getBankStatementPageQuota());

                            LocalDateTime start = active.getStartDate().atStartOfDay();
                            LocalDateTime end = active.getEndDate().atTime(23, 59, 59);
                            long normalConsumption = pieceRepository.countNormalPiecesForCabinetInPeriod(cabinet.getId(), start, end);
                            Long bankConsumption = pieceRepository.sumBankPagesForCabinetInPeriod(cabinet.getId(), start, end);
                            
                            cabinetInfo.put("normalStatementPieceConsumption", normalConsumption);
                            cabinetInfo.put("bankStatementPageConsumption", bankConsumption != null ? bankConsumption : 0L);
                        }

                        return cabinetInfo;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(cabinetList);

        } catch (Exception e) {
            log.error("Error fetching cabinets with stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String jsonContracts(List<com.pacioli.core.models.CabinetContract> contracts) {
        if (contracts == null || contracts.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < contracts.size(); i++) {
            com.pacioli.core.models.CabinetContract c = contracts.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
                    .append("\"id\":").append(c.getId()).append(",")
                    .append("\"startDate\":\"").append(c.getStartDate()).append("\",")
                    .append("\"endDate\":\"").append(c.getEndDate()).append("\",")
                    .append("\"normalStatementPieceQuota\":").append(c.getNormalStatementPieceQuota()).append(",")
                    .append("\"bankStatementPageQuota\":").append(c.getBankStatementPageQuota()).append(",")
                    .append("\"normalStatementPieceConsumption\":").append(pieceRepository.countNormalPiecesForCabinetInPeriod(c.getCabinet().getId(), c.getStartDate().atStartOfDay(), c.getEndDate().atTime(23, 59, 59))).append(",")
                    .append("\"bankStatementPageConsumption\":").append(Optional.ofNullable(pieceRepository.sumBankPagesForCabinetInPeriod(c.getCabinet().getId(), c.getStartDate().atStartOfDay(), c.getEndDate().atTime(23, 59, 59))).orElse(0L)).append(",")
                    .append("\"active\":").append(c.isActive())
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
