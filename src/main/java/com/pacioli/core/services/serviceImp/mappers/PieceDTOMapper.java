package com.pacioli.core.services.serviceImp.mappers;

import com.pacioli.core.DTO.AccountDTO;
import com.pacioli.core.DTO.EcrituresDTO2;
import com.pacioli.core.DTO.FactureDataDTO;
import com.pacioli.core.DTO.JournalDTO;
import com.pacioli.core.DTO.LineDTO;
import com.pacioli.core.DTO.PieceDTO;
import com.pacioli.core.models.Ecriture;
import com.pacioli.core.models.Line;
import com.pacioli.core.models.Piece;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PieceDTOMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public PieceDTO toBasicDTO(Piece piece) {
        PieceDTO dto = new PieceDTO();
        dto.setId(piece.getId());
        dto.setFilename(piece.getFilename());
        dto.setOriginalFileName(piece.getOriginalFileName());
        dto.setType(piece.getType());
        dto.setStatus(piece.getStatus());
        dto.setUploadDate(piece.getUploadDate());
        dto.setAmount(piece.getAmount());
        dto.setDossierId(piece.getDossier().getId());
        dto.setDossierName(piece.getDossier().getName());
        dto.setIsForced(piece.getIsForced());

        // Add duplicate information
        dto.setIsDuplicate(piece.getIsDuplicate());
        if (piece.getOriginalPiece() != null) {
            dto.setOriginalPieceId(piece.getOriginalPiece().getId());
            dto.setOriginalPieceName(piece.getOriginalPiece().getOriginalFileName());
        }

        // Add AI currency and amount info
        dto.setAiCurrency(piece.getAiCurrency());
        dto.setAiAmount(piece.getAiAmount());

        // Add exchange rate info
        dto.setExchangeRate(piece.getExchangeRate());
        dto.setConvertedCurrency(piece.getConvertedCurrency());
        dto.setExchangeRateDate(piece.getExchangeRateDate());
        dto.setExchangeRateUpdated(piece.getExchangeRateUpdated());

        return dto;
    }

    public void addFactureDataIfExists(Piece piece, PieceDTO dto) {
        if (piece.getFactureData() != null) {
            FactureDataDTO factureDataDTO = new FactureDataDTO();
            factureDataDTO.setInvoiceNumber(piece.getFactureData().getInvoiceNumber());
            factureDataDTO.setTotalTVA(piece.getFactureData().getTotalTVA());
            factureDataDTO.setTaxRate(piece.getFactureData().getTaxRate());
            factureDataDTO.setInvoiceDate(piece.getFactureData().getInvoiceDate());

            // Add currency information
            factureDataDTO.setDevise(piece.getFactureData().getDevise());
            factureDataDTO.setOriginalCurrency(piece.getFactureData().getOriginalCurrency());
            factureDataDTO.setConvertedCurrency(piece.getFactureData().getConvertedCurrency());
            factureDataDTO.setExchangeRate(piece.getFactureData().getExchangeRate());

            dto.setFactureData(factureDataDTO);
        }
    }

    public void addEcrituresIfExists(Piece piece, PieceDTO dto) {
        if (piece.getEcritures() != null && !piece.getEcritures().isEmpty()) {
            List<EcrituresDTO2> ecrituresDTOs = piece.getEcritures().stream()
                    .map(this::mapEcritureToDTO)
                    .collect(Collectors.toList());
            dto.setEcritures(ecrituresDTOs);
        }
    }

    private EcrituresDTO2 mapEcritureToDTO(Ecriture ecriture) {
        EcrituresDTO2 dto2 = new EcrituresDTO2();
        dto2.setUniqueEntryNumber(ecriture.getUniqueEntryNumber());
        dto2.setEntryDate(ecriture.getEntryDate().format(DATE_FORMATTER));

        // Add journal information
        if (ecriture.getJournal() != null) {
            JournalDTO journalDTO = new JournalDTO();
            journalDTO.setName(ecriture.getJournal().getName());
            journalDTO.setType(ecriture.getJournal().getType());
            dto2.setJournal(journalDTO);
        }

        // Map lines with account information
        if (ecriture.getLines() != null && !ecriture.getLines().isEmpty()) {
            List<LineDTO> linesDTOs = ecriture.getLines().stream()
                    .map(this::mapLineToDTO)
                    .collect(Collectors.toList());
            dto2.setLines(linesDTOs);
        }

        return dto2;
    }

    private LineDTO mapLineToDTO(Line line) {
        LineDTO lineDTO = new LineDTO();
        lineDTO.setId(line.getId());
        lineDTO.setLabel(line.getLabel());
        lineDTO.setDebit(line.getDebit());
        lineDTO.setCredit(line.getCredit());

        // Add account information
        if (line.getAccount() != null) {
            AccountDTO accountDTO = new AccountDTO();
            accountDTO.setId(line.getAccount().getId());
            accountDTO.setAccount(line.getAccount().getAccount());
            accountDTO.setLabel(line.getAccount().getLabel());
            lineDTO.setAccount(accountDTO);
        }

        // Add currency information if available
        lineDTO.setOriginalDebit(line.getOriginalDebit());
        lineDTO.setOriginalCredit(line.getOriginalCredit());
        lineDTO.setOriginalCurrency(line.getOriginalCurrency());
        lineDTO.setConvertedDebit(line.getConvertedDebit());
        lineDTO.setConvertedCredit(line.getConvertedCredit());
        lineDTO.setConvertedCurrency(line.getConvertedCurrency());
        lineDTO.setExchangeRate(line.getExchangeRate());
        lineDTO.setExchangeRateDate(line.getExchangeRateDate());
        lineDTO.setUsdDebit(line.getUsdDebit());
        lineDTO.setUsdCredit(line.getUsdCredit());

        return lineDTO;
    }
}
