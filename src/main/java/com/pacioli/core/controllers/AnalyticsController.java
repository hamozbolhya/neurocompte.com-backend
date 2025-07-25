// AnalyticsController.java - Fixed version
package com.pacioli.core.controllers;

import com.pacioli.core.DTO.analytics.AdminAnalyticsDTO;
import com.pacioli.core.DTO.analytics.CabinetAnalytics;
import com.pacioli.core.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<AdminAnalyticsDTO> getAdminAnalytics() {
        log.info("Admin analytics requested");

        try {
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalytics();
            log.info("Admin analytics generated successfully");
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            log.error("Error generating admin analytics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/period")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<AdminAnalyticsDTO> getAdminAnalyticsForPeriod(
            @RequestParam("startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam("endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Admin analytics requested for period: {} to {}", startDate, endDate);

        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start date {} is after end date {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        try {
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalyticsForPeriod(startDate, endDate);
            log.info("Admin analytics for period generated successfully");
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            log.error("Error generating admin analytics for period", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/cabinets")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<AdminAnalyticsDTO> getCabinetAnalytics() {
        log.info("Cabinet analytics requested");

        try {
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalytics();
            AdminAnalyticsDTO cabinetOnlyAnalytics = AdminAnalyticsDTO.builder()
                    .cabinetAnalytics(analytics.getCabinetAnalytics())
                    .build();

            log.info("Cabinet analytics generated successfully");
            return ResponseEntity.ok(cabinetOnlyAnalytics);
        } catch (Exception e) {
            log.error("Error generating cabinet analytics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/pieces")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Object> getPieceStatistics() {
        log.info("Piece statistics requested");

        try {
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalytics();
            log.info("Piece statistics generated successfully");
            return ResponseEntity.ok(analytics.getPieceStats());
        } catch (Exception e) {
            log.error("Error generating piece statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/dossiers")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Object> getDossierStatistics() {
        log.info("Dossier statistics requested");

        try {
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalytics();
            log.info("Dossier statistics generated successfully");
            return ResponseEntity.ok(analytics.getDossierStats());
        } catch (Exception e) {
            log.error("Error generating dossier statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/historique")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Object> getHistoriqueStatistics() {
        log.info("Historique statistics requested");

        try {
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalytics();
            log.info("Historique statistics generated successfully");
            return ResponseEntity.ok(analytics.getHistoriqueStats());
        } catch (Exception e) {
            log.error("Error generating historique statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/forced-pieces")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Object> getForcedPiecesStatistics() {
        log.info("Forced pieces statistics requested");

        try {
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalytics();

            // Create a focused response for forced pieces
            Map<String, Object> forcedPiecesStats = new HashMap<>();
            forcedPiecesStats.put("totalForcedPieces", analytics.getPieceStats().getForcedPieces());
            forcedPiecesStats.put("forcedPiecesByCabinet", analytics.getPieceStats().getForcedPiecesByCabinet());
            forcedPiecesStats.put("forcedPiecesByDossier", analytics.getPieceStats().getForcedPiecesByDossier());

            // Add cabinet names for better readability
            Map<Long, String> cabinetNames = new HashMap<>();
            if (analytics.getCabinetAnalytics() != null) {
                for (CabinetAnalytics cabinet : analytics.getCabinetAnalytics()) {  // Remove AdminAnalyticsDTO.
                    cabinetNames.put(cabinet.getCabinetId(), cabinet.getCabinetName());
                }
            }
            forcedPiecesStats.put("cabinetNames", cabinetNames);

            log.info("Forced pieces statistics generated successfully");
            return ResponseEntity.ok(forcedPiecesStats);
        } catch (Exception e) {
            log.error("Error generating forced pieces statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/forced-pieces/period")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Object> getForcedPiecesStatisticsForPeriod(
            @RequestParam("startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam("endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Forced pieces statistics requested for period: {} to {}", startDate, endDate);

        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start date {} is after end date {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        try {
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalyticsForPeriod(startDate, endDate);

            Map<String, Object> forcedPiecesStats = new HashMap<>();
            forcedPiecesStats.put("totalForcedPieces", analytics.getPieceStats().getForcedPieces());
            forcedPiecesStats.put("forcedPiecesByCabinet", analytics.getPieceStats().getForcedPiecesByCabinet());
            forcedPiecesStats.put("forcedPiecesByDossier", analytics.getPieceStats().getForcedPiecesByDossier());
            forcedPiecesStats.put("period", Map.of("startDate", startDate, "endDate", endDate));

            log.info("Forced pieces statistics for period generated successfully");
            return ResponseEntity.ok(forcedPiecesStats);
        } catch (Exception e) {
            log.error("Error generating forced pieces statistics for period", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/cabinet/{cabinetId}/forced-pieces")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Object> getCabinetForcedPiecesStatistics(@PathVariable Long cabinetId) {
        log.info("Forced pieces statistics requested for cabinet: {}", cabinetId);

        try {
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalytics();

            // Find the specific cabinet - CHANGED: Remove AdminAnalyticsDTO.
            CabinetAnalytics cabinetAnalytics = null;
            if (analytics.getCabinetAnalytics() != null) {
                cabinetAnalytics = analytics.getCabinetAnalytics().stream()
                        .filter(c -> c.getCabinetId().equals(cabinetId))
                        .findFirst()
                        .orElse(null);
            }

            if (cabinetAnalytics == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> cabinetForcedStats = new HashMap<>();
            cabinetForcedStats.put("cabinetId", cabinetId);
            cabinetForcedStats.put("cabinetName", cabinetAnalytics.getCabinetName());
            cabinetForcedStats.put("totalForcedPieces", cabinetAnalytics.getTotalForcedPieces());
            cabinetForcedStats.put("forcedPiecesByDossier", cabinetAnalytics.getForcedPiecesByDossier());

            log.info("Cabinet {} forced pieces statistics generated successfully", cabinetId);
            return ResponseEntity.ok(cabinetForcedStats);
        } catch (Exception e) {
            log.error("Error generating forced pieces statistics for cabinet {}", cabinetId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}