package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacioli.core.DTO.*;
import com.pacioli.core.batches.DTO.BaseDTOBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class EcritureBuilder extends BaseDTOBuilder {

    public List<EcrituresDTO2> buildEcritures(JsonNode ecrituresNode) {
        List<EcrituresDTO2> ecritures = new ArrayList<>();

        if (ecrituresNode == null || !ecrituresNode.isArray() || ecrituresNode.size() == 0) {
            log.warn("⚠️ No ecritures data available");
            return ecritures;
        }

        try {
            EcrituresDTO2 ecriture = new EcrituresDTO2();
            ecriture.setUniqueEntryNumber(UUID.randomUUID().toString());

            String dateStr = extractStringSafely(ecrituresNode.get(0), "Date", "");
            String formattedDate = formatDateToStandard(dateStr);
            ecriture.setEntryDate(formattedDate);

            ecriture.setJournal(buildJournal(ecrituresNode.get(0)));

            List<LineDTO> lines = buildLines(ecrituresNode);
            ecriture.setLines(lines);

            ecritures.add(ecriture);
            log.info("✅ Built ecriture with {} lines", lines.size());

        } catch (Exception e) {
            log.error("❌ Error building ecriture: {}", e.getMessage());
        }

        return ecritures;
    }

    private List<LineDTO> buildLines(JsonNode ecrituresNode) {
        List<LineDTO> lines = new ArrayList<>();

        if (ecrituresNode == null || !ecrituresNode.isArray()) {
            log.error("❌ Invalid ecrituresNode in buildLines");
            return lines;
        }

        for (JsonNode entry : ecrituresNode) {
            try {
                LineDTO line = buildLine(entry);
                if (line != null) {
                    lines.add(line);
                }
            } catch (Exception e) {
                log.error("❌ Error building line from entry: {}", e.getMessage());
            }
        }

        log.info("✅ Built {} lines from ecritures", lines.size());
        return lines;
    }

    private LineDTO buildLine(JsonNode entry) {
        LineDTO line = new LineDTO();

        try {
            line.setLabel(extractStringSafely(entry, "EcritLib", "Unknown Entry"));

            // Handle debit amounts
            if (entry.has("OriginalDebitAmt")) {
                line.setOriginalDebit(parseDoubleSafely(entry, "OriginalDebitAmt"));
                line.setDebit(parseDoubleSafely(entry, "OriginalDebitAmt"));
                line.setConvertedDebit(parseDoubleSafely(entry, "DebitAmt"));
            } else {
                line.setDebit(parseDoubleSafely(entry, "DebitAmt"));
            }

            // Handle credit amounts
            if (entry.has("OriginalCreditAmt")) {
                line.setOriginalCredit(parseDoubleSafely(entry, "OriginalCreditAmt"));
                line.setCredit(parseDoubleSafely(entry, "OriginalCreditAmt"));
                line.setConvertedCredit(parseDoubleSafely(entry, "CreditAmt"));
            } else {
                line.setCredit(parseDoubleSafely(entry, "CreditAmt"));
            }

            // Set currency information
            line.setOriginalCurrency(extractStringSafely(entry, "OriginalDevise", null));
            line.setConvertedCurrency(extractStringSafely(entry, "Devise", "USD"));

            // Set account information
            AccountDTO account = new AccountDTO();
            account.setAccount(extractStringSafely(entry, "CompteNum", "0000"));
            account.setLabel(extractStringSafely(entry, "CompteLib", "Unknown Account"));
            line.setAccount(account);

            log.debug("✅ Built line: {} - Debit: {}, Credit: {}",
                    line.getLabel(), line.getDebit(), line.getCredit());

            return line;

        } catch (Exception e) {
            log.error("❌ Error building line: {}", e.getMessage());
            return null;
        }
    }

    private JournalDTO buildJournal(JsonNode entry) {
        JournalDTO journal = new JournalDTO();
        journal.setName(extractStringSafely(entry, "JournalCode", "Unknown"));
        journal.setType(extractStringSafely(entry, "JournalLib", "Unknown"));
        return journal;
    }
}