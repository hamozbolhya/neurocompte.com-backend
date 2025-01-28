package com.pacioli.core.services;

import com.pacioli.core.DTO.PieceDTO;
import com.pacioli.core.DTO.UpdatePieceStatusRequest;
import com.pacioli.core.models.Piece;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface PieceService {
    Piece getPieceById(Long id); // Add this method
    List<Piece> getPiecesByDossier(Long dossierId);
    List<Piece> getAllPieces();
    void deletePiece(Long id);

    Piece savePiece(String pieceData, MultipartFile file, Long dossierId) throws IOException;
    Piece saveEcrituresAndFacture(Long pieceId ,Long dossierId , String pieceData);

    void notifyPiecesUpdate(Long dossierId);

    Piece updatePieceStatus(Long id, String request);
}
