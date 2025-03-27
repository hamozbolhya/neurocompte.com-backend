package com.pacioli.core.repositories;

import com.pacioli.core.DTO.PieceStatsDTO;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Piece;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PieceRepository extends JpaRepository<Piece, Long> {
    List<Piece> findByDossierId(Long dossierId);

    // Count total pieces for a dossier
    long countByDossierId(Long dossierId);

    // Count pieces by status for a dossier
    long countByDossierIdAndStatus(Long dossierId, PieceStatus status);

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
}