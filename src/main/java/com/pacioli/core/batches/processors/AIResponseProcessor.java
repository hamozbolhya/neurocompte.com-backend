package com.pacioli.core.batches.processors;

import com.pacioli.core.models.Piece;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AIResponseProcessor {

    @Autowired
    private AIProcessorFactory processorFactory;

    public void processPieceWithRetry(Piece piece, int attempt) throws InterruptedException {
        log.info("🚀 Starting AI processing for piece {} (attempt {})", piece.getId(), attempt);
        processorFactory.processPieceWithRetry(piece, attempt);
    }
}