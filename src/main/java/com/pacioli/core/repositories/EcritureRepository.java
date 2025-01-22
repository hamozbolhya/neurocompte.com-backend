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

    // Find Ecritures by Cabinet ID
    @Query("SELECT e FROM Ecriture e WHERE e.piece.dossier.cabinet.id = :cabinetId")
    List<Ecriture> findByPiece_Dossier_Cabinet_Id(@Param("cabinetId") Long cabinetId);

  /*  @Query("SELECT e FROM Ecriture e " +
            "JOIN e.piece p " +
            "JOIN e.journal j " +
            "JOIN p.dossier d " +
            "JOIN d.exercises ex " +
            "WHERE ex.id = :exerciseId AND d.cabinet.id = :cabinetId")
    List<Ecriture> findEcrituresByExerciseAndCabinet(@Param("exerciseId") Long exerciseId,
                                                     @Param("cabinetId") Long cabinetId);*/

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


    @Query("SELECT DISTINCT e FROM Ecriture e LEFT JOIN FETCH e.lines WHERE e.piece.id IN :pieceIds")
    List<Ecriture> findEcrituresWithLines(@Param("pieceIds") List<Long> pieceIds);


/*    @Query("SELECT e FROM Ecriture e " +
            "JOIN e.piece p " +
            "JOIN p.dossier d " +
            "JOIN d.exercises ex " +
            "WHERE d.id = :dossierId AND ex.id = :exerciseId")
    List<Ecriture> findByDossierAndExerciseId(@Param("dossierId") Long dossierId, @Param("exerciseId") Long exerciseId);*/

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
        SUM(DISTINCT l.debit),
        SUM(DISTINCT l.credit),
        fd.invoiceNumber, 
        fd.invoiceDate, 
        fd.totalTTC, 
        fd.totalHT, 
        fd.totalTVA, 
        fd.taxRate, 
        fd.tier, 
        fd.ice
    )
    FROM 
        Ecriture e
    JOIN 
        e.piece p 
    JOIN 
        p.dossier d 
    LEFT JOIN 
        e.journal j 
    LEFT JOIN 
        e.lines l 
    LEFT JOIN 
        l.account a 
    LEFT JOIN 
        p.factureData fd 
    LEFT JOIN 
        Exercise ex ON ex.dossier.id = d.id 
        AND e.entryDate BETWEEN ex.startDate AND ex.endDate
    WHERE 
        d.id = :dossierId
        AND (:exerciseId IS NULL OR ex.id = :exerciseId)
        AND e.entryDate BETWEEN COALESCE(:startDate, e.entryDate) AND COALESCE(:endDate, CURRENT_DATE)
        AND (:journalId IS NULL OR e.journal.id = :journalId)
    GROUP BY 
        e.uniqueEntryNumber, 
        e.entryDate, 
        j.name, 
        p.filename, 
        a.label, 
        l.label, 
        fd.invoiceNumber, 
        fd.invoiceDate, 
        fd.totalTTC, 
        fd.totalHT, 
        fd.totalTVA, 
        fd.taxRate, 
        fd.tier, 
        fd.ice
    ORDER BY 
        e.entryDate DESC
""")
    List<EcritureExportDTO> findEcrituresByFilters(
            @Param("dossierId") Long dossierId,
            @Param("exerciseId") Long exerciseId,
            @Param("journalId") Long journalId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

   /* @Query("""
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
        fd.ice
    )
    FROM 
        Ecriture e
    JOIN 
        e.piece p 
    JOIN 
        p.dossier d 
    LEFT JOIN 
        e.journal j 
    LEFT JOIN 
        e.lines l 
    LEFT JOIN 
        l.account a 
    LEFT JOIN 
        p.factureData fd 
    LEFT JOIN 
        Exercise ex ON ex.dossier.id = d.id
   WHERE
       d.id = :dossierId
       AND (
           (:exerciseId IS NULL OR (ex.id = :exerciseId AND e.entryDate BETWEEN ex.startDate AND ex.endDate))
           AND e.entryDate BETWEEN COALESCE(:startDate, e.entryDate) AND COALESCE(:endDate, CURRENT_DATE)
       )
       AND (:journalId IS NULL OR e.journal.id = :journalId)
    ORDER BY 
        e.entryDate DESC
""")
    List<EcritureExportDTO> findEcrituresByFilters(
            @Param("dossierId") Long dossierId,
            @Param("exerciseId") Long exerciseId,
            @Param("journalId") Long journalId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
*/

}

