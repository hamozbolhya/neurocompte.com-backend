package com.pacioli.core.DTO;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class EcritureDTO {
    private Long id;
    private String uniqueEntryNumber;
    private LocalDate entryDate;
    private JournalDTO journal;
    private List<LineDTO> lines;
//    private PieceDTO piece; // Add this field
    private Boolean amountUpdated;
    private Boolean manuallyUpdated;
    private LocalDate manualUpdateDate;
}
