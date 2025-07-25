package com.pacioli.core.services.serviceImp;

import com.pacioli.core.DTO.analytics.AdminAnalyticsDTO;
import com.pacioli.core.DTO.analytics.CabinetAnalytics;
import com.pacioli.core.DTO.analytics.CabinetCreationTrend;
import com.pacioli.core.DTO.analytics.CabinetStatistics;
import com.pacioli.core.DTO.analytics.DossierCreationTrend;
import com.pacioli.core.DTO.analytics.DossierStatistics;
import com.pacioli.core.DTO.analytics.HistoriqueStatistics;
import com.pacioli.core.DTO.analytics.HistoriqueUploadTrend;
import com.pacioli.core.DTO.analytics.PieceStatistics;
import com.pacioli.core.DTO.analytics.PieceUploadTrend;
import com.pacioli.core.Exceptions.AnalyticsException;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.repositories.*;
import com.pacioli.core.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final CabinetRepository cabinetRepository;
    private final PieceRepository pieceRepository;
    private final DossierRepository dossierRepository;
    private final UserRepository userRepository;

    @Override
    @Cacheable(value = "adminAnalytics", unless = "#result == null")
    public AdminAnalyticsDTO getAdminAnalytics() {
        log.info("Generating comprehensive admin analytics");

        try {
            // Use async processing for better performance
            CompletableFuture<CabinetStatistics> cabinetStatsFuture = generateCabinetStatisticsAsync();
            CompletableFuture<PieceStatistics> pieceStatsFuture = generatePieceStatisticsAsync();
            CompletableFuture<DossierStatistics> dossierStatsFuture = generateDossierStatisticsAsync();
            CompletableFuture<HistoriqueStatistics> historiqueStatsFuture = generateHistoriqueStatisticsAsync();
            CompletableFuture<List<CabinetAnalytics>> cabinetAnalyticsFuture = generateCabinetAnalyticsAsync();

            // Wait for all async operations to complete
            CompletableFuture.allOf(
                    cabinetStatsFuture,
                    pieceStatsFuture,
                    dossierStatsFuture,
                    historiqueStatsFuture,
                    cabinetAnalyticsFuture
            ).join();

            return AdminAnalyticsDTO.builder()
                    .cabinetStats(cabinetStatsFuture.get())
                    .pieceStats(pieceStatsFuture.get())
                    .dossierStats(dossierStatsFuture.get())
                    .historiqueStats(historiqueStatsFuture.get())
                    .cabinetAnalytics(cabinetAnalyticsFuture.get())
                    .build();

        } catch (Exception e) {
            log.error("Error generating admin analytics", e);
            throw new AnalyticsException("Failed to generate admin analytics", e);
        }
    }

    @Override
    @Cacheable(value = "adminAnalyticsPeriod", key = "#startDate.toString() + '_' + #endDate.toString()", unless = "#result == null")
    public AdminAnalyticsDTO getAdminAnalyticsForPeriod(LocalDate startDate, LocalDate endDate) {
        log.info("Generating admin analytics for period: {} to {}", startDate, endDate);

        if (startDate.isAfter(endDate)) {
            throw new AnalyticsException("Start date cannot be after end date");
        }

        try {
            return AdminAnalyticsDTO.builder()
                    .cabinetStats(generateCabinetStatisticsForPeriod(startDate, endDate))
                    .pieceStats(generatePieceStatisticsForPeriod(startDate, endDate))
                    .dossierStats(generateDossierStatisticsForPeriod(startDate, endDate))
                    .historiqueStats(generateHistoriqueStatisticsForPeriod(startDate, endDate))
                    .cabinetAnalytics(generateCabinetAnalyticsForPeriod(startDate, endDate))
                    .build();
        } catch (Exception e) {
            log.error("Error generating admin analytics for period", e);
            throw new AnalyticsException("Failed to generate admin analytics for period", e);
        }
    }

    @Async("analyticsTaskExecutor")
    public CompletableFuture<CabinetStatistics> generateCabinetStatisticsAsync() {
        try {
            Long totalCabinets = cabinetRepository.count();
            Long activeCabinets = totalCabinets; // All cabinets are considered active
            Long inactiveCabinets = 0L;

            List<CabinetCreationTrend> trends = generateCabinetCreationTrends();

            CabinetStatistics stats = CabinetStatistics.builder()
                    .totalCabinets(totalCabinets)
                    .activeCabinets(activeCabinets)
                    .inactiveCabinets(inactiveCabinets)
                    .creationTrends(trends)
                    .build();

            return CompletableFuture.completedFuture(stats);
        } catch (Exception e) {
            log.error("Error generating cabinet statistics", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async("analyticsTaskExecutor")
    public CompletableFuture<DossierStatistics> generateDossierStatisticsAsync() {
        try {
            Long totalDossiers = dossierRepository.count();
            Long activeDossiers = totalDossiers; // All dossiers are considered active

            Map<Long, Long> dossiersByCabinet = dossierRepository.findAll()
                    .stream()
                    .collect(Collectors.groupingBy(
                            dossier -> dossier.getCabinet().getId(),
                            Collectors.counting()
                    ));

            List<DossierCreationTrend> creationTrends = generateDossierCreationTrends();

            DossierStatistics stats = DossierStatistics.builder()
                    .totalDossiers(totalDossiers)
                    .activeDossiers(activeDossiers)
                    .dossiersByCabinet(dossiersByCabinet)
                    .creationTrends(creationTrends)
                    .build();

            return CompletableFuture.completedFuture(stats);
        } catch (Exception e) {
            log.error("Error generating dossier statistics", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async("analyticsTaskExecutor")
    public CompletableFuture<HistoriqueStatistics> generateHistoriqueStatisticsAsync() {
        try {
            Long totalHistoriqueFiles = pieceRepository.countByType("HISTORIQUE");
            if (totalHistoriqueFiles == null) totalHistoriqueFiles = 0L;

            Map<Long, Long> historiqueFilesByCabinet = pieceRepository.findByType("HISTORIQUE")
                    .stream()
                    .filter(piece -> piece.getDossier() != null && piece.getDossier().getCabinet() != null)
                    .collect(Collectors.groupingBy(
                            piece -> piece.getDossier().getCabinet().getId(),
                            Collectors.counting()
                    ));

            List<HistoriqueUploadTrend> uploadTrends = generateHistoriqueUploadTrends();

            HistoriqueStatistics stats = HistoriqueStatistics.builder()
                    .totalHistoriqueFiles(totalHistoriqueFiles)
                    .historiqueFilesByCabinet(historiqueFilesByCabinet)
                    .uploadTrends(uploadTrends)
                    .build();

            return CompletableFuture.completedFuture(stats);
        } catch (Exception e) {
            log.error("Error generating historique statistics", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async("analyticsTaskExecutor")
    public CompletableFuture<List<CabinetAnalytics>> generateCabinetAnalyticsAsync() {
        try {
            List<CabinetAnalytics> analytics = cabinetRepository.findAll().stream()
                    .map(cabinet -> {
                        try {
                            Long totalDossiers = dossierRepository.countByCabinetId(cabinet.getId());
                            Long totalPieces = pieceRepository.countByDossierCabinetId(cabinet.getId());
                            Long totalUsers = userRepository.countByCabinetId(cabinet.getId());

                            Map<String, Long> piecesByStatus = new HashMap<>();
                            for (PieceStatus status : PieceStatus.values()) {
                                Long count = pieceRepository.countByDossierCabinetIdAndStatus(cabinet.getId(), status);
                                piecesByStatus.put(status.name(), count != null ? count : 0L);
                            }

                            Long totalHistoriqueFiles = pieceRepository.countByDossierCabinetIdAndType(cabinet.getId(), "HISTORIQUE");

                            return CabinetAnalytics.builder()
                                    .cabinetId(cabinet.getId())
                                    .cabinetName(cabinet.getName())
                                    .totalDossiers(totalDossiers != null ? totalDossiers : 0L)
                                    .totalPieces(totalPieces != null ? totalPieces : 0L)
                                    .totalUsers(totalUsers != null ? totalUsers : 0L)
                                    .piecesByStatus(piecesByStatus)
                                    .totalHistoriqueFiles(totalHistoriqueFiles != null ? totalHistoriqueFiles : 0L)
                                    .build();
                        } catch (Exception e) {
                            log.warn("Error generating analytics for cabinet {}: {}", cabinet.getId(), e.getMessage());
                            return CabinetAnalytics.builder()
                                    .cabinetId(cabinet.getId())
                                    .cabinetName(cabinet.getName())
                                    .totalDossiers(0L)
                                    .totalPieces(0L)
                                    .totalUsers(0L)
                                    .piecesByStatus(new HashMap<>())
                                    .totalHistoriqueFiles(0L)
                                    .build();
                        }
                    })
                    .collect(Collectors.toList());

            return CompletableFuture.completedFuture(analytics);
        } catch (Exception e) {
            log.error("Error generating cabinet analytics", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // Private helper methods remain the same as in the previous implementation
    private CabinetStatistics generateCabinetStatisticsForPeriod(LocalDate startDate, LocalDate endDate) {
        // Implementation remains the same
        return generateCabinetStatistics();
    }

    private PieceStatistics generatePieceStatisticsForPeriod(LocalDate startDate, LocalDate endDate) {
        Date start = java.sql.Date.valueOf(startDate);
        Date end = java.sql.Date.valueOf(endDate.plusDays(1));

        Long totalPieces = pieceRepository.countPiecesByUploadDateBetween(start, end);

        Map<String, Long> piecesByStatus = new HashMap<>();
        for (PieceStatus status : PieceStatus.values()) {
            Long count = pieceRepository.countPiecesByStatusAndUploadDateBetween(status, start, end);
            piecesByStatus.put(status.name(), count != null ? count : 0L);
        }

        Long uploadedPieces = piecesByStatus.getOrDefault(PieceStatus.UPLOADED.name(), 0L);
        Long processingPieces = piecesByStatus.getOrDefault(PieceStatus.PROCESSING.name(), 0L);
        Long processedPieces = piecesByStatus.getOrDefault(PieceStatus.PROCESSED.name(), 0L);
        Long rejectedPieces = piecesByStatus.getOrDefault(PieceStatus.REJECTED.name(), 0L);
        Long duplicatePieces = piecesByStatus.getOrDefault(PieceStatus.DUPLICATE.name(), 0L);

        // NEW: Add forced pieces for period
        Long forcedPieces = pieceRepository.countByIsForcedTrueAndUploadDateBetween(start, end);
        Map<Long, Long> forcedPiecesByCabinet = generateForcedPiecesByCabinetMapForPeriod(startDate, endDate);
        Map<Long, Long> forcedPiecesByDossier = generateForcedPiecesByDossierMapForPeriod(startDate, endDate);

        return PieceStatistics.builder()
                .totalPieces(totalPieces != null ? totalPieces : 0L)
                .uploadedPieces(uploadedPieces)
                .processingPieces(processingPieces)
                .processedPieces(processedPieces)
                .rejectedPieces(rejectedPieces)
                .duplicatePieces(duplicatePieces)
                .forcedPieces(forcedPieces != null ? forcedPieces : 0L)  // NEW
                .piecesByStatus(piecesByStatus)
                .uploadTrends(generatePieceUploadTrendsForPeriod(startDate, endDate))
                .forcedPiecesByCabinet(forcedPiecesByCabinet)  // NEW
                .forcedPiecesByDossier(forcedPiecesByDossier)  // NEW
                .build();
    }

    private DossierStatistics generateDossierStatisticsForPeriod(LocalDate startDate, LocalDate endDate) {
        return generateDossierStatistics();
    }

    private HistoriqueStatistics generateHistoriqueStatisticsForPeriod(LocalDate startDate, LocalDate endDate) {
        Date start = java.sql.Date.valueOf(startDate);
        Date end = java.sql.Date.valueOf(endDate.plusDays(1));

        Long totalHistoriqueFiles = pieceRepository.countByTypeAndUploadDateBetween("HISTORIQUE", start, end);

        Map<Long, Long> historiqueFilesByCabinet = pieceRepository.findByTypeAndUploadDateBetween("HISTORIQUE", start, end)
                .stream()
                .filter(piece -> piece.getDossier() != null && piece.getDossier().getCabinet() != null)
                .collect(Collectors.groupingBy(
                        piece -> piece.getDossier().getCabinet().getId(),
                        Collectors.counting()
                ));

        return HistoriqueStatistics.builder()
                .totalHistoriqueFiles(totalHistoriqueFiles != null ? totalHistoriqueFiles : 0L)
                .historiqueFilesByCabinet(historiqueFilesByCabinet)
                .uploadTrends(Collections.emptyList())
                .build();
    }

    private List<CabinetAnalytics> generateCabinetAnalyticsForPeriod(LocalDate startDate, LocalDate endDate) {
        Date start = java.sql.Date.valueOf(startDate);
        Date end = java.sql.Date.valueOf(endDate.plusDays(1));

        return cabinetRepository.findAll().stream()
                .map(cabinet -> {
                    try {
                        Long totalDossiers = dossierRepository.countByCabinetId(cabinet.getId());
                        Long totalPieces = pieceRepository.countByDossierCabinetIdAndUploadDateBetween(cabinet.getId(), start, end);
                        Long totalUsers = userRepository.countByCabinetId(cabinet.getId());

                        Map<String, Long> piecesByStatus = new HashMap<>();
                        for (PieceStatus status : PieceStatus.values()) {
                            Long count = pieceRepository.countByDossierCabinetIdAndStatusAndUploadDateBetween(cabinet.getId(), status, start, end);
                            piecesByStatus.put(status.name(), count != null ? count : 0L);
                        }

                        Long totalHistoriqueFiles = pieceRepository.countByDossierCabinetIdAndTypeAndUploadDateBetween(cabinet.getId(), "HISTORIQUE", start, end);

                        return CabinetAnalytics.builder()
                                .cabinetId(cabinet.getId())
                                .cabinetName(cabinet.getName())
                                .totalDossiers(totalDossiers != null ? totalDossiers : 0L)
                                .totalPieces(totalPieces != null ? totalPieces : 0L)
                                .totalUsers(totalUsers != null ? totalUsers : 0L)
                                .piecesByStatus(piecesByStatus)
                                .totalHistoriqueFiles(totalHistoriqueFiles != null ? totalHistoriqueFiles : 0L)
                                .build();
                    } catch (Exception e) {
                        log.warn("Error generating period analytics for cabinet {}: {}", cabinet.getId(), e.getMessage());
                        return CabinetAnalytics.builder()
                                .cabinetId(cabinet.getId())
                                .cabinetName(cabinet.getName())
                                .totalDossiers(0L)
                                .totalPieces(0L)
                                .totalUsers(0L)
                                .piecesByStatus(new HashMap<>())
                                .totalHistoriqueFiles(0L)
                                .build();
                    }
                })
                .collect(Collectors.toList());
    }

    // Trend generation methods - implement based on your needs
    private List<CabinetCreationTrend> generateCabinetCreationTrends() {
        return Collections.emptyList(); // Implement when Cabinet has creation date
    }

    private List<PieceUploadTrend> generatePieceUploadTrends() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        try {
            return pieceRepository.findAll().stream()
                    .filter(piece -> piece.getUploadDate() != null)
                    .collect(Collectors.groupingBy(
                            piece -> piece.getUploadDate().toInstant()
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                                    .format(formatter),
                            Collectors.groupingBy(
                                    piece -> piece.getStatus() != null ? piece.getStatus().name() : "UNKNOWN",
                                    Collectors.counting()
                            )
                    ))
                    .entrySet().stream()
                    .map(entry -> PieceUploadTrend.builder()
                            .period(entry.getKey())
                            .count(entry.getValue().values().stream().mapToLong(Long::longValue).sum())
                            .statusBreakdown(entry.getValue())
                            .build())
                    .sorted(Comparator.comparing(PieceUploadTrend::getPeriod))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Error generating piece upload trends: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<PieceUploadTrend> generatePieceUploadTrendsForPeriod(LocalDate startDate, LocalDate endDate) {
        Date start = java.sql.Date.valueOf(startDate);
        Date end = java.sql.Date.valueOf(endDate.plusDays(1));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        try {
            return pieceRepository.findByUploadDateBetween(start, end).stream()
                    .collect(Collectors.groupingBy(
                            piece -> piece.getUploadDate().toInstant()
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                                    .format(formatter),
                            Collectors.groupingBy(
                                    piece -> piece.getStatus() != null ? piece.getStatus().name() : "UNKNOWN",
                                    Collectors.counting()
                            )
                    ))
                    .entrySet().stream()
                    .map(entry -> PieceUploadTrend.builder()
                            .period(entry.getKey())
                            .count(entry.getValue().values().stream().mapToLong(Long::longValue).sum())
                            .statusBreakdown(entry.getValue())
                            .build())
                    .sorted(Comparator.comparing(PieceUploadTrend::getPeriod))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Error generating piece upload trends for period: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<DossierCreationTrend> generateDossierCreationTrends() {
        return Collections.emptyList(); // Implement when Dossier has creation date
    }

    private List<HistoriqueUploadTrend> generateHistoriqueUploadTrends() {
        return Collections.emptyList(); // Implement if needed
    }

    // Synchronous fallback methods
    private CabinetStatistics generateCabinetStatistics() {
        try {
            return generateCabinetStatisticsAsync().get();
        } catch (Exception e) {
            log.error("Error in synchronous cabinet statistics generation", e);
            return CabinetStatistics.builder()
                    .totalCabinets(0L)
                    .activeCabinets(0L)
                    .inactiveCabinets(0L)
                    .creationTrends(Collections.emptyList())
                    .build();
        }
    }

    private PieceStatistics generatePieceStatistics() {
        Long totalPieces = pieceRepository.count();

        Map<String, Long> piecesByStatus = new HashMap<>();
        for (PieceStatus status : PieceStatus.values()) {
            Long count = pieceRepository.countPiecesByStatus(status);
            piecesByStatus.put(status.name(), count != null ? count : 0L);
        }

        Long uploadedPieces = piecesByStatus.getOrDefault(PieceStatus.UPLOADED.name(), 0L);
        Long processingPieces = piecesByStatus.getOrDefault(PieceStatus.PROCESSING.name(), 0L);
        Long processedPieces = piecesByStatus.getOrDefault(PieceStatus.PROCESSED.name(), 0L);
        Long rejectedPieces = piecesByStatus.getOrDefault(PieceStatus.REJECTED.name(), 0L);
        Long duplicatePieces = piecesByStatus.getOrDefault(PieceStatus.DUPLICATE.name(), 0L);

        // NEW: Add forced pieces statistics
        Long forcedPieces = pieceRepository.countByIsForcedTrue();
        Map<Long, Long> forcedPiecesByCabinet = generateForcedPiecesByCabinetMap();
        Map<Long, Long> forcedPiecesByDossier = generateForcedPiecesByDossierMap();

        List<PieceUploadTrend> uploadTrends = generatePieceUploadTrends();

        return PieceStatistics.builder()
                .totalPieces(totalPieces)
                .uploadedPieces(uploadedPieces)
                .processingPieces(processingPieces)
                .processedPieces(processedPieces)
                .rejectedPieces(rejectedPieces)
                .duplicatePieces(duplicatePieces)
                .forcedPieces(forcedPieces != null ? forcedPieces : 0L)  // NEW
                .piecesByStatus(piecesByStatus)
                .uploadTrends(uploadTrends)
                .forcedPiecesByCabinet(forcedPiecesByCabinet)  // NEW
                .forcedPiecesByDossier(forcedPiecesByDossier)  // NEW
                .build();
    }

    private DossierStatistics generateDossierStatistics() {
        try {
            return generateDossierStatisticsAsync().get();
        } catch (Exception e) {
            log.error("Error in synchronous dossier statistics generation", e);
            return DossierStatistics.builder()
                    .totalDossiers(0L)
                    .activeDossiers(0L)
                    .dossiersByCabinet(new HashMap<>())
                    .creationTrends(Collections.emptyList())
                    .build();
        }
    }

    private HistoriqueStatistics generateHistoriqueStatistics() {
        try {
            return generateHistoriqueStatisticsAsync().get();
        } catch (Exception e) {
            log.error("Error in synchronous historique statistics generation", e);
            return HistoriqueStatistics.builder()
                    .totalHistoriqueFiles(0L)
                    .historiqueFilesByCabinet(new HashMap<>())
                    .uploadTrends(Collections.emptyList())
                    .build();
        }
    }

    private List<CabinetAnalytics> generateCabinetAnalytics() {
        return cabinetRepository.findAll().stream()
                .map(cabinet -> {
                    try {
                        Long totalDossiers = dossierRepository.countByCabinetId(cabinet.getId());
                        Long totalPieces = pieceRepository.countByDossierCabinetId(cabinet.getId());
                        Long totalUsers = userRepository.countByCabinetId(cabinet.getId());

                        Map<String, Long> piecesByStatus = new HashMap<>();
                        for (PieceStatus status : PieceStatus.values()) {
                            Long count = pieceRepository.countByDossierCabinetIdAndStatus(cabinet.getId(), status);
                            piecesByStatus.put(status.name(), count != null ? count : 0L);
                        }

                        Long totalHistoriqueFiles = pieceRepository.countByDossierCabinetIdAndType(cabinet.getId(), "HISTORIQUE");

                        // NEW: Add forced pieces statistics for this cabinet
                        Long totalForcedPieces = pieceRepository.countByDossierCabinetIdAndIsForcedTrue(cabinet.getId());
                        Map<Long, Long> forcedPiecesByDossier = generateForcedPiecesByDossierMapForCabinet(cabinet.getId());

                        return CabinetAnalytics.builder()
                                .cabinetId(cabinet.getId())
                                .cabinetName(cabinet.getName())
                                .totalDossiers(totalDossiers != null ? totalDossiers : 0L)
                                .totalPieces(totalPieces != null ? totalPieces : 0L)
                                .totalUsers(totalUsers != null ? totalUsers : 0L)
                                .piecesByStatus(piecesByStatus)
                                .totalHistoriqueFiles(totalHistoriqueFiles != null ? totalHistoriqueFiles : 0L)
                                .totalForcedPieces(totalForcedPieces != null ? totalForcedPieces : 0L)  // NEW
                                .forcedPiecesByDossier(forcedPiecesByDossier)  // NEW
                                .build();
                    } catch (Exception e) {
                        log.warn("Error generating analytics for cabinet {}: {}", cabinet.getId(), e.getMessage());
                        return CabinetAnalytics.builder()
                                .cabinetId(cabinet.getId())
                                .cabinetName(cabinet.getName())
                                .totalDossiers(0L)
                                .totalPieces(0L)
                                .totalUsers(0L)
                                .piecesByStatus(new HashMap<>())
                                .totalHistoriqueFiles(0L)
                                .totalForcedPieces(0L)  // NEW
                                .forcedPiecesByDossier(new HashMap<>())  // NEW
                                .build();
                    }
                })
                .collect(Collectors.toList());
    }

    private Map<Long, Long> generateForcedPiecesByCabinetMap() {
        try {
            return pieceRepository.countForcedPiecesByCabinet().stream()
                    .collect(Collectors.toMap(
                            result -> ((Number) result[0]).longValue(),  // cabinet_id
                            result -> ((Number) result[1]).longValue()   // count
                    ));
        } catch (Exception e) {
            log.warn("Error generating forced pieces by cabinet map: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private Map<Long, Long> generateForcedPiecesByCabinetMapForPeriod(LocalDate startDate, LocalDate endDate) {
        try {
            Date start = java.sql.Date.valueOf(startDate);
            Date end = java.sql.Date.valueOf(endDate.plusDays(1));

            return pieceRepository.countForcedPiecesByCabinetAndPeriod(start, end).stream()
                    .collect(Collectors.toMap(
                            result -> ((Number) result[0]).longValue(),  // cabinet_id
                            result -> ((Number) result[1]).longValue()   // count
                    ));
        } catch (Exception e) {
            log.warn("Error generating forced pieces by cabinet map for period: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private Map<Long, Long> generateForcedPiecesByDossierMap() {
        try {
            return pieceRepository.countForcedPiecesByDossier().stream()
                    .collect(Collectors.toMap(
                            result -> ((Number) result[0]).longValue(),  // dossier_id
                            result -> ((Number) result[1]).longValue()   // count
                    ));
        } catch (Exception e) {
            log.warn("Error generating forced pieces by dossier map: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private Map<Long, Long> generateForcedPiecesByDossierMapForPeriod(LocalDate startDate, LocalDate endDate) {
        try {
            Date start = java.sql.Date.valueOf(startDate);
            Date end = java.sql.Date.valueOf(endDate.plusDays(1));

            return pieceRepository.countForcedPiecesByDossierAndPeriod(start, end).stream()
                    .collect(Collectors.toMap(
                            result -> ((Number) result[0]).longValue(),  // dossier_id
                            result -> ((Number) result[1]).longValue()   // count
                    ));
        } catch (Exception e) {
            log.warn("Error generating forced pieces by dossier map for period: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private Map<Long, Long> generateForcedPiecesByDossierMapForCabinet(Long cabinetId) {
        try {
            return pieceRepository.countForcedPiecesByDossierInCabinet(cabinetId).stream()
                    .collect(Collectors.toMap(
                            result -> ((Number) result[0]).longValue(),  // dossier_id
                            result -> ((Number) result[1]).longValue()   // count
                    ));
        } catch (Exception e) {
            log.warn("Error generating forced pieces by dossier map for cabinet {}: {}", cabinetId, e.getMessage());
            return new HashMap<>();
        }
    }

// 4. Update the async methods as well

    @Async("analyticsTaskExecutor")
    public CompletableFuture<PieceStatistics> generatePieceStatisticsAsync() {
        try {
            Long totalPieces = pieceRepository.count();

            Map<String, Long> piecesByStatus = new HashMap<>();
            for (PieceStatus status : PieceStatus.values()) {
                Long count = pieceRepository.countPiecesByStatus(status);
                piecesByStatus.put(status.name(), count != null ? count : 0L);
            }

            Long uploadedPieces = piecesByStatus.getOrDefault(PieceStatus.UPLOADED.name(), 0L);
            Long processingPieces = piecesByStatus.getOrDefault(PieceStatus.PROCESSING.name(), 0L);
            Long processedPieces = piecesByStatus.getOrDefault(PieceStatus.PROCESSED.name(), 0L);
            Long rejectedPieces = piecesByStatus.getOrDefault(PieceStatus.REJECTED.name(), 0L);
            Long duplicatePieces = piecesByStatus.getOrDefault(PieceStatus.DUPLICATE.name(), 0L);

            // NEW: Add forced pieces
            Long forcedPieces = pieceRepository.countByIsForcedTrue();
            Map<Long, Long> forcedPiecesByCabinet = generateForcedPiecesByCabinetMap();
            Map<Long, Long> forcedPiecesByDossier = generateForcedPiecesByDossierMap();

            List<PieceUploadTrend> uploadTrends = generatePieceUploadTrends();

            PieceStatistics stats = PieceStatistics.builder()
                    .totalPieces(totalPieces)
                    .uploadedPieces(uploadedPieces)
                    .processingPieces(processingPieces)
                    .processedPieces(processedPieces)
                    .rejectedPieces(rejectedPieces)
                    .duplicatePieces(duplicatePieces != null ? duplicatePieces : 0L)
                    .forcedPieces(forcedPieces != null ? forcedPieces : 0L)  // NEW
                    .piecesByStatus(piecesByStatus)
                    .uploadTrends(uploadTrends)
                    .forcedPiecesByCabinet(forcedPiecesByCabinet)  // NEW
                    .forcedPiecesByDossier(forcedPiecesByDossier)  // NEW
                    .build();

            return CompletableFuture.completedFuture(stats);
        } catch (Exception e) {
            log.error("Error generating piece statistics", e);
            return CompletableFuture.failedFuture(e);
        }
        }
}