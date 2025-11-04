package com.pacioli.core.repositories;

import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciceRepository extends JpaRepository<Exercise, Long> {
    boolean existsByDossierAndStartDateAndEndDate(Dossier dossier, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COUNT(e) > 0 FROM Exercise e WHERE e.dossier = :dossier " +
            "AND e.id <> :exerciseId " +
            "AND ((:startDate BETWEEN e.startDate AND e.endDate) " +
            "OR (:endDate BETWEEN e.startDate AND e.endDate) " +
            "OR (e.startDate BETWEEN :startDate AND :endDate))")
    boolean existsByDossierAndStartDateAndEndDateOverlap(
            @Param("dossier") Dossier dossier,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("exerciseId") Long exerciseId);

    List<Exercise> findAllById(Iterable<Long> ids);

    @Query("SELECT ex FROM Exercise ex " +
            "JOIN ex.dossier d " +
            "WHERE d.cabinet.id = :cabinetId")
    List<Exercise> findExercisesByCabinetId(@Param("cabinetId") Long cabinetId);
    @Query("SELECT ex FROM Exercise ex " +
            "JOIN ex.dossier d " +
            "WHERE d.id = :dossierId")
    List<Exercise> findExercisesByDossierID(@Param("dossierId") Long dossierId);

    @Query("SELECT ex FROM Exercise ex WHERE ex.id = :exerciseId AND ex.dossier.cabinet.id = :cabinetId")
    Optional<Exercise> validateExerciseAndCabinet(@Param("exerciseId") Long exerciseId,
                                                  @Param("cabinetId") Long cabinetId);

    // ✅ COMPREHENSIVE OVERLAP DETECTION: Covers all overlap scenarios
    @Query("SELECT COUNT(e) > 0 FROM Exercise e WHERE e.dossier = :dossier AND " +
            "(" +
            // Scenario 1: New exercise starts during existing exercise
            "(e.startDate <= :startDate AND e.endDate >= :startDate) OR " +
            // Scenario 2: New exercise ends during existing exercise
            "(e.startDate <= :endDate AND e.endDate >= :endDate) OR " +
            // Scenario 3: New exercise completely contains existing exercise
            "(e.startDate >= :startDate AND e.endDate <= :endDate) OR " +
            // Scenario 4: Existing exercise completely contains new exercise
            "(e.startDate <= :startDate AND e.endDate >= :endDate)" +
            ")")
    boolean existsOverlappingExercise(@Param("dossier") Dossier dossier,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);
}