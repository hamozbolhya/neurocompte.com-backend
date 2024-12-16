package com.pacioli.core.repositories;

import com.pacioli.core.models.Piece;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PieceRepository extends JpaRepository<Piece, Long> {
    List<Piece> findByDossierId(Long dossierId);

    @Query("SELECT DISTINCT p FROM Piece p LEFT JOIN FETCH p.ecritures WHERE p.dossier.id = :dossierId")
    List<Piece> findPiecesWithEcritures(@Param("dossierId") Long dossierId);

}
