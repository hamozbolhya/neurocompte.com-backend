package com.pacioli.core.repositories;

import com.pacioli.core.DTO.PieceStatsDTO;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Piece;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface PieceRepository extends JpaRepository<Piece, Long> {
    List<Piece> findByDossierId(Long dossierId);

    @Query("SELECT p FROM Piece p " +
            "LEFT JOIN FETCH p.ecritures e " +
            "LEFT JOIN FETCH e.journal j " +
            "LEFT JOIN FETCH e.lines l " +
            "LEFT JOIN FETCH l.account a " +
            "WHERE p.dossier.id = :dossierId " +
            "ORDER BY p.uploadDate DESC")
    List<Piece> findByDossierIdWithDetailsOrderByUploadDateDesc(@Param("dossierId") Long dossierId);

    List<Piece> findTop20ByStatusOrderByUploadDateAsc(PieceStatus status);

    List<Piece> findTop20ByStatusInOrderByUploadDateAsc(Collection<PieceStatus> statuses);

    /**
     * Count the number of pieces uploaded by a specific user in a specific cabinet
     *
     * @param cabinetId The ID of the cabinet
     * @return The count of pieces
     */
    @Query("SELECT COUNT(p) FROM Piece p WHERE p.dossier.cabinet.id = :cabinetId")
    Long countByUploaderAndCabinetId(@Param("cabinetId") Long cabinetId);

    // Custom query to get piece stats for a single dossier
    @Query("SELECT new com.pacioli.core.DTO.PieceStatsDTO(" +
            "d.id, d.name, " +
            "COUNT(p), " +
            "SUM(CASE WHEN p.status = com.pacioli.core.enums.PieceStatus.UPLOADED THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN p.status = com.pacioli.core.enums.PieceStatus.PROCESSED THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN p.status = com.pacioli.core.enums.PieceStatus.REJECTED THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN p.status = com.pacioli.core.enums.PieceStatus.PROCESSING THEN 1 ELSE 0 END), " +
            "COALESCE((SELECT c.currency.code FROM Dossier dos JOIN dos.country c WHERE dos.id = d.id), ''), " +
            "COALESCE((SELECT c.code FROM Dossier dos JOIN dos.country c WHERE dos.id = d.id), '')) " +
            "FROM Dossier d LEFT JOIN d.pieces p " +
            "WHERE d.id = :dossierId " +
            "GROUP BY d.id, d.name")
    PieceStatsDTO getPieceStatsByDossierId(@Param("dossierId") Long dossierId);

    @Query("SELECT new com.pacioli.core.DTO.PieceStatsDTO(" +
            "d.id, d.name, " +
            "COUNT(p), " +
            "SUM(CASE WHEN p.status = com.pacioli.core.enums.PieceStatus.UPLOADED THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN p.status = com.pacioli.core.enums.PieceStatus.PROCESSED THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN p.status = com.pacioli.core.enums.PieceStatus.REJECTED THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN p.status = com.pacioli.core.enums.PieceStatus.PROCESSING THEN 1 ELSE 0 END), " +
            "COALESCE((SELECT c.currency.code FROM Dossier dos JOIN dos.country c WHERE dos.id = d.id), ''), " +
            "COALESCE((SELECT c.code FROM Dossier dos JOIN dos.country c WHERE dos.id = d.id), '')) " +
            "FROM Dossier d LEFT JOIN d.pieces p " +
            "WHERE d.cabinet.id = :cabinetId " +
            "GROUP BY d.id, d.name")
    List<PieceStatsDTO> getPieceStatsByCabinetId(@Param("cabinetId") Long cabinetId);

    @Query("SELECT p FROM Piece p WHERE p.dossier.id = :dossierId AND p.originalFileName = :originalFileName AND p.isDuplicate = false AND p.status != 'DUPLICATE'")
    Optional<Piece> findByDossierIdAndOriginalFileName(@Param("dossierId") Long dossierId, @Param("originalFileName") String originalFileName);

    @Query("SELECT p FROM Piece p " +
            "JOIN p.factureData fd " +
            "WHERE p.dossier.id = :dossierId " +
            "AND fd.invoiceDate = :invoiceDate " +
            "AND fd.totalTTC = :totalTTC " +
            "AND p.isDuplicate = false " +
            "AND p.status != 'DUPLICATE' " +
            "ORDER BY p.uploadDate ASC")
    List<Piece> findFunctionalDuplicates(@Param("dossierId") Long dossierId,
                                         @Param("invoiceDate") Date invoiceDate,
                                         @Param("totalTTC") Double totalTTC);


    @Query("SELECT DISTINCT p FROM Piece p " +
            "JOIN p.ecritures e " +
            "JOIN e.lines l " +
            "WHERE p.dossier.id = :dossierId " +
            "AND e.entryDate = :entryDate " +
            "AND p.isDuplicate = false " +
            "AND p.status != 'DUPLICATE' " +
            "AND (l.debit = :maxAmount OR l.credit = :maxAmount) " +
            "ORDER BY p.uploadDate ASC")
    List<Piece> findByEcritureData(@Param("dossierId") Long dossierId,
                                   @Param("entryDate") LocalDate entryDate,
                                   @Param("maxAmount") Double maxAmount);

    @Query("SELECT DISTINCT p FROM Piece p " +
            "JOIN p.ecritures e " +
            "JOIN e.lines l " +
            "WHERE p.dossier.id = :dossierId " +
            "AND e.entryDate = :entryDate " +
            "AND p.isDuplicate = false " +
            "AND p.status != 'DUPLICATE' " +
            "AND ((l.debit BETWEEN :minAmount AND :maxAmount) OR (l.credit BETWEEN :minAmount AND :maxAmount)) " +
            "ORDER BY p.uploadDate ASC")
    List<Piece> findByEcritureDataWithTolerance(@Param("dossierId") Long dossierId,
                                                @Param("entryDate") LocalDate entryDate,
                                                @Param("minAmount") Double minAmount,
                                                @Param("maxAmount") Double maxAmount);


    @Query("SELECT p FROM Piece p WHERE p.originalPiece.id = :originalPieceId")
    List<Piece> findDuplicatesByOriginalPieceId(@Param("originalPieceId") Long originalPieceId);

    @Query("SELECT p FROM Piece p " +
            "WHERE p.dossier.id = :dossierId " +
            "AND p.isDuplicate = false " +
            "AND p.status != 'DUPLICATE' " +
            "AND (LOWER(p.originalFileName) LIKE LOWER(CONCAT('%', :baseFileName, '%')) " +
            "OR LOWER(:originalFileName) LIKE LOWER(CONCAT('%', SUBSTRING(p.originalFileName, 1, LENGTH(p.originalFileName) - 4), '%'))) " +
            "ORDER BY p.uploadDate ASC")
    List<Piece> findSimilarFileNames(@Param("dossierId") Long dossierId,
                                     @Param("originalFileName") String originalFileName,
                                     @Param("baseFileName") String baseFileName);



}