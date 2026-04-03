package com.pacioli.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacioli.core.DTO.PieceDTO;
import com.pacioli.core.DTO.PieceStatsDTO;
import com.pacioli.core.models.Piece;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface PieceService {
    Piece getPieceById(@NonNull Long id);
    PieceDTO getPieceDetails(@NonNull Long pieceId);
    void deletePiece(@NonNull Long id);

    List<Piece> getPiecesByDossierIdSortedByDate(@NonNull Long id);
    Page<PieceDTO> getPiecesByDossier(@NonNull Long dossierId, @NonNull Pageable pageable);
    Page<PieceDTO> getPiecesForUser(@NonNull UUID userId, @NonNull Pageable pageable);

    Piece savePiece(String pieceData, MultipartFile file, @NonNull Long dossierId, String country);
    Piece saveEcrituresAndFacture(@NonNull Long pieceId, @NonNull Long dossierId, String pieceData,
            @Nullable JsonNode originalAiResponse);

    Piece updatePieceStatus(@NonNull Long pieceId, String newStatus);
    Piece forcePieceNotDuplicate(@NonNull Long pieceId);

    PieceStatsDTO getPieceStatsByDossier(@NonNull Long dossierId);
    List<PieceStatsDTO> getPieceStatsByCabinet(@NonNull Long cabinetId);

    byte[] getPieceFilesAsZip(@NonNull Long pieceId);

    void notifyPiecesUpdate(@NonNull Long dossierId);

    default Piece saveEcrituresAndFacture(@NonNull Long pieceId, @NonNull Long dossierId, String pieceData) {
        return saveEcrituresAndFacture(pieceId, dossierId, pieceData, null);
    }
}
