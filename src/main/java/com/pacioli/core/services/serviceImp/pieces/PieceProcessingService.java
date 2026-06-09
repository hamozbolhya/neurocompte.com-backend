package com.pacioli.core.services.serviceImp.pieces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.*;
import com.pacioli.core.repositories.*;
import com.pacioli.core.services.serviceImp.AccountCreationService;
import com.pacioli.core.services.serviceImp.DuplicateDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class PieceProcessingService {

    private static final String DEFAULT_DEVISE = "MAD";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private final PieceRepository pieceRepository;
    private final FactureDataRepository factureDataRepository;
    private final EcritureRepository ecritureRepository;
    private final LineRepository lineRepository;
    private final DossierRepository dossierRepository;
    private final AccountRepository accountRepository;
    private final JournalRepository journalRepository;
    private final AccountCreationService accountCreationService;
    private final ObjectMapper objectMapper;

    public PieceProcessingService(PieceRepository pieceRepository,
                                  FactureDataRepository factureDataRepository,
                                  EcritureRepository ecritureRepository,
                                  LineRepository lineRepository,
                                  DossierRepository dossierRepository,
                                  AccountRepository accountRepository,
                                  JournalRepository journalRepository,
                                  AccountCreationService accountCreationService,
                                  DuplicateDetectionService duplicateDetectionService,
                                  ObjectMapper objectMapper) {
        this.pieceRepository = pieceRepository;
        this.factureDataRepository = factureDataRepository;
        this.ecritureRepository = ecritureRepository;
        this.lineRepository = lineRepository;
        this.dossierRepository = dossierRepository;
        this.accountRepository = accountRepository;
        this.journalRepository = journalRepository;
        this.accountCreationService = accountCreationService;
        this.objectMapper = objectMapper;
    }


    /**
     * Parse original AI response for amount extraction
     */
    private JsonNode parseOriginalAiResponse(JsonNode originalAiResponse) {
        if (originalAiResponse == null) {
            log.debug("Original AI response is null");
            return null;
        }

        try {
            String responseText = originalAiResponse.asText();
            JsonNode parsedOriginal = objectMapper.readTree(responseText);

            // Try different possible locations for ecritures
            JsonNode originalEcritures = parsedOriginal.get("ecritures");
            if (originalEcritures == null) {
                originalEcritures = parsedOriginal.get("Ecritures");
            }
            if (originalEcritures == null && parsedOriginal.isArray()) {
                originalEcritures = parsedOriginal;
            }

            if (originalEcritures != null) {
                log.debug("Found {} original ecritures",
                        originalEcritures.isArray() ? originalEcritures.size() : 1);
            }

            return originalEcritures;

        } catch (Exception e) {
            log.error("Error parsing original AI response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Save FactureData for piece
     */
    public void saveFactureDataForPiece(Piece piece, String pieceData, JsonNode originalAiResponse) {
        FactureData factureData = deserializeFactureData(pieceData);

        if (factureData == null) {
            log.warn("⚠️ No FactureData found in the pieceData for piece {}", piece.getId());
            return;
        }

        factureData.setPiece(piece);

        // Parse original AI response to get exact string values
        try {
            String responseText = originalAiResponse.asText();
            JsonNode parsedOriginal = objectMapper.readTree(responseText);
            JsonNode originalEcritures = parseOriginalAiResponse(originalAiResponse);
            if (originalEcritures == null) {
                originalEcritures = parsedOriginal.get("Ecritures");
            }

            if (originalEcritures != null && originalEcritures.isArray() && originalEcritures.size() > 0) {
                JsonNode firstEntry = originalEcritures.get(0);

                // Set amounts using exact precision from original AI response
                if (firstEntry.has("TotalTTC")) {
                    factureData.setTotalTTCExact(firstEntry.get("TotalTTC").asText());
                }
                if (firstEntry.has("TotalHT")) {
                    factureData.setTotalHTExact(firstEntry.get("TotalHT").asText());
                }
                if (firstEntry.has("TotalTVA")) {
                    factureData.setTotalTVAExact(firstEntry.get("TotalTVA").asText());
                }

                // Validate accounting balance using BigDecimal
                BigDecimal totalDebit = BigDecimal.ZERO;
                BigDecimal totalCredit = BigDecimal.ZERO;

                for (JsonNode entry : originalEcritures) {
                    String debitStr = entry.get("DebitAmt").asText("0");
                    String creditStr = entry.get("CreditAmt").asText("0");

                    BigDecimal debit = new BigDecimal(debitStr.replace(",", "."));
                    BigDecimal credit = new BigDecimal(creditStr.replace(",", "."));

                    totalDebit = totalDebit.add(debit);
                    totalCredit = totalCredit.add(credit);
                }

                if (totalDebit.compareTo(totalCredit) != 0) {
                    log.warn("❗ Imbalanced accounting: Debit={} ≠ Credit={}", totalDebit, totalCredit);
                } else {
                    log.info("✅ Accounting is balanced: {} = {}", totalDebit, totalCredit);
                }
            }
        } catch (Exception e) {
            log.error("❌ Error processing original AI response for piece {}: {}", piece.getId(), e.getMessage(), e);
        }

        // Set ICE if missing
        if (factureData.getIce() == null || factureData.getIce().isEmpty()) {
            try {
                Dossier dossier = piece.getDossier();
                if (dossier != null && dossier.getICE() != null) {
                    factureData.setIce(dossier.getICE());
                    log.info("ℹ️ Set ICE from dossier: {}", dossier.getICE());
                }
            } catch (Exception e) {
                log.error("❌ Failed to set ICE from dossier for piece {}: {}", piece.getId(), e.getMessage(), e);
            }
        }

        // Set exchange info if missing
        if (piece.getExchangeRate() != null && factureData.getExchangeRate() == null) {
            factureData.setExchangeRate(piece.getExchangeRate());
            factureData.setOriginalCurrency(piece.getAiCurrency());
            factureData.setConvertedCurrency(piece.getConvertedCurrency());
            factureData.setExchangeRateDate(piece.getExchangeRateDate());

            double rate = piece.getExchangeRate();

            if (factureData.getTotalTTC() != null && factureData.getConvertedTotalTTC() == null)
                factureData.setConvertedTotalTTC(factureData.getTotalTTC() * rate);
            if (factureData.getTotalHT() != null && factureData.getConvertedTotalHT() == null)
                factureData.setConvertedTotalHT(factureData.getTotalHT() * rate);
            if (factureData.getTotalTVA() != null && factureData.getConvertedTotalTVA() == null)
                factureData.setConvertedTotalTVA(factureData.getTotalTVA() * rate);
        }

        // Save or update FactureData
        Optional<FactureData> existingOpt = factureDataRepository.findByPiece(piece);

        if (existingOpt.isPresent()) {
            FactureData existing = existingOpt.get();
            // Update all fields including the new exact fields
            existing.setInvoiceNumber(factureData.getInvoiceNumber());
            existing.setInvoiceDate(factureData.getInvoiceDate());
            existing.setIce(factureData.getIce());
            existing.setExchangeRate(factureData.getExchangeRate());
            existing.setOriginalCurrency(factureData.getOriginalCurrency());
            existing.setConvertedCurrency(factureData.getConvertedCurrency());
            existing.setExchangeRateDate(factureData.getExchangeRateDate());
            existing.setConvertedTotalHT(factureData.getConvertedTotalHT());
            existing.setConvertedTotalTTC(factureData.getConvertedTotalTTC());
            existing.setConvertedTotalTVA(factureData.getConvertedTotalTVA());
            existing.setTotalHT(factureData.getTotalHT());
            existing.setTotalTTC(factureData.getTotalTTC());
            existing.setTotalTVA(factureData.getTotalTVA());
            existing.setDevise(factureData.getDevise());
            existing.setTaxRate(factureData.getTaxRate());
            existing.setUsdTotalHT(factureData.getUsdTotalHT());
            existing.setUsdTotalTTC(factureData.getUsdTotalTTC());
            existing.setUsdTotalTVA(factureData.getUsdTotalTVA());

            // SET THE NEW EXACT FIELDS
            existing.setTotalTTCExact(factureData.getTotalTTCExact());
            existing.setTotalHTExact(factureData.getTotalHTExact());
            existing.setTotalTVAExact(factureData.getTotalTVAExact());

            factureDataRepository.save(existing);
            log.info("📝 Updated existing FactureData for piece {}", piece.getId());
        } else {
            factureData.setPiece(piece);
            factureDataRepository.save(factureData);
            log.info("✅ Created new FactureData for piece {}", piece.getId());
        }
    }

    /**
     * Save Ecritures for piece
     */
    @Transactional
    public void saveEcrituresForPiece(Piece piece, @NonNull Long dossierId, String pieceData, JsonNode originalAiResponse) {
        log.info("🔥🔥🔥 SAVE ECritures START =========================================");

        try {
            Dossier dossier = dossierRepository.findById(dossierId)
                    .orElseThrow(() -> new IllegalArgumentException("Dossier not found for ID: " + dossierId));
            log.info("🔥 Fetched Dossier: {} (ID: {})", dossier.getName(), dossier.getId());

            // Parse original AI response to get exact string values
            JsonNode originalEcritures = parseOriginalAiResponse(originalAiResponse);

            // Fetch existing Accounts and Journals for the Dossier
            Map<String, Account> accountMap = accountRepository.findByDossierId(dossierId).stream()
                    .collect(Collectors.toMap(Account::getAccount, Function.identity()));

            List<Journal> journals = journalRepository.findByDossierId(dossierId);

            // ✅ THIS SHOULD RETURN ALL ECritures
            List<Ecriture> ecritures = deserializeEcritures(pieceData, dossier);
            log.info("🔥🔥 Deserialized {} Ecritures for piece {}", ecritures.size(), piece.getId());

            if (ecritures.isEmpty()) {
                log.warn("⚠️ No Ecritures to save!");
                return;
            }

            int totalLines = 0;
            int totalSavedEcritures = 0;

            // pass 1: prepare all ecritures and lines (including accounts)
            for (int i = 0; i < ecritures.size(); i++) {
                Ecriture ecriture = ecritures.get(i);
                ecriture.setPiece(piece);
                if (ecriture.getManuallyUpdated() == null) {
                    ecriture.setManuallyUpdated(false);
                }

                Journal journal = findOrCreateJournal(ecriture, dossier, journals);
                ecriture.setJournal(journal);

                if (ecriture.getLines() != null && !ecriture.getLines().isEmpty()) {
                    for (int j = 0; j < ecriture.getLines().size(); j++) {
                        Line line = ecriture.getLines().get(j);
                        line.setEcriture(ecriture);
                        if (line.getManuallyUpdated() == null) {
                            line.setManuallyUpdated(false);
                        }

                        processLineConversion(line, j, originalEcritures, piece);

                        String accountNumber = line.getAccount() != null ?
                                line.getAccount().getAccount() : null;

                        if (accountNumber != null) {
                            String accountLabel = line.getAccount().getLabel();
                            Account account = findOrCreateAccount(accountNumber, dossier, journal,
                                    accountLabel, accountMap);
                            line.setAccount(account);
                        }
                    }
                }
            }

            // pass 2: save everything
            for (int i = 0; i < ecritures.size(); i++) {
                Ecriture ecriture = ecritures.get(i);
                try {
                    // ✅ SAVE ECriture (cascades to lines)
                    Ecriture savedEcriture = ecritureRepository.save(Objects.requireNonNull(ecriture));
                    totalSavedEcritures++;
                    if (savedEcriture.getLines() != null) {
                        totalLines += savedEcriture.getLines().size();
                    }
                    log.info("✅ Saved Ecriture {}: ID={}, lines={}",
                            i + 1, savedEcriture.getId(),
                            savedEcriture.getLines() != null ? savedEcriture.getLines().size() : 0);
                } catch (Exception e) {
                    log.error("❌ Error saving Ecriture {}: {}", i + 1, e.getMessage(), e);
                    throw e;
                }
            }

            log.info("🔥🔥🔥 SAVE ECritures COMPLETE =======================================");
            log.info("✅ Successfully saved {} Ecritures with total {} lines for piece {}",
                    totalSavedEcritures, totalLines, piece.getId());

        } catch (Exception e) {
            log.error("❌❌❌ FATAL ERROR in saveEcrituresForPiece: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save ecritures: " + e.getMessage(), e);
        }
    }

    private Journal findOrCreateJournal(Ecriture ecriture, Dossier dossier, List<Journal> journals) {
        String journalName = ecriture.getJournal() != null ?
                ecriture.getJournal().getName() : "BQ"; // Default to "BQ" for bank
        String journalType = ecriture.getJournal() != null ?
                ecriture.getJournal().getType() : "Banque"; // Default to "Banque" for bank

        log.debug("🔥 Looking for journal: name={}, type={}", journalName, journalType);

        Journal journal = journals.stream()
                .filter(j -> j.getName().equalsIgnoreCase(journalName))
                .findFirst()
                .orElseGet(() -> {
                    log.info("🔥 Creating new Journal: {} ({})", journalName, journalType);
                    Journal newJournal = new Journal(journalName, journalType,
                            dossier.getCabinet(), dossier);
                    Journal savedJournal = journalRepository.save(newJournal);
                    journals.add(savedJournal);
                    return savedJournal;
                });

        log.debug("🔥 Using journal: {} (ID: {})", journal.getName(), journal.getId());
        return journal;
    }

    private void processLineConversion(Line line, int lineIndex, JsonNode originalEcritures, Piece piece) {
        if (originalEcritures != null && originalEcritures.isArray() &&
                lineIndex < originalEcritures.size()) {

            JsonNode originalEntry = originalEcritures.get(lineIndex);

            // Set exact string values from original AI response
            if (originalEntry.has("OriginalDebitAmt")) {
                line.setOriginalDebitExact(originalEntry.get("OriginalDebitAmt").asText());
                line.setDebitExact(originalEntry.get("OriginalDebitAmt").asText());
                line.setConvertedDebitExact(originalEntry.get("DebitAmt").asText());

                line.setOriginalCreditExact(originalEntry.get("OriginalCreditAmt").asText());
                line.setCreditExact(originalEntry.get("OriginalCreditAmt").asText());
                line.setConvertedCreditExact(originalEntry.get("CreditAmt").asText());
            } else {
                line.setDebitExact(originalEntry.get("DebitAmt").asText());
                line.setCreditExact(originalEntry.get("CreditAmt").asText());
            }

            // Set currencies
            if (line.getOriginalCurrency() == null) {
                if (originalEntry.has("OriginalDevise")) {
                    line.setOriginalCurrency(originalEntry.get("OriginalDevise").asText());
                }
            }

            if (line.getConvertedCurrency() == null) {
                if (originalEntry.has("Devise")) {
                    line.setConvertedCurrency(originalEntry.get("Devise").asText());
                }
            }

            // Set exchange rate
            if (originalEntry.has("ExchangeRate")) {
                line.setExchangeRateExact(originalEntry.get("ExchangeRate").asText());
            }

            // Set exchange rate date
            if (originalEntry.has("ExchangeRateDate") &&
                    !originalEntry.get("ExchangeRateDate").isNull()) {
                String dateStr = originalEntry.get("ExchangeRateDate").asText();
                if (dateStr != null && !dateStr.isEmpty() && !dateStr.equals("null")) {
                    line.setExchangeRateDate(dateStr);
                }
            }
        }

        // Fallback to piece data
        if (line.getExchangeRateDate() == null && piece.getExchangeRateDate() != null) {
            line.setExchangeRateDate(piece.getExchangeRateDate().toString());
        }
    }

    /**
     * Deserialize FactureData from JSON
     */
    private FactureData deserializeFactureData(String pieceData) {
        try {
            JsonNode rootNode = objectMapper.readTree(pieceData);

            // Case 1: "factureData" exists in JSON
            JsonNode factureDataNode = rootNode.get("factureData");
            if (factureDataNode != null && !factureDataNode.isNull()) {
                return objectMapper.treeToValue(factureDataNode, FactureData.class);
            }

            // Case 2: Extract from "Ecritures"
            JsonNode ecrituresNode = rootNode.get("ecritures");
            if (ecrituresNode == null) ecrituresNode = rootNode.get("Ecritures");

            if (ecrituresNode != null && ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                return createFactureDataFromEcriture(ecrituresNode.get(0));
            }

            return null;

        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse factureData or ecritures: " + e.getMessage(), e);
        }
    }

    /**
     * Create FactureData from Ecriture node
     */
    private FactureData createFactureDataFromEcriture(JsonNode firstEntry) {
        FactureData fd = new FactureData();
        fd.setInvoiceNumber(firstEntry.has("FactureNum") ? firstEntry.get("FactureNum").asText() : null);
        fd.setDevise(firstEntry.has("Devise") ? firstEntry.get("Devise").asText() : DEFAULT_DEVISE);

        fd.setTaxRate(firstEntry.has("TVARate") ? parseTaxRate(firstEntry.get("TVARate").asText()) : 0.0);

        return fd;
    }

    private Double parseTaxRate(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }

        String normalized = rawValue.trim()
                .replace(',', '.')
                .replace("%", "");
        Matcher matcher = NUMBER_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }

        return normalizeTaxRate(Double.parseDouble(matcher.group()));
    }

    private Double normalizeTaxRate(Double value) {
        if (value == null) {
            return null;
        }

        double normalized = value;
        if (normalized > 0 && normalized <= 1) {
            normalized *= 100;
        }

        while (normalized > 100 && normalized / 100 <= 100) {
            normalized /= 100;
        }

        return normalized;
    }

    /**
     * Deserialize Ecritures from JSON
     */
    private List<Ecriture> deserializeEcritures(String pieceData, Dossier dossier) {
        try {
            JsonNode rootNode = objectMapper.readTree(pieceData);
            JsonNode ecrituresNode = rootNode.get("ecritures");

            if (ecrituresNode == null || ecrituresNode.isNull()) {
                log.warn("'ecritures' field is missing or null in the JSON");
                return Collections.emptyList();
            }

            log.info("🔥 DESERIALIZE: Found {} ecritures in JSON (isArray: {})",
                    ecrituresNode.size(), ecrituresNode.isArray());

            if (!ecrituresNode.isArray()) {
                log.error("❌ 'ecritures' is not an array!");
                return Collections.emptyList();
            }

            // Check structure of first ecriture
            if (ecrituresNode.size() > 0) {
                JsonNode firstEcriture = ecrituresNode.get(0);
                log.info("🔥 First ecriture keys: {}", firstEcriture.fieldNames());
                if (firstEcriture.has("lines")) {
                    JsonNode lines = firstEcriture.get("lines");
                    log.info("🔥 First ecriture has {} lines",
                            lines.isArray() ? lines.size() : "not an array");
                }
            }

            Map<String, Account> accountMap = accountRepository.findByDossierId(dossier.getId()).stream()
                    .collect(Collectors.toMap(Account::getAccount, Function.identity()));
            log.info("Initial Account Map size: {}", accountMap.size());

            List<Ecriture> ecritures = new ArrayList<>();
            for (int i = 0; i < ecrituresNode.size(); i++) {
                JsonNode ecritureNode = ecrituresNode.get(i);

                log.info("🔥 Processing ecriture {} of {}", i + 1, ecrituresNode.size());

                try {
                    Ecriture ecriture = objectMapper.treeToValue(ecritureNode, Ecriture.class);

                    if (ecriture.getManuallyUpdated() == null) {
                        ecriture.setManuallyUpdated(false);
                    }

                    log.info("🔥 Ecriture {}: uniqueNumber={}, date={}",
                            i + 1,
                            ecriture.getUniqueEntryNumber(),
                            ecriture.getEntryDate());

                    if (ecriture.getLines() != null) {
                        log.info("🔥 Ecriture {} has {} lines", i + 1, ecriture.getLines().size());

                        for (int j = 0; j < ecriture.getLines().size(); j++) {
                            Line line = ecriture.getLines().get(j);
                            line.setEcriture(ecriture); // CRITICAL: Link line to ecriture

                            if (line.getManuallyUpdated() == null) {
                                line.setManuallyUpdated(false);
                            }

//                            log.debug("🔥 Line {}: label={}, debit={}, credit={}",
//                                    j + 1, line.getLabel(), line.getDebit(), line.getCredit());

                            Account account = line.getAccount();
                            if (account != null) {
                                Account existingAccount = accountMap.get(account.getAccount());
                                if (existingAccount == null) {
                                    existingAccount = accountRepository.findByAccountAndDossierId(
                                            account.getAccount(), dossier.getId());
                                    if (existingAccount != null) {
                                        accountMap.put(existingAccount.getAccount(), existingAccount);
                                    }
                                }

                                if (existingAccount != null) {
                                    line.setAccount(existingAccount);
                                } else {
                                    account.setDossier(dossier);
                                    // JSON often carries a phantom journal id that does not exist in DB; journal is set in saveEcritures pass 1.
                                    account.setJournal(null);
                                    log.info("🔥 Prepared new Account: {}", account.getAccount());
                                    accountMap.put(account.getAccount(), account);
                                }
                            } else {
                                log.warn("🔥 Line {} has no account!", j + 1);
                            }
                        }
                    } else {
                        log.warn("🔥 Ecriture {} has NO lines!", i + 1);
                    }

                    ecritures.add(ecriture);
                    log.info("✅ Added ecriture {} to list", i + 1);

                } catch (Exception e) {
                    log.error("❌ Failed to deserialize ecriture {}: {}", i + 1, e.getMessage(), e);
                    throw e;
                }
            }

            log.info("✅ Successfully deserialized {} Ecritures", ecritures.size());
            return ecritures;

        } catch (IOException e) {
            log.error("❌ Failed to parse 'ecritures' JSON: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Invalid JSON format for 'ecritures': " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Unexpected error in deserializeEcritures: {}", e.getMessage(), e);
            throw e;
        }
    }
    /**
     * Find or create account
     */
    private Account findOrCreateAccount(String accountNumber, Dossier dossier, Journal journal,
                                        String accountLabel, Map<String, Account> accountMap) {
        if (accountMap.containsKey(accountNumber)) {
            Account cached = accountMap.get(accountNumber);
            // Transient accounts from JSON must use the journal resolved for this écriture, not deserialized FKs.
            if (cached.getId() == null) {
                cached.setJournal(journal);
            }
            return cached;
        }

        try {
            Account account = accountCreationService.findOrCreateAccount(
                    accountNumber, dossier, journal, accountLabel);
            accountMap.put(accountNumber, account);
            return account;
        } catch (Exception e) {
            log.error("❌ Error finding/creating account {}: {}", accountNumber, e.getMessage());
            throw e;
        }
    }

    /**
     * Force piece as not duplicate
     */
    @Transactional
    public Piece forcePieceAsNotDuplicate(Piece piece) {
        // ✅ FIXED CONDITION: Allow if isDuplicate = true OR status = DUPLICATE
        if (!Boolean.TRUE.equals(piece.getIsDuplicate()) && piece.getStatus() != PieceStatus.DUPLICATE) {
            throw new IllegalStateException("Seules les pièces dupliquées peuvent être forcées à être considérées comme non dupliquées.");
        }

        piece.setIsForced(true);
        piece.setIsDuplicate(false);
        piece.setAmount(null);
        piece.setStatus(PieceStatus.UPLOADED);
        piece.setAiAmount(null);
        piece.setAiCurrency(null);
        piece.setExchangeRate(null);
        piece.setConvertedCurrency(null);
        piece.setExchangeRateDate(null);
        piece.setExchangeRateUpdated(false);

        // Delete factureData if exists
        FactureData factureData = piece.getFactureData();
        if (factureData != null) {
            factureDataRepository.delete(factureData);
            piece.setFactureData(null);
        }

        // Delete ecritures and lines
        List<Ecriture> ecritures = piece.getEcritures();
        if (ecritures != null) {
            for (Ecriture ecriture : ecritures) {
                List<Line> lines = ecriture.getLines();
                if (lines != null) {
                    lineRepository.deleteAll(lines);
                }
            }
            ecritureRepository.deleteAll(ecritures);
            ecritures.clear();
        }

        return pieceRepository.save(piece);
    }

    /**
     * Create piece files as ZIP
     */
    public byte[] createPieceFilesZip(@NonNull Long pieceId) {
        try {
            Optional<Piece> pieceOpt = pieceRepository.findById(pieceId);
            if (!pieceOpt.isPresent()) {
                return null;
            }

            Piece requestedPiece = pieceOpt.get();
            List<Piece> allFiles = new ArrayList<>();

            // Get original piece
            Piece originalPiece;
            if (requestedPiece.getIsDuplicate() && requestedPiece.getOriginalPiece() != null) {
                originalPiece = requestedPiece.getOriginalPiece();
            } else {
                originalPiece = requestedPiece;
            }
            allFiles.add(originalPiece);

            // Get all duplicates
            List<Piece> duplicates = pieceRepository.findByOriginalPieceId(originalPiece.getId());
            allFiles.addAll(duplicates);

            // Create ZIP
            return createZipFromPieces(allFiles);

        } catch (Exception e) {
            log.error("Error creating ZIP for piece {}: {}", pieceId, e.getMessage());
            return null;
        }
    }

    /**
     * Create ZIP from pieces
     */
    private byte[] createZipFromPieces(List<Piece> pieces) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Piece piece : pieces) {
                // Read file from upload directory
                Path filePath = Paths.get("Files/", piece.getFilename()); // Adjust path as needed

                if (Files.exists(filePath)) {
                    byte[] fileContent = Files.readAllBytes(filePath);

                    // Create entry name
                    String entryName = piece.getIsDuplicate() ? "duplicate_" + piece.getOriginalFileName() : "original_" + piece.getOriginalFileName();

                    // Add to ZIP
                    ZipEntry entry = new ZipEntry(entryName);
                    zos.putNextEntry(entry);
                    zos.write(fileContent);
                    zos.closeEntry();
                }
            }
        }
        return baos.toByteArray();
    }
}
