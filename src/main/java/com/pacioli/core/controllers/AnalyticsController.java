// AnalyticsController.java - Fixed version
package com.pacioli.core.controllers;

import com.pacioli.core.DTO.analytics.AdminAnalyticsDTO;
import com.pacioli.core.DTO.analytics.CabinetAnalytics;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.repositories.CabinetRepository;
import com.pacioli.core.repositories.DossierRepository;
import com.pacioli.core.repositories.EcritureRepository;
import com.pacioli.core.repositories.LineRepository;
import com.pacioli.core.repositories.PieceRepository;
import com.pacioli.core.repositories.UserRepository;
import com.pacioli.core.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final EcritureRepository ecritureRepository;
    private final LineRepository lineRepository;
    private final PieceRepository pieceRepository;
    private final CabinetRepository cabinetRepository;
    private final UserRepository userRepository;
    private final DossierRepository dossierRepository;

    @Autowired
    private CacheManager cacheManager;

    @GetMapping("/cabinet")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Map<String, Object>> getCabinetAnalytics(
            @RequestParam("cabinetId") Long cabinetId,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {

        log.info("Cabinet analytics requested for cabinet: {} (refresh: {})", cabinetId, refresh);

        try {
            // Clear cache if refresh is requested
            if (refresh) {
                Cache adminCache = cacheManager.getCache("adminAnalytics");
                if (adminCache != null) {
                    adminCache.clear();
                    log.info("Cache cleared due to refresh parameter");
                }
            }

            // Get the cabinet
            Cabinet cabinet = cabinetRepository.findById(cabinetId)
                    .orElseThrow(() -> new IllegalArgumentException("Cabinet not found with ID: " + cabinetId));

            // Generate cabinet-specific analytics using the service
            Map<String, Object> cabinetAnalytics = analyticsService.getCabinetAnalytics(cabinetId);

            // Add cabinet info (if not already included by the service)
            cabinetAnalytics.put("cabinetInfo", Map.of(
                    "id", cabinet.getId(),
                    "name", cabinet.getName()
            ));

            log.info("Cabinet analytics generated successfully for cabinet: {}", cabinetId);
            return ResponseEntity.ok(cabinetAnalytics);

        } catch (Exception e) {
            log.error("Error generating cabinet analytics for cabinet {}: {}", cabinetId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/cabinet/period")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Map<String, Object>> getCabinetAnalyticsForPeriod(
            @RequestParam("cabinetId") Long cabinetId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {

        log.info("Cabinet analytics requested for cabinet: {} and period: {} to {} (refresh: {})",
                cabinetId, startDate, endDate, refresh);

        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start date {} is after end date {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Clear cache if refresh is requested
            if (refresh) {
                Cache periodCache = cacheManager.getCache("adminAnalyticsPeriod");
                if (periodCache != null) {
                    periodCache.clear();
                    log.info("Cache cleared due to refresh parameter");
                }
            }

            // Get the cabinet
            Cabinet cabinet = cabinetRepository.findById(cabinetId)
                    .orElseThrow(() -> new IllegalArgumentException("Cabinet not found with ID: " + cabinetId));

            // Generate cabinet-specific analytics for period using the service
            Map<String, Object> cabinetAnalytics = analyticsService.getCabinetAnalyticsForPeriod(cabinetId, startDate, endDate);

            // Add cabinet and period info (if not already included by the service)
            cabinetAnalytics.put("cabinetInfo", Map.of(
                    "id", cabinet.getId(),
                    "name", cabinet.getName()
            ));
            cabinetAnalytics.put("period", Map.of(
                    "startDate", startDate,
                    "endDate", endDate
            ));

            log.info("Cabinet analytics for period generated successfully for cabinet: {}", cabinetId);
            return ResponseEntity.ok(cabinetAnalytics);

        } catch (Exception e) {
            log.error("Error generating cabinet analytics for period for cabinet {}: {}", cabinetId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<AdminAnalyticsDTO> getAdminAnalytics(
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        log.info("Admin analytics requested (refresh: {})", refresh);

        try {
            // Clear cache if refresh is requested
            if (refresh) {
                Cache adminCache = cacheManager.getCache("adminAnalytics");
                if (adminCache != null) {
                    adminCache.clear();
                    log.info("Cache cleared due to refresh parameter");
                }
            }

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
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {

        log.info("Admin analytics requested for period: {} to {} (refresh: {})", startDate, endDate, refresh);

        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start date {} is after end date {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Clear cache if refresh is requested
            if (refresh) {
                Cache periodCache = cacheManager.getCache("adminAnalyticsPeriod");
                if (periodCache != null) {
                    // Clear specific cache entry or entire cache
                    String cacheKey = startDate.toString() + "_" + endDate.toString();
                    periodCache.evict(cacheKey);
                    log.info("Cache cleared for period {} due to refresh parameter", cacheKey);
                }
            }

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

    // Add these endpoints to your AnalyticsController.java
    @GetMapping("/admin/manual-updates")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Object> getManualUpdateStatistics() {
        log.info("Manual update statistics requested");

        try {
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalytics();
            log.info("Analytics retrieved: {}", analytics != null);
            log.info("Manual update stats: {}", analytics.getManualUpdateStats() != null);

            if (analytics.getManualUpdateStats() == null) {
                log.warn("Manual update statistics not available");
                return ResponseEntity.noContent().build();
            }
            Map<String, Object> manualUpdateStats = new HashMap<>();
            manualExtracted(analytics, manualUpdateStats);
            manualUpdateStats.put("manualUpdateTrends", analytics.getManualUpdateStats().getManualUpdateTrends());

            log.info("Manual update statistics generated successfully");
            return ResponseEntity.ok(manualUpdateStats);
        } catch (Exception e) {
            log.error("Error generating manual update statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private void manualExtracted(AdminAnalyticsDTO analytics, Map<String, Object> manualUpdateStats) {
        if (analytics.getManualUpdateStats() == null) {
            log.warn("Manual update statistics are not available");
            manualUpdateStats.put("error", "Manual update statistics not available");
            return;
        }
        manualUpdateStats.put("totalManuallyUpdatedEcritures", analytics.getManualUpdateStats().getTotalManuallyUpdatedEcritures());
        manualUpdateStats.put("totalManuallyUpdatedLines", analytics.getManualUpdateStats().getTotalManuallyUpdatedLines());
        manualUpdateStats.put("manuallyUpdatedEcrituresByCabinet", analytics.getManualUpdateStats().getManuallyUpdatedEcrituresByCabinet());
        manualUpdateStats.put("manuallyUpdatedLinesByCabinet", analytics.getManualUpdateStats().getManuallyUpdatedLinesByCabinet());
        manualUpdateStats.put("manualUpdateCabinetNames", analytics.getManualUpdateStats().getManualUpdateCabinetNames());
        manualUpdateStats.put("manualUpdatePercentage", analytics.getManualUpdateStats().getManualUpdatePercentage());
    }

    @GetMapping("/admin/manual-updates/period")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Object> getManualUpdateStatisticsForPeriod(
            @RequestParam("startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam("endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Manual update statistics requested for period: {} to {}", startDate, endDate);

        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start date {} is after end date {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        try {
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalyticsForPeriod(startDate, endDate);

            Map<String, Object> manualUpdateStats = new HashMap<>();
            manualExtracted(analytics, manualUpdateStats);
            manualUpdateStats.put("period", Map.of("startDate", startDate, "endDate", endDate));

            log.info("Manual update statistics for period generated successfully");
            return ResponseEntity.ok(manualUpdateStats);
        } catch (Exception e) {
            log.error("Error generating manual update statistics for period", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/cabinet/{cabinetId}/manual-updates")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Object> getCabinetManualUpdateStatistics(@PathVariable Long cabinetId) {
        log.info("Manual update statistics requested for cabinet: {}", cabinetId);

        try {
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalytics();

            // Find the specific cabinet
            CabinetAnalytics cabinetAnalytics = analytics.getCabinetAnalytics().stream()
                    .filter(c -> c.getCabinetId().equals(cabinetId))
                    .findFirst()
                    .orElse(null);

            if (cabinetAnalytics == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> cabinetManualUpdateStats = new HashMap<>();
            cabinetManualUpdateStats.put("cabinetId", cabinetId);
            cabinetManualUpdateStats.put("cabinetName", cabinetAnalytics.getCabinetName());
            cabinetManualUpdateStats.put("totalManuallyUpdatedEcritures", cabinetAnalytics.getTotalManuallyUpdatedEcritures());
            cabinetManualUpdateStats.put("totalManuallyUpdatedLines", cabinetAnalytics.getTotalManuallyUpdatedLines());
            cabinetManualUpdateStats.put("manualUpdatePercentage", cabinetAnalytics.getManualUpdatePercentage());
            cabinetManualUpdateStats.put("lastManualUpdateDate", cabinetAnalytics.getLastManualUpdateDate());

            log.info("Cabinet {} manual update statistics generated successfully", cabinetId);
            return ResponseEntity.ok(cabinetManualUpdateStats);
        } catch (Exception e) {
            log.error("Error generating manual update statistics for cabinet {}", cabinetId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/debug/manual-updates-raw")
    public ResponseEntity<Map<String, Object>> debugManualUpdatesRaw() {
        Map<String, Object> debug = new HashMap<>();

        try {
            debug.put("totalEcritures", ecritureRepository.countManuallyUpdatedEcritures());
            debug.put("totalLines", lineRepository.countManuallyUpdatedLines());
            debug.put("ecrituresByCabinet", ecritureRepository.countManuallyUpdatedEcrituresByCabinet());
            debug.put("linesByCabinet", lineRepository.countManuallyUpdatedLinesByCabinet());

            return ResponseEntity.ok(debug);
        } catch (Exception e) {
            debug.put("error", e.getMessage());
            return ResponseEntity.ok(debug);
        }
    }

    @GetMapping("/debug/cabinet-pieces/{cabinetId}")
    public ResponseEntity<Map<String, Object>> debugCabinetPieces(@PathVariable Long cabinetId) {
        Map<String, Object> debug = new HashMap<>();

        try {
            // Check total pieces
            Long totalPieces = pieceRepository.countByDossierCabinetId(cabinetId);
            debug.put("totalPieces", totalPieces);

            // Check pieces by status
            Map<String, Long> piecesByStatus = new HashMap<>();
            for (PieceStatus status : PieceStatus.values()) {
                Long count = pieceRepository.countByDossierCabinetIdAndStatus(cabinetId, status);
                piecesByStatus.put(status.name(), count);
            }
            debug.put("piecesByStatus", piecesByStatus);

            // Check manual updates
            Long manualEcritures = ecritureRepository.countManuallyUpdatedEcrituresByCabinet()
                    .stream()
                    .filter(result -> ((Number) result[0]).longValue() == cabinetId)
                    .map(result -> ((Number) result[1]).longValue())
                    .findFirst()
                    .orElse(0L);
            debug.put("manualEcritures", manualEcritures);

            // Check forced pieces
            Long forcedPieces = pieceRepository.countForcedPiecesByCabinet()
                    .stream()
                    .filter(result -> ((Number) result[0]).longValue() == cabinetId)
                    .map(result -> ((Number) result[1]).longValue())
                    .findFirst()
                    .orElse(0L);
            debug.put("forcedPieces", forcedPieces);

            // Raw piece data
            List<Object[]> rawPieces = pieceRepository.findRawPieceDataByCabinet(cabinetId);
            debug.put("rawPieceCount", rawPieces.size());
            debug.put("rawPieces", rawPieces);

            return ResponseEntity.ok(debug);
        } catch (Exception e) {
            debug.put("error", e.getMessage());
            debug.put("stackTrace", Arrays.toString(e.getStackTrace()));
            return ResponseEntity.ok(debug);
        }
    }

    @GetMapping("/debug/piece-counts")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Map<String, Object>> debugPieceCounts() {
        Map<String, Object> debug = new HashMap<>();

        try {
            // 1. Raw piece count from database
            Long totalPiecesRaw = pieceRepository.count();
            debug.put("totalPiecesRaw", totalPiecesRaw);

            // 2. Count by each status
            Map<String, Long> piecesByStatus = new HashMap<>();
            for (PieceStatus status : PieceStatus.values()) {
                Long count = pieceRepository.countPiecesByStatus(status);
                piecesByStatus.put(status.name(), count != null ? count : 0L);
            }
            debug.put("piecesByStatus", piecesByStatus);

            // 3. Sum of all status counts (should equal total)
            Long statusSum = piecesByStatus.values().stream().mapToLong(Long::longValue).sum();
            debug.put("statusSum", statusSum);

            // 4. Count by cabinet
            List<Object[]> piecesByCabinet = pieceRepository.countPiecesByAllCabinets();
            Map<String, Object> cabinetCounts = new HashMap<>();
            for (Object[] result : piecesByCabinet) {
                Long cabinetId = ((Number) result[0]).longValue();
                Long count = ((Number) result[1]).longValue();
                String cabinetName = (String) result[2]; // Assuming you have cabinet name in the query

                Map<String, Object> cabinetInfo = new HashMap<>();
                cabinetInfo.put("cabinetId", cabinetId);
                cabinetInfo.put("cabinetName", cabinetName);
                cabinetInfo.put("pieceCount", count);

                // Get status breakdown for this cabinet
                Map<String, Long> cabinetStatusCounts = new HashMap<>();
                for (PieceStatus status : PieceStatus.values()) {
                    Long statusCount = pieceRepository.countByDossierCabinetIdAndStatus(cabinetId, status);
                    if (statusCount > 0) {
                        cabinetStatusCounts.put(status.name(), statusCount);
                    }
                }
                cabinetInfo.put("statusBreakdown", cabinetStatusCounts);

                cabinetCounts.put("cabinet_" + cabinetId, cabinetInfo);
            }
            debug.put("cabinetCounts", cabinetCounts);

            // 5. Check for pieces without dossier/cabinet association
            Long piecesWithoutDossier = pieceRepository.countPiecesWithoutDossier();
            debug.put("piecesWithoutDossier", piecesWithoutDossier);

            // 6. Check for soft-deleted or hidden pieces
            Long activePieces = pieceRepository.countActivePieces();
            debug.put("activePieces", activePieces);

            // 7. Analytics service result for comparison
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalytics();
            debug.put("analyticsServiceTotalPieces", analytics.getPieceStats().getTotalPieces());
            debug.put("analyticsServicePiecesByStatus", analytics.getPieceStats().getPiecesByStatus());

            // 8. Check what queries analytics service is using
            debug.put("analyticsQueries", Map.of(
                    "globalPieceCount", "pieceRepository.count()",
                    "piecesByStatusMethod", "pieceRepository.countPiecesByStatus(status)",
                    "cabinetPieceCountMethod", "pieceRepository.countByDossierCabinetId(cabinetId)"
            ));

        } catch (Exception e) {
            debug.put("error", e.getMessage());
            debug.put("stackTrace", Arrays.toString(e.getStackTrace()));
        }

        return ResponseEntity.ok(debug);
    }

    @GetMapping("/debug/specific-cabinet/{cabinetId}")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Map<String, Object>> debugSpecificCabinet(@PathVariable Long cabinetId) {
        Map<String, Object> debug = new HashMap<>();

        try {
            // Raw piece details for this cabinet
            List<Object[]> rawPieces = pieceRepository.findRawPieceDataByCabinet(cabinetId);
            debug.put("rawPieceCount", rawPieces.size());

            List<Map<String, Object>> pieceDetails = new ArrayList<>();
            for (Object[] piece : rawPieces) {
                Map<String, Object> pieceInfo = new HashMap<>();
                pieceInfo.put("id", piece[0]);
                pieceInfo.put("filename", piece[1]);
                pieceInfo.put("status", piece[2]);
                pieceInfo.put("isForced", piece[3]);
                pieceInfo.put("cabinetId", piece[4]);
                pieceInfo.put("cabinetName", piece[5]);
                pieceDetails.add(pieceInfo);
            }
            debug.put("pieceDetails", pieceDetails);

            // Count using different methods
            debug.put("countByDossierCabinetId", pieceRepository.countByDossierCabinetId(cabinetId));

            Map<String, Long> statusCounts = new HashMap<>();
            for (PieceStatus status : PieceStatus.values()) {
                Long count = pieceRepository.countByDossierCabinetIdAndStatus(cabinetId, status);
                if (count > 0) {
                    statusCounts.put(status.name(), count);
                }
            }
            debug.put("statusCounts", statusCounts);
            debug.put("statusSum", statusCounts.values().stream().mapToLong(Long::longValue).sum());

        } catch (Exception e) {
            debug.put("error", e.getMessage());
        }

        return ResponseEntity.ok(debug);
    }

    @GetMapping("/debug/analytics-breakdown")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Map<String, Object>> debugAnalyticsBreakdown() {
        Map<String, Object> debug = new HashMap<>();

        try {
            // Show exactly what the analytics service is calculating
            debug.put("step1_rawCount", pieceRepository.count());

            // Get the full analytics to see what's happening
            AdminAnalyticsDTO analytics = analyticsService.getAdminAnalytics();
            debug.put("step2_analyticsResult", Map.of(
                    "totalPieces", analytics.getPieceStats().getTotalPieces(),
                    "piecesByStatus", analytics.getPieceStats().getPiecesByStatus(),
                    "uploadedPieces", analytics.getPieceStats().getUploadedPieces(),
                    "processedPieces", analytics.getPieceStats().getProcessedPieces(),
                    "rejectedPieces", analytics.getPieceStats().getRejectedPieces(),
                    "processingPieces", analytics.getPieceStats().getProcessingPieces(),
                    "duplicatePieces", analytics.getPieceStats().getDuplicatePieces()
            ));

            // Manual calculation step by step
            Long manualTotal = pieceRepository.count();
            Map<String, Long> manualByStatus = new HashMap<>();
            for (PieceStatus status : PieceStatus.values()) {
                Long count = pieceRepository.countPiecesByStatus(status);
                manualByStatus.put(status.name(), count != null ? count : 0L);
            }

            debug.put("step3_manualCalculation", Map.of(
                    "manualTotal", manualTotal,
                    "manualByStatus", manualByStatus,
                    "manualStatusSum", manualByStatus.values().stream().mapToLong(Long::longValue).sum()
            ));

            // Check each cabinet individually
            List<Cabinet> cabinets = cabinetRepository.findAll();
            Map<String, Object> cabinetBreakdown = new HashMap<>();

            for (Cabinet cabinet : cabinets) {
                Long count = pieceRepository.countByDossierCabinetId(cabinet.getId());
                cabinetBreakdown.put("cabinet_" + cabinet.getId() + "_" + cabinet.getName(), count);
            }
            debug.put("step4_cabinetBreakdown", cabinetBreakdown);

            // Check for potential issues
            debug.put("step5_potentialIssues", Map.of(
                    "piecesWithoutDossier", pieceRepository.countPiecesWithoutDossier(),
                    "duplicatePieces", pieceRepository.countByIsDuplicateTrue(),
                    "forcedPieces", pieceRepository.countByIsForcedTrue()
            ));

        } catch (Exception e) {
            debug.put("error", e.getMessage());
            debug.put("stackTrace", Arrays.toString(e.getStackTrace()));
        }

        return ResponseEntity.ok(debug);
    }

    @PostMapping("/admin/clear-cache")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Map<String, String>> clearAnalyticsCache() {
        try {
            // Clear both caches
            Cache adminCache = cacheManager.getCache("adminAnalytics");
            Cache periodCache = cacheManager.getCache("adminAnalyticsPeriod");

            if (adminCache != null) {
                adminCache.clear();
                log.info("Cleared adminAnalytics cache");
            }

            if (periodCache != null) {
                periodCache.clear();
                log.info("Cleared adminAnalyticsPeriod cache");
            }

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Analytics cache cleared successfully");
            response.put("timestamp", new Date().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error clearing analytics cache: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to clear cache: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/admin/cache-status")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Map<String, Object>> getCacheStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            Cache adminCache = cacheManager.getCache("adminAnalytics");
            Cache periodCache = cacheManager.getCache("adminAnalyticsPeriod");

            if (adminCache != null) {
                status.put("adminAnalyticsCache", "exists");
                // Try to get cache stats
                try {
                    Object nativeCache = adminCache.getNativeCache();
                    status.put("adminCacheType", nativeCache.getClass().getSimpleName());

                    // Generic way to get cache info without depending on Caffeine
                    if (nativeCache.toString().contains("size")) {
                        status.put("adminCacheInfo", nativeCache.toString());
                    }
                } catch (Exception e) {
                    status.put("adminCacheDetails", "Unable to get cache details: " + e.getMessage());
                }
            } else {
                status.put("adminAnalyticsCache", "not found");
            }

            if (periodCache != null) {
                status.put("periodAnalyticsCache", "exists");
                try {
                    Object nativeCache = periodCache.getNativeCache();
                    status.put("periodCacheType", nativeCache.getClass().getSimpleName());

                    // Generic way to get cache info without depending on Caffeine
                    if (nativeCache.toString().contains("size")) {
                        status.put("periodCacheInfo", nativeCache.toString());
                    }
                } catch (Exception e) {
                    status.put("periodCacheDetails", "Unable to get cache details: " + e.getMessage());
                }
            } else {
                status.put("periodAnalyticsCache", "not found");
            }

            status.put("timestamp", new Date().toString());

        } catch (Exception e) {
            status.put("error", e.getMessage());
        }

        return ResponseEntity.ok(status);
    }

    @GetMapping("/debug/cabinet-check/{cabinetId}")
    @PreAuthorize("hasAuthority('PACIOLI')")
    public ResponseEntity<Map<String, Object>> debugCabinetCheck(@PathVariable Long cabinetId) {
        Map<String, Object> debug = new HashMap<>();

        try {
            // Check if cabinet exists
            Cabinet cabinet = cabinetRepository.findById(cabinetId).orElse(null);
            debug.put("cabinetExists", cabinet != null);
            if (cabinet != null) {
                debug.put("cabinetName", cabinet.getName());
            }

            // Check counts
            debug.put("totalDossiers", dossierRepository.countByCabinetId(cabinetId));
            debug.put("totalPieces", pieceRepository.countByDossierCabinetId(cabinetId));
            debug.put("totalUsers", userRepository.countByCabinetId(cabinetId));

            // Check if analytics service works
            Map<String, Object> analytics = analyticsService.getCabinetAnalytics(cabinetId);
            debug.put("analyticsKeys", analytics.keySet());
            debug.put("analyticsEmpty", analytics.isEmpty());

            return ResponseEntity.ok(debug);
        } catch (Exception e) {
            debug.put("error", e.getMessage());
            return ResponseEntity.ok(debug);
        }
    }
}