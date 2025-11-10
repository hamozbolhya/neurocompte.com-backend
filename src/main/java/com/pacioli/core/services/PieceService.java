package com.pacioli.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacioli.core.DTO.PieceDTO;
import com.pacioli.core.DTO.PieceStatsDTO;
import com.pacioli.core.models.Piece;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface PieceService {
    // Core CRUD operations
    Piece getPieceById(Long id);
    PieceDTO getPieceDetails(Long pieceId);
    void deletePiece(Long id);

    // Query operations
    List<Piece> getPiecesByDossierIdSortedByDate(Long id);
    Page<PieceDTO> getPiecesByDossier(Long dossierId, Pageable pageable);
    Page<PieceDTO> getPiecesForUser(UUID userId, Pageable pageable);

    // Business operations
    Piece savePiece(String pieceData, MultipartFile file, Long dossierId, String country);
    Piece saveEcrituresAndFacture(Long pieceId, Long dossierId, String pieceData, JsonNode originalAiResponse);

    // Status operations
    Piece updatePieceStatus(Long id, String status);
    Piece forcePieceNotDuplicate(Long pieceId);

    // Statistics operations
    PieceStatsDTO getPieceStatsByDossier(Long dossierId);
    List<PieceStatsDTO> getPieceStatsByCabinet(Long cabinetId);

    // File operations
    byte[] getPieceFilesAsZip(Long pieceId);

    // Notification operations
    void notifyPiecesUpdate(Long dossierId);

    default Piece saveEcrituresAndFacture(Long pieceId, Long dossierId, String pieceData) {
        return saveEcrituresAndFacture(pieceId, dossierId, pieceData, null);
    }
}