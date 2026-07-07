package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.PieceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateDetectionServiceTest {

    @Mock
    private PieceRepository pieceRepository;

    @InjectMocks
    private DuplicateDetectionService duplicateDetectionService;

    @Test
    void checkTechnicalDuplicateKeepsTrailingBusinessNumbersInBaseFilename() {
        Piece piece = piece("ADM 2.pdf");

        when(pieceRepository.findAllByDossierIdAndOriginalFileNameIgnoreCase(8L, "ADM 2.pdf"))
                .thenReturn(List.of(piece));
        when(pieceRepository.findSimilarFileNames(8L, "ADM 2.pdf", "ADM 2"))
                .thenReturn(List.of());

        duplicateDetectionService.checkTechnicalDuplicate(piece);

        verify(pieceRepository).findSimilarFileNames(
                eq(8L),
                eq("ADM 2.pdf"),
                eq("ADM 2")
        );
    }

    @Test
    void checkTechnicalDuplicateStillStripsCopySuffixes() {
        Piece piece = piece("ADM (1).pdf");

        when(pieceRepository.findAllByDossierIdAndOriginalFileNameIgnoreCase(8L, "ADM (1).pdf"))
                .thenReturn(List.of(piece));
        when(pieceRepository.findSimilarFileNames(8L, "ADM (1).pdf", "ADM"))
                .thenReturn(List.of());

        duplicateDetectionService.checkTechnicalDuplicate(piece);

        verify(pieceRepository).findSimilarFileNames(
                eq(8L),
                eq("ADM (1).pdf"),
                eq("ADM")
        );
    }

    private static Piece piece(String originalFileName) {
        Dossier dossier = new Dossier();
        dossier.setId(8L);

        Piece piece = new Piece();
        piece.setId(1660L);
        piece.setDossier(dossier);
        piece.setOriginalFileName(originalFileName);
        return piece;
    }
}
