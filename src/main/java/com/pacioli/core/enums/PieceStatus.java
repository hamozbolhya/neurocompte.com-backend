package com.pacioli.core.enums;

public enum PieceStatus {
    UPLOADED,  // When the piece is created and waiting To Send TO AI for processing
    PROCESSING,  // When the piece is Sended to AI and waiting for AI processing
    PROCESSED,  // When the ecritures and facture are processed and linked to the piece
    REJECTED    // When the Piece file rejected
}
