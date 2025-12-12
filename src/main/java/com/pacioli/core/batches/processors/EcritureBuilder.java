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
                log.debug("Node {} - has entries: {}, has Date: {}",
                        i,
                        node.has("entries") && node.get("entries").isArray(),
                        node.has("Date"));
            }

            // Check if this is a bank statement
            boolean isBankStatement = false;
            for (JsonNode node : ecrituresNode) {
                if (node.has("entries") && node.get("entries").isArray()) {
                    isBankStatement = true;
                    break;
                }
            }

            if (isBankStatement) {
                log.info("🏦 Processing BANK statement: creating one Ecriture per transaction group");
                ecritures = processBankStatement(ecrituresNode);
            } else {
                log.info("📄 Processing NORMAL invoice: creating ONE Ecriture with each line keeping its original date");
                ecritures = processNormalInvoice(ecrituresNode);
            }

            log.info("✅ Built {} ecritures", ecritures.size());

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

        if (ecrituresNode.size() == 0) {
            return ecritures;
        }

        // For normal invoices: Create ONE Ecriture for ALL entries
        // Use earliest date for the Ecriture itself
        EcrituresDTO2 ecriture = createSingleEcritureForAllEntries(ecrituresNode);
        if (ecriture != null) {
            ecritures.add(ecriture);
        }

        log.info("📄 Created 1 Ecriture with {} lines from normal invoice",
                ecriture != null ? ecriture.getLines().size() : 0);
        return ecritures;
    }

    private EcrituresDTO2 createSingleEcritureForAllEntries(JsonNode ecrituresNode) {
        try {
            if (ecrituresNode.size() == 0) {
                return null;
            }

            EcrituresDTO2 ecriture = new EcrituresDTO2();
            ecriture.setUniqueEntryNumber(UUID.randomUUID().toString());

            // Find the EARLIEST date among all entries for the Ecriture date
            String earliestDate = null;
            JournalDTO journal = null;

            for (JsonNode entry : ecrituresNode) {
                String entryDateStr = extractStringSafely(entry, "Date", "");
                String formattedEntryDate = formatDateToStandard(entryDateStr);

                if (earliestDate == null || formattedEntryDate.compareTo(earliestDate) < 0) {
                    earliestDate = formattedEntryDate;
                    journal = buildJournal(entry); // Use journal from earliest entry
                }
            }

            ecriture.setEntryDate(earliestDate != null ? earliestDate : formatDateToStandard(""));
            ecriture.setJournal(journal != null ? journal : buildJournal(ecrituresNode.get(0)));

            // Create lines for ALL entries, each line will keep its original date
            List<LineDTO> lines = new ArrayList<>();
            for (JsonNode entry : ecrituresNode) {
                LineDTO line = buildLine(entry);
                if (line != null) {
                    lines.add(line);
                }
            }
            ecriture.setLines(lines);

            log.info("📄 Created ONE Ecriture with {} lines using earliest date: {}",
                    lines.size(), earliestDate);
            return ecriture;

        } catch (Exception e) {
            log.error("❌ Error creating single Ecriture for all entries: {}", e.getMessage(), e);
            return null;
        }
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

            // Set date from first entry in the transaction group
            String dateStr = extractStringSafely(firstEntry, "Date", "");
            if (dateStr.isEmpty() && transactionGroup.has("Date")) {
                dateStr = transactionGroup.get("Date").asText();
            }
            String formattedDate = formatDateToStandard(dateStr);
            ecriture.setEntryDate(formattedDate);

            // Set journal
            ecriture.setJournal(buildJournal(firstEntry));

            // Create lines for all entries in this transaction group
            List<LineDTO> lines = new ArrayList<>();
            for (JsonNode entry : entries) {
                LineDTO line = buildLine(entry);
                if (line != null) {
                    lines.add(line);
                }
            }
            ecriture.setLines(lines);

            log.debug("🏦 Created bank Ecriture with {} lines for date: {}",
                    lines.size(), formattedDate);

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

            // ⭐⭐⭐ IMPORTANT: Set the line's ORIGINAL DATE in exchangeRateDate ⭐⭐⭐
            String lineDateStr = extractStringSafely(entry, "Date", "");
            if (lineDateStr != null && !lineDateStr.isEmpty()) {
                try {
                    LocalDate lineDate = parseDate(lineDateStr);
                    line.setExchangeRateDate(lineDate);
                    log.trace("📅 Line date saved to exchangeRateDate: {}", lineDate);
                } catch (Exception e) {
                    log.trace("Error parsing line date: {}", e.getMessage());
                }
            }

            // Set account
            AccountDTO account = new AccountDTO();
            account.setAccount(extractStringSafely(entry, "CompteNum", "0000"));
            account.setLabel(extractStringSafely(entry, "CompteLib", "Unknown Account"));
            line.setAccount(account);

            log.trace("✅ Built line: {} - Debit: {}, Credit: {}, Date: {}",
                    line.getLabel(), line.getDebit(), line.getCredit(),
                    line.getExchangeRateDate());

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