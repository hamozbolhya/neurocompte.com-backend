package com.pacioli.core.DTO;

import lombok.Data;

@Data
public class UpdatePieceStatusRequest {
    private String status;
    /** Optional; stored when status is REJECTED */
    private String motifOfRejection;
}
