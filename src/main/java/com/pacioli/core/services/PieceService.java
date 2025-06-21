package com.pacioli.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacioli.core.DTO.PieceStatsDTO;
import com.pacioli.core.models.Piece;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface PieceService {
    Piece getPieceById(Long id);
    List<Piece> getPiecesByDossierIdSortedByDate(Long id);
    List<Piece> getPiecesByDossier(Long dossierId);
    void deletePiece(Long id);

    Piece savePiece(String pieceData, MultipartFile file, Long dossierId, String country) throws IOException;

    // NEW: Method with JsonNode for exact precision (called from AI processing)
    Piece saveEcrituresAndFacture(Long pieceId, Long dossierId, String pieceData, JsonNode originalAiResponse);

    // KEEP: Original method for backward compatibility (called from controllers)
    default Piece saveEcrituresAndFacture(Long pieceId, Long dossierId, String pieceData) {
        return saveEcrituresAndFacture(pieceId, dossierId, pieceData, null);
    }
    void notifyPiecesUpdate(Long dossierId);
    Piece updatePieceStatus(Long id, String request);
    PieceStatsDTO getPieceStatsByDossier(Long dossierId);
    List<PieceStatsDTO> getPieceStatsByCabinet(Long cabinetId);
    byte[] getPieceFilesAsZip(Long pieceId);

    Piece forcePieceNotDuplicate(Long pieceId);
}