package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacioli.core.DTO.*;
import com.pacioli.core.batches.DTO.BaseDTOBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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

        log.info("🔍 Starting buildEcritures with {} nodes", ecrituresNode.size());

        try {
            // DEBUG: Log the structure
            log.debug("First 3 nodes structure:");
            for (int i = 0; i < Math.min(3, ecrituresNode.size()); i++) {
                JsonNode node = ecrituresNode.get(i);
                log.debug("Node {} - has entries: {}, has isBankTransaction: {}, has Date: {}",
                        i,
                        node.has("entries") && node.get("entries").isArray(),
                        node.has("isBankTransaction"),
                        node.has("Date"));
            }

            // Check if this is a bank statement
            boolean isBankStatement = false;
            for (JsonNode node : ecrituresNode) {
                if ((node.has("isBankTransaction") && node.get("isBankTransaction").asBoolean()) ||
                        (node.has("entries") && node.get("entries").isArray() &&
                                node.get("entries").size() > 0)) {
                    isBankStatement = true;
                    break;
                }
            }

            if (isBankStatement) {
                log.info("🏦 Processing BANK statement: creating one Ecriture per transaction group");
                ecritures = processBankStatement(ecrituresNode);
            } else {
                log.info("📄 Processing NORMAL invoice: grouping by date");
                ecritures = processNormalInvoice(ecrituresNode);
            }

            log.info("✅ Built {} ecritures", ecritures.size());

            // Log details
            for (int i = 0; i < ecritures.size(); i++) {
                EcrituresDTO2 ecriture = ecritures.get(i);
//                log.debug("Ecriture {}: Date={}, Journal={}, Lines={}",
//                        i, ecriture.getEntryDate(),
//                        ecriture.getJournal() != null ? ecriture.getJournal().getName() : "null",
//                        ecriture.getLines() != null ? ecriture.getLines().size() : 0);
            }

        } catch (Exception e) {
            log.error("❌ Error building ecritures: {}", e.getMessage(), e);
        }

        return ecritures;
    }

    private List<EcrituresDTO2> processBankStatement(JsonNode ecrituresNode) {
        List<EcrituresDTO2> ecritures = new ArrayList<>();

        for (JsonNode node : ecrituresNode) {
            // Check if this is a transaction group
            if (node.has("entries") && node.get("entries").isArray()) {
                EcrituresDTO2 ecriture = createEcritureFromTransactionGroup(node);
                if (ecriture != null) {
                    ecritures.add(ecriture);
                }
            } else if (node.has("isBankTransaction") && node.get("isBankTransaction").asBoolean()) {
                // Already marked as bank transaction
                EcrituresDTO2 ecriture = createEcritureFromTransactionGroup(node);
                if (ecriture != null) {
                    ecritures.add(ecriture);
                }
            } else {
                // Single entry (shouldn't happen in bank statements, but handle it)
                EcrituresDTO2 ecriture = createEcritureFromSingleEntry(node);
                if (ecriture != null) {
                    ecritures.add(ecriture);
                }
            }
        }

        log.info("🏦 Created {} Ecritures from bank statement", ecritures.size());
        return ecritures;
    }

    private List<EcrituresDTO2> processNormalInvoice(JsonNode ecrituresNode) {
        List<EcrituresDTO2> ecritures = new ArrayList<>();
        List<JsonNode> allEntries = new ArrayList<>();

        // Flatten any nested structures
        for (JsonNode node : ecrituresNode) {
            allEntries.add(node);
        }

        // Group by date
        Map<String, List<JsonNode>> entriesByDate = groupEntriesByDate(allEntries);

        // Create one Ecriture per date
        for (Map.Entry<String, List<JsonNode>> dateGroup : entriesByDate.entrySet()) {
            EcrituresDTO2 ecriture = createEcritureForDate(dateGroup.getKey(), dateGroup.getValue());
            if (ecriture != null) {
                ecritures.add(ecriture);
            }
        }

        log.info("📄 Created {} Ecritures from normal invoice", ecritures.size());
        return ecritures;
    }

    private EcrituresDTO2 createEcritureFromTransactionGroup(JsonNode transactionGroup) {
        try {
            if (!transactionGroup.has("entries") || !transactionGroup.get("entries").isArray() ||
                    transactionGroup.get("entries").size() == 0) {
                log.warn("⚠️ Invalid transaction group");
                return null;
            }

            JsonNode entries = transactionGroup.get("entries");
            JsonNode firstEntry = entries.get(0);

            EcrituresDTO2 ecriture = new EcrituresDTO2();
            ecriture.setUniqueEntryNumber(UUID.randomUUID().toString());

            // Set date
            String dateStr = extractStringSafely(firstEntry, "Date", "");
            if (dateStr.isEmpty() && transactionGroup.has("Date")) {
                dateStr = transactionGroup.get("Date").asText();
            }
            String formattedDate = formatDateToStandard(dateStr);
            ecriture.setEntryDate(formattedDate);

            // Set journal
            ecriture.setJournal(buildJournal(firstEntry));

            // Create lines
            List<LineDTO> lines = new ArrayList<>();
            for (JsonNode entry : entries) {
                LineDTO line = buildLine(entry);
                if (line != null) {
                    lines.add(line);
                }
            }
            ecriture.setLines(lines);

//            log.debug("🏦 Created bank Ecriture with {} lines for date: {}",
//                    lines.size(), formattedDate);

            return ecriture;

        } catch (Exception e) {
            log.error("❌ Error creating Ecriture from transaction group: {}", e.getMessage(), e);
            return null;
        }
    }

    private EcrituresDTO2 createEcritureFromSingleEntry(JsonNode entry) {
        try {
            EcrituresDTO2 ecriture = new EcrituresDTO2();
            ecriture.setUniqueEntryNumber(UUID.randomUUID().toString());

            // Set date
            String dateStr = extractStringSafely(entry, "Date", "");
            String formattedDate = formatDateToStandard(dateStr);
            ecriture.setEntryDate(formattedDate);

            // Set journal
            ecriture.setJournal(buildJournal(entry));

            // Create single line
            List<LineDTO> lines = new ArrayList<>();
            LineDTO line = buildLine(entry);
            if (line != null) {
                lines.add(line);
            }
            ecriture.setLines(lines);

            log.debug("📄 Created single-entry Ecriture for date: {}", formattedDate);

            return ecriture;

        } catch (Exception e) {
            log.error("❌ Error creating Ecriture from single entry: {}", e.getMessage(), e);
            return null;
        }
    }

    private EcrituresDTO2 createEcritureForDate(String date, List<JsonNode> entriesForDate) {
        try {
            if (entriesForDate.isEmpty()) {
                return null;
            }

            JsonNode firstEntry = entriesForDate.get(0);

            EcrituresDTO2 ecriture = new EcrituresDTO2();
            ecriture.setUniqueEntryNumber(UUID.randomUUID().toString());
            ecriture.setEntryDate(date);
            ecriture.setJournal(buildJournal(firstEntry));

            // Create lines for all entries
            List<LineDTO> lines = new ArrayList<>();
            for (JsonNode entry : entriesForDate) {
                LineDTO line = buildLine(entry);
                if (line != null) {
                    lines.add(line);
                }
            }
            ecriture.setLines(lines);

            log.debug("📝 Created grouped Ecriture for date {} with {} lines", date, lines.size());
            return ecriture;

        } catch (Exception e) {
            log.error("❌ Error creating ecriture for date {}: {}", date, e.getMessage());
            return null;
        }
    }

    private Map<String, List<JsonNode>> groupEntriesByDate(List<JsonNode> allEntries) {
        Map<String, List<JsonNode>> entriesByDate = new LinkedHashMap<>();

        for (JsonNode entry : allEntries) {
            String dateStr = extractStringSafely(entry, "Date", "");
            String formattedDate = formatDateToStandard(dateStr);

            entriesByDate.computeIfAbsent(formattedDate, k -> new ArrayList<>()).add(entry);
        }

        log.debug("📊 Grouped {} entries into {} date groups",
                allEntries.size(), entriesByDate.size());
        return entriesByDate;
    }

    private LineDTO buildLine(JsonNode entry) {
        LineDTO line = new LineDTO();

        try {
            line.setLabel(extractStringSafely(entry, "EcritLib", "Unknown Entry"));

            // Handle amounts
            line.setOriginalDebit(parseDoubleSafely(entry, "OriginalDebitAmt"));
            line.setOriginalCredit(parseDoubleSafely(entry, "OriginalCreditAmt"));
            line.setDebit(parseDoubleSafely(entry, "DebitAmt"));
            line.setCredit(parseDoubleSafely(entry, "CreditAmt"));
            line.setConvertedDebit(parseDoubleSafely(entry, "DebitAmt"));
            line.setConvertedCredit(parseDoubleSafely(entry, "CreditAmt"));
            line.setUsdDebit(parseDoubleSafely(entry, "UsdDebitAmt"));
            line.setUsdCredit(parseDoubleSafely(entry, "UsdCreditAmt"));
            line.setExchangeRate(parseDoubleSafely(entry, "ExchangeRate"));

            // Set currency information
            line.setOriginalCurrency(extractStringSafely(entry, "OriginalDevise", null));
            line.setConvertedCurrency(extractStringSafely(entry, "Devise", "USD"));

            // Set exchange rate date
            if (entry.has("ExchangeRateDate")) {
                String dateStr = entry.get("ExchangeRateDate").asText();
                if (dateStr != null && !dateStr.isEmpty() && !dateStr.equals("null")) {
                    try {
                        // Parse the date
                        LocalDate exchangeDate = parseDate(dateStr);
                        line.setExchangeRateDate(LocalDate.parse(exchangeDate.toString()));
                    } catch (Exception e) {
                        log.trace("Error parsing exchange rate date: {}", e.getMessage());
                    }
                }
            }

            // Set account
            AccountDTO account = new AccountDTO();
            account.setAccount(extractStringSafely(entry, "CompteNum", "0000"));
            account.setLabel(extractStringSafely(entry, "CompteLib", "Unknown Account"));
            line.setAccount(account);

            log.trace("✅ Built line: {} - Debit: {}, Credit: {}",
                    line.getLabel(), line.getDebit(), line.getCredit());

            return line;

        } catch (Exception e) {
            log.error("❌ Error building line: {}", e.getMessage(), e);
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