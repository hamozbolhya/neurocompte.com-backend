package com.pacioli.core.repositories;

import com.pacioli.core.DTO.EcritureExportDTO;
import com.pacioli.core.models.Ecriture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EcritureRepository extends JpaRepository<Ecriture, Long> {
    // Fetch all ecritures by Piece ID
    List<Ecriture> findByPieceId(Long pieceId);

    // Fetch all ecritures
    List<Ecriture> findAll();

    // Find Ecritures by Exercise ID
    @Query("SELECT e FROM Ecriture e WHERE e.piece.dossier.id IN " +
            "(SELECT d.id FROM Dossier d JOIN d.exercises ex WHERE ex.id = :exerciseId)")
    List<Ecriture> findByPiece_Dossier_Exercises_Id(@Param("exerciseId") Long exerciseId);

    @Query("""
                SELECT e 
                FROM Ecriture e
                JOIN e.piece p
                JOIN p.dossier d
                JOIN d.exercises ex
                WHERE ex.id = :exerciseId
                  AND d.cabinet.id = :cabinetId
                  AND ex.startDate <= e.entryDate
                  AND ex.endDate >= e.entryDate
            """)
    List<Ecriture> findEcrituresByExerciseAndCabinet(@Param("exerciseId") Long exerciseId,
                                                     @Param("cabinetId") Long cabinetId);


    void deleteAllById(Iterable<? extends Long> ids);


    @Query("SELECT e FROM Ecriture e " +
            "JOIN e.piece p " +
            "JOIN p.dossier d " +
            "JOIN d.exercises ex " +
            "WHERE d.id = :dossierId " +
            "AND ex.id = :exerciseId " +
            "AND e.entryDate BETWEEN ex.startDate AND ex.endDate")
    List<Ecriture> findByDossierAndExerciseId(@Param("dossierId") Long dossierId, @Param("exerciseId") Long exerciseId);


    @Query("""
                SELECT e FROM Ecriture e
                JOIN FETCH e.piece p
                LEFT JOIN FETCH e.journal j
                JOIN FETCH p.dossier d
                LEFT JOIN FETCH e.lines l
                LEFT JOIN FETCH l.account a
                WHERE e.id = :ecritureId
            """)
    Optional<Ecriture> findEcritureByIdWithDetails(@Param("ecritureId") Long ecritureId);

    @Query("""
                SELECT e FROM Ecriture e
                LEFT JOIN FETCH e.piece p
                LEFT JOIN FETCH e.journal j
                LEFT JOIN FETCH p.dossier d
                LEFT JOIN FETCH e.lines l
                WHERE e.id = :ecritureId
            """)
    Optional<Ecriture> findEcritureByIdCustom(@Param("ecritureId") Long ecritureId);


    @Query("""
                SELECT new com.pacioli.core.DTO.EcritureExportDTO(
                    e.uniqueEntryNumber, 
                    e.entryDate, 
                    j.name, 
                    p.filename, 
                    a.label, 
                    l.label, 
                    l.debit,
                    l.credit,
                    fd.invoiceNumber, 
                    fd.invoiceDate, 
                    fd.totalTTC, 
                    fd.totalHT, 
                    fd.totalTVA, 
                    fd.taxRate, 
                    fd.tier, 
                    fd.ice,
                    (SELECT SUM(l2.debit) FROM Line l2 WHERE l2.ecriture = e),
                    (SELECT SUM(l2.credit) FROM Line l2 WHERE l2.ecriture = e),
                    COALESCE(l.originalCurrency, e.originalCurrency, fd.originalCurrency, p.aiCurrency),
                    COALESCE(l.convertedCurrency, e.convertedCurrency, fd.convertedCurrency, p.convertedCurrency),
                    COALESCE(l.exchangeRate, e.exchangeRate, fd.exchangeRate, p.exchangeRate),
                    COALESCE(l.exchangeRateDate, e.exchangeRateDate, fd.exchangeRateDate, p.exchangeRateDate),
                    l.originalDebit,
                    l.originalCredit,
                    l.convertedDebit,
                    l.convertedCredit,
                    fd.convertedTotalTTC,
                    fd.convertedTotalHT,
                    fd.convertedTotalTVA,
                    l.usdDebit,
                    l.usdCredit,
                    fd.usdTotalTTC,
                    fd.usdTotalHT,
                    fd.usdTotalTVA
                )
                FROM Ecriture e
                JOIN e.piece p 
                JOIN p.dossier d 
                LEFT JOIN e.journal j 
                LEFT JOIN e.lines l 
                LEFT JOIN l.account a 
                LEFT JOIN p.factureData fd 
                LEFT JOIN Exercise ex ON ex.dossier.id = d.id 
                    AND e.entryDate BETWEEN ex.startDate AND ex.endDate
                WHERE d.id = :dossierId
                    AND (:exerciseId IS NULL OR ex.id = :exerciseId)
                    AND e.entryDate BETWEEN COALESCE(:startDate, e.entryDate) AND COALESCE(:endDate, CURRENT_DATE)
                    AND (:journalId IS NULL OR e.journal.id = :journalId)
                ORDER BY e.entryDate DESC, e.uniqueEntryNumber, l.id
            """)
    List<EcritureExportDTO> findEcrituresByFilters(
            @Param("dossierId") Long dossierId,
            @Param("exerciseId") Long exerciseId,
            @Param("journalId") Long journalId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COUNT(e) FROM Ecriture e WHERE e.manuallyUpdated = true")
    Long countManuallyUpdatedEcritures();

    @Query("SELECT COUNT(e) FROM Ecriture e WHERE e.manuallyUpdated = true AND e.manualUpdateDate BETWEEN :startDate AND :endDate")
    Long countManuallyUpdatedEcrituresInPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Count manually updated ecritures by cabinet
    @Query("SELECT e.piece.dossier.cabinet.id, COUNT(e) FROM Ecriture e WHERE e.manuallyUpdated = true GROUP BY e.piece.dossier.cabinet.id")
    List<Object[]> countManuallyUpdatedEcrituresByCabinet();

    @Query("SELECT e.piece.dossier.cabinet.id, COUNT(e) FROM Ecriture e WHERE e.manuallyUpdated = true AND e.manualUpdateDate BETWEEN :startDate AND :endDate GROUP BY e.piece.dossier.cabinet.id")
    List<Object[]> countManuallyUpdatedEcrituresByCabinetInPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Get manual update trends by period
    @Query(value = "SELECT TO_CHAR(manual_update_date, 'YYYY-MM') as period, COUNT(*) FROM ecriture WHERE manually_updated = true AND manual_update_date IS NOT NULL GROUP BY TO_CHAR(manual_update_date, 'YYYY-MM') ORDER BY period", nativeQuery = true)
    List<Object[]> getManualUpdateTrendsByMonth();

    // Get last manual update date by cabinet
    @Query("SELECT e.piece.dossier.cabinet.id, MAX(e.manualUpdateDate) FROM Ecriture e WHERE e.manuallyUpdated = true GROUP BY e.piece.dossier.cabinet.id")
    List<Object[]> getLastManualUpdateDateByCabinet();

    @Query("SELECT COUNT(e) FROM Ecriture e WHERE e.piece.dossier.cabinet.id = :cabinetId")
    Long countEcrituresByCabinet(@Param("cabinetId") Long cabinetId);

}

