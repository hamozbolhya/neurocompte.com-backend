package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacioli.core.DTO.*;
import com.pacioli.core.batches.DTO.BaseDTOBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

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
            // First, extract all entries (flatten transaction groups if needed)
            List<JsonNode> allEntries = extractAllEntries(ecrituresNode);

            // Group entries by date
            Map<String, List<JsonNode>> entriesByDate = groupEntriesByDate(allEntries);

            // Create one Ecriture per date
            for (Map.Entry<String, List<JsonNode>> dateGroup : entriesByDate.entrySet()) {
                String date = dateGroup.getKey();
                List<JsonNode> entriesForDate = dateGroup.getValue();

                EcrituresDTO2 ecriture = createEcritureForDate(date, entriesForDate);
                if (ecriture != null) {
                    ecritures.add(ecriture);
                }
            }

            log.info("✅ Built {} ecritures for {} different dates", ecritures.size(), entriesByDate.size());

        } catch (Exception e) {
            log.error("❌ Error building ecritures: {}", e.getMessage());
        }

        return ecritures;
    }

    private List<JsonNode> extractAllEntries(JsonNode ecrituresNode) {
        List<JsonNode> allEntries = new ArrayList<>();

        for (JsonNode node : ecrituresNode) {
            if (node.has("entries") && node.get("entries").isArray()) {
                // This is a bank transaction group with multiple entries
                JsonNode entries = node.get("entries");
                for (JsonNode entry : entries) {
                    allEntries.add(entry);
                }
            } else {
                // This is a regular single entry
                allEntries.add(node);
            }
        }

        log.info("🔍 Extracted {} total entries", allEntries.size());
        return allEntries;
    }

    private Map<String, List<JsonNode>> groupEntriesByDate(List<JsonNode> allEntries) {
        Map<String, List<JsonNode>> entriesByDate = new LinkedHashMap<>();

        for (JsonNode entry : allEntries) {
            String dateStr = extractStringSafely(entry, "Date", "");
            String formattedDate = formatDateToStandard(dateStr);

            entriesByDate.computeIfAbsent(formattedDate, k -> new ArrayList<>()).add(entry);
        }

        log.info("📊 Grouped entries into {} date groups", entriesByDate.size());
        for (Map.Entry<String, List<JsonNode>> entry : entriesByDate.entrySet()) {
            log.info("   - Date {}: {} entries", entry.getKey(), entry.getValue().size());
        }

        return entriesByDate;
    }

    private EcrituresDTO2 createEcritureForDate(String date, List<JsonNode> entriesForDate) {
        try {
            if (entriesForDate.isEmpty()) {
                return null;
            }

            // Use the first entry for journal information
            JsonNode firstEntry = entriesForDate.get(0);

            EcrituresDTO2 ecriture = new EcrituresDTO2();
            ecriture.setUniqueEntryNumber(UUID.randomUUID().toString());
            ecriture.setEntryDate(date);
            ecriture.setJournal(buildJournal(firstEntry));

            // Create lines for all entries in this date
            List<LineDTO> lines = new ArrayList<>();
            for (JsonNode entry : entriesForDate) {
                LineDTO line = buildLine(entry);
                if (line != null) {
                    lines.add(line);
                }
            }
            ecriture.setLines(lines);

            log.info("📝 Created ecriture for date {} with {} lines", date, lines.size());

            return ecriture;

        } catch (Exception e) {
            log.error("❌ Error creating ecriture for date {}: {}", date, e.getMessage());
            return null;
        }
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