package com.pacioli.core.repositories;

import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Piece;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.awt.print.Pageable;
import java.util.Collection;
import java.util.List;

@Repository
public interface PieceRepository extends JpaRepository<Piece, Long> {
    List<Piece> findByDossierId(Long dossierId);
    //List<Piece> findByDossierIdOrderByUploadDateDesc(Long dossierId);
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

}
