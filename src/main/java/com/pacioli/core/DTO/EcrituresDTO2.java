package com.pacioli.core.DTO;

import com.pacioli.core.models.Piece;
import lombok.Data;
import java.util.List;

@Data
public class EcrituresDTO2 {

        private Long id;
        private String uniqueEntryNumber;
        private String entryDate;
        private JournalDTO journal;
        private List<LineDTO> lines; // ← ADD THIS FIELD
        private Piece piece;
}
