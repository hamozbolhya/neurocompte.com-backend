package com.pacioli.core.batches.processors;

import com.pacioli.core.models.Piece;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AIProcessorFactory {

    @Autowired
    private NormalAIProcessor normalAIProcessor;

    @Autowired
    private BankAIProcessor bankAIProcessor;

    public BaseAIProcessor getProcessor(Piece piece) {
        boolean isBankStatement = "Relevés bancaires".equals(piece.getType());
        log.info("🏭 Factory: Piece type {} -> Using {} processor",
                piece.getType(), isBankStatement ? "BANK" : "NORMAL");

        return isBankStatement ? bankAIProcessor : normalAIProcessor;
    }

    public void processPieceWithRetry(Piece piece, int attempt) throws InterruptedException {
        BaseAIProcessor processor = getProcessor(piece);

        if (processor instanceof NormalAIProcessor) {
            ((NormalAIProcessor) processor).processPieceWithRetry(piece, attempt);
        } else if (processor instanceof BankAIProcessor) {
            ((BankAIProcessor) processor).processPieceWithRetry(piece, attempt);
        }
    }
}