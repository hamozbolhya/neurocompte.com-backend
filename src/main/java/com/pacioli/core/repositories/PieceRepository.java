package com.pacioli.core.repositories;

import com.pacioli.core.DTO.PieceStatsDTO;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Piece;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PieceRepository extends JpaRepository<Piece, Long> {

    // ==================== DUPLICATION DETECTION METHODS ====================

    // Find by filename (exact match)
    Optional<Piece> findByFilename(String filename);

    // Find by file hash
    List<Piece> findByFileHash(String fileHash);

    // Find similar AI data with amount range
    @Query("SELECT p FROM Piece p WHERE " +
            "p.dossier.id = :dossierId AND " +
            "p.aiAmount BETWEEN :minAmount AND :maxAmount AND " +
            "p.aiCurrency = :currency AND " +
            "p.uploadDate = :uploadDate AND " +
            "p.isDuplicate = false AND " +
            "p.status != 'DUPLICATE'")
    List<Piece> findSimilarAIData(@Param("dossierId") Long dossierId,
                                  @Param("minAmount") Double minAmount,
                                  @Param("maxAmount") Double maxAmount,
                                  @Param("currency") String currency,
                                  @Param("uploadDate") Date uploadDate);

    // Find by AI data with tolerance
    @Query("SELECT p FROM Piece p WHERE " +
            "p.dossier.id = :dossierId AND " +
            "p.aiCurrency = :currency AND " +
            "ABS(p.aiAmount - :amount) <= :tolerance AND " +
            "p.isDuplicate = false AND " +
            "p.status != 'DUPLICATE'")
    List<Piece> findByAIDataWithTolerance(@Param("dossierId") Long dossierId,
                                          @Param("amount") Double amount,
                                          @Param("currency") String currency,
                                          @Param("tolerance") Double tolerance);

    // ==================== EXISTING METHODS ====================

    Page<Piece> findByDossierId(Long dossierId, Pageable pageable);
    List<Piece> findAllByDossierIdAndOriginalFileName(Long dossierId, String originalFileName);
    List<Piece> findByOriginalPieceId(Long originalPieceId);

    @Query("SELECT p FROM Piece p " +
            "LEFT JOIN FETCH p.ecritures e " +
            "LEFT JOIN FETCH e.journal j " +
            "LEFT JOIN FETCH e.lines l " +
            "LEFT JOIN FETCH l.account a " +
            "WHERE p.dossier.id = :dossierId " +
            "ORDER BY p.uploadDate DESC")
    List<Piece> findByDossierIdWithDetailsOrderByUploadDateDesc(@Param("dossierId") Long dossierId);

    @Query("SELECT p FROM Piece p WHERE p.status = :status ORDER BY p.uploadDate ASC")
    List<Piece> findTopNByStatusOrderByUploadDateAsc(@Param("status") PieceStatus status,
                                                     Pageable pageable);

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


//    Long countPiecesByStatus(PieceStatus status);

    Long countByType(String type);

    List<Piece> findByType(String type);

    // Period-based queries
    Long countPiecesByUploadDateBetween(Date startDate, Date endDate);

    Long countPiecesByStatusAndUploadDateBetween(PieceStatus status, Date startDate, Date endDate);

    Long countByTypeAndUploadDateBetween(String type, Date startDate, Date endDate);

    List<Piece> findByTypeAndUploadDateBetween(String type, Date startDate, Date endDate);

    List<Piece> findByUploadDateBetween(Date startDate, Date endDate);

    // Cabinet-based queries
    Long countByDossierCabinetId(Long cabinetId);

    Long countByDossierCabinetIdAndStatus(Long cabinetId, PieceStatus status);

    Long countByDossierCabinetIdAndType(Long cabinetId, String type);

    Long countByIsForcedTrue();

    Long countByIsForcedTrueAndUploadDateBetween(Date startDate, Date endDate);

    @Query("SELECT COUNT(p) FROM Piece p WHERE p.dossier.cabinet.id = :cabinetId AND p.isForced = true")
    Long countByDossierCabinetIdAndIsForcedTrue(@Param("cabinetId") Long cabinetId);

    // Get forced pieces grouped by cabinet
    @Query("SELECT p.dossier.cabinet.id, COUNT(p) FROM Piece p WHERE p.isForced = true GROUP BY p.dossier.cabinet.id")
    List<Object[]> countForcedPiecesByCabinet();

    @Query("SELECT p.dossier.cabinet.id, COUNT(p) FROM Piece p WHERE p.isForced = true AND p.uploadDate BETWEEN :startDate AND :endDate GROUP BY p.dossier.cabinet.id")
    List<Object[]> countForcedPiecesByCabinetAndPeriod(@Param("startDate") Date startDate, @Param("endDate") Date endDate);

    // Get forced pieces grouped by dossier
    @Query("SELECT p.dossier.id, COUNT(p) FROM Piece p WHERE p.isForced = true GROUP BY p.dossier.id")
    List<Object[]> countForcedPiecesByDossier();

    @Query("SELECT p.dossier.id, COUNT(p) FROM Piece p WHERE p.isForced = true AND p.uploadDate BETWEEN :startDate AND :endDate GROUP BY p.dossier.id")
    List<Object[]> countForcedPiecesByDossierAndPeriod(@Param("startDate") Date startDate, @Param("endDate") Date endDate);

    // Get forced pieces by dossier within a specific cabinet
    @Query("SELECT p.dossier.id, COUNT(p) FROM Piece p WHERE p.dossier.cabinet.id = :cabinetId AND p.isForced = true GROUP BY p.dossier.id")
    List<Object[]> countForcedPiecesByDossierInCabinet(@Param("cabinetId") Long cabinetId);


    @Query("SELECT d.cabinet.id, COUNT(p), d.cabinet.name FROM Piece p JOIN p.dossier d GROUP BY d.cabinet.id, d.cabinet.name")
    List<Object[]> countPiecesByAllCabinets();

    @Query("SELECT COUNT(p) FROM Piece p WHERE p.dossier IS NULL")
    Long countPiecesWithoutDossier();

    @Query("SELECT COUNT(p) FROM Piece p WHERE p.status != 'DELETED'") // Adjust based on your deletion strategy
    Long countActivePieces();

    @Query("SELECT p.id, p.filename, p.status, p.isForced, d.cabinet.id, d.cabinet.name FROM Piece p JOIN p.dossier d WHERE d.cabinet.id = :cabinetId")
    List<Object[]> findRawPieceDataByCabinet(@Param("cabinetId") Long cabinetId);

    // Make sure you have this method for status counting:
    @Query("SELECT COUNT(p) FROM Piece p WHERE p.status = :status")
    Long countPiecesByStatus(@Param("status") PieceStatus status);

    @Query("SELECT COUNT(p) FROM Piece p WHERE p.isDuplicate = true")
    Long countByIsDuplicateTrue();

    @Query("SELECT COUNT(p) FROM Piece p WHERE p.dossier.cabinet.id = :cabinetId AND p.uploadDate BETWEEN :startDate AND :endDate")
    Long countByDossierCabinetIdAndUploadDateBetween(@Param("cabinetId") Long cabinetId, @Param("startDate") Date startDate, @Param("endDate") Date endDate);

    @Query("SELECT COUNT(p) FROM Piece p WHERE p.dossier.cabinet.id = :cabinetId AND p.status = :status AND p.uploadDate BETWEEN :startDate AND :endDate")
    Long countByDossierCabinetIdAndStatusAndUploadDateBetween(@Param("cabinetId") Long cabinetId, @Param("status") PieceStatus status, @Param("startDate") Date startDate, @Param("endDate") Date endDate);

    @Query("SELECT COUNT(p) FROM Piece p WHERE p.dossier.cabinet.id = :cabinetId AND p.type = :type AND p.uploadDate BETWEEN :startDate AND :endDate")
    Long countByDossierCabinetIdAndTypeAndUploadDateBetween(@Param("cabinetId") Long cabinetId, @Param("type") String type, @Param("startDate") Date startDate, @Param("endDate") Date endDate);

    @Query("SELECT COUNT(p) FROM Piece p WHERE p.dossier.cabinet.id = :cabinetId AND p.isForced = true AND p.uploadDate BETWEEN :startDate AND :endDate")
    Long countByDossierCabinetIdAndIsForcedTrueAndUploadDateBetween(@Param("cabinetId") Long cabinetId, @Param("startDate") Date startDate, @Param("endDate") Date endDate);

    Page<Piece> findByDossierCabinetUsersId(UUID userId, Pageable pageable);

}