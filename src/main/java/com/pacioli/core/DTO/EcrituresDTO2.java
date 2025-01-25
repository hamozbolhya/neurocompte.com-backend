package com.pacioli.core.DTO;

import lombok.Data;
import java.util.List;

@Data
public class EcrituresDTO2 {

        private Long id;
        private String uniqueEntryNumber;
        private String entryDate;
        private JournalDTO journal;
        private List<LineDTO> lines;
        private PieceDTO piece; // Add this field
}
