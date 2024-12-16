package com.pacioli.core.services;

import com.pacioli.core.DTO.PieceDTO;
import com.pacioli.core.models.Piece;

import java.util.List;

public interface PieceService {
    Piece savePiece(Piece piece);
    Piece getPieceById(Long id); // Add this method
    List<Piece> getPiecesByDossier(Long dossierId);
    List<Piece> getAllPieces();
    void deletePiece(Long id);
}
