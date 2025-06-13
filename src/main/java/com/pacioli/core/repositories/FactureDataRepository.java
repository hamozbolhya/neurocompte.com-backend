package com.pacioli.core.repositories;

import com.pacioli.core.models.FactureData;
import com.pacioli.core.models.Piece;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FactureDataRepository extends JpaRepository<FactureData, Long> {
    Optional<FactureData> findByPiece(Piece piece);
    Optional<FactureData> findByPieceDossierIdAndPieceOriginalFileName(Long dossierId, String originalFileName);
}

