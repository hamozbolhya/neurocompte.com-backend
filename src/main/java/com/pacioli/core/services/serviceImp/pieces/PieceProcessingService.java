package com.pacioli.core.services.serviceImp.pieces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.*;
import com.pacioli.core.repositories.*;
import com.pacioli.core.services.serviceImp.AccountCreationService;
import com.pacioli.core.services.serviceImp.DuplicateDetectionService;
import lombok.extern.slf4j.Slf4j;
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
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class PieceProcessingService {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_DEVISE = "MAD";

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
            JsonNode originalEcritures = parsedOriginal.get("ecritures");
            return originalEcritures != null ? originalEcritures : parsedOriginal.get("Ecritures");
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
    public void saveEcrituresForPiece(Piece piece, Long dossierId, String pieceData, JsonNode originalAiResponse) {
        log.info("Processing Piece ID: {}, Dossier ID: {}", piece.getId(), dossierId);

        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new IllegalArgumentException("Dossier not found for ID: " + dossierId));
        log.info("Fetched Dossier ID: {}", dossier.getId());

        // Parse original AI response to get exact string values
        JsonNode originalEcritures = parseOriginalAiResponse(originalAiResponse);

        // Fetch existing Accounts and Journals for the Dossier
        Map<String, Account> accountMap = accountRepository.findByDossierId(dossierId).stream()
                .collect(Collectors.toMap(Account::getAccount, Function.identity()));
        List<Journal> journals = journalRepository.findByDossierId(dossierId);

        List<Ecriture> ecritures = deserializeEcritures(pieceData, dossier);

        for (int i = 0; i < ecritures.size(); i++) {
            Ecriture ecriture = ecritures.get(i);
            ecriture.setPiece(piece);

            if (ecriture.getManuallyUpdated() == null) {
                ecriture.setManuallyUpdated(false);
            }

            // Find or create Journal
            Journal journal = journals.stream()
                    .filter(j -> j.getName().equalsIgnoreCase(ecriture.getJournal().getName()))
                    .findFirst()
                    .orElseGet(() -> {
                        log.info("Creating new Journal: {}", ecriture.getJournal().getName());
                        Journal newJournal = new Journal(ecriture.getJournal().getName(), ecriture.getJournal().getType(), dossier.getCabinet(), dossier);
                        Journal savedJournal = journalRepository.save(newJournal);
                        journals.add(savedJournal);
                        return savedJournal;
                    });
            ecriture.setJournal(journal);

            try {
                Ecriture savedEcriture = ecritureRepository.save(ecriture);

                // Process lines
                for (int j = 0; j < ecriture.getLines().size(); j++) {
                    Line line = ecriture.getLines().get(j);
                    line.setEcriture(savedEcriture);

                    if (line.getManuallyUpdated() == null) {
                        line.setManuallyUpdated(false);
                    }

                    // Handle currency conversion info
                    boolean hasConversionInfo = (line.getOriginalCurrency() != null && line.getConvertedCurrency() != null && !line.getOriginalCurrency().equals(line.getConvertedCurrency()));

                    if (hasConversionInfo) {
                        log.info("💱 Using conversion info from DTO - Original: {}, Converted: {}", line.getOriginalCurrency(), line.getConvertedCurrency());

                        // Set exact precision from original AI response if available
                        if (originalEcritures != null && j < originalEcritures.size()) {
                            JsonNode originalEntry = originalEcritures.get(j);

                            if (line.getOriginalDebit() != null) {
                                line.setOriginalDebitExact(String.valueOf(line.getOriginalDebit()));
                            }
                            if (line.getConvertedDebit() != null) {
                                line.setConvertedDebitExact(String.valueOf(line.getConvertedDebit()));
                            }
                            if (line.getDebit() != null) {
                                line.setDebitExact(String.valueOf(line.getDebit()));
                            }

                            if (line.getOriginalCredit() != null) {
                                line.setOriginalCreditExact(String.valueOf(line.getOriginalCredit()));
                            }
                            if (line.getConvertedCredit() != null) {
                                line.setConvertedCreditExact(String.valueOf(line.getConvertedCredit()));
                            }
                            if (line.getCredit() != null) {
                                line.setCreditExact(String.valueOf(line.getCredit()));
                            }

                            // Set exchange rate exact if available
                            if (line.getExchangeRate() != null) {
                                line.setExchangeRateExact(String.valueOf(line.getExchangeRate()));
                            }
                        }
                    } else {
                        log.info("💱 No conversion info in DTO, using original AI response");

                        if (originalEcritures != null && j < originalEcritures.size()) {
                            JsonNode originalEntry = originalEcritures.get(j);

                            // Set amounts using exact string values
                            if (originalEntry.has("OriginalDebitAmt")) {
                                // Currency conversion case
                                line.setOriginalDebitExact(originalEntry.get("OriginalDebitAmt").asText());
                                line.setDebitExact(originalEntry.get("OriginalDebitAmt").asText());
                                line.setConvertedDebitExact(originalEntry.get("DebitAmt").asText());

                                line.setOriginalCreditExact(originalEntry.get("OriginalCreditAmt").asText());
                                line.setCreditExact(originalEntry.get("OriginalCreditAmt").asText());
                                line.setConvertedCreditExact(originalEntry.get("CreditAmt").asText());
                            } else {
                                // No conversion case
                                line.setDebitExact(originalEntry.get("DebitAmt").asText());
                                line.setCreditExact(originalEntry.get("CreditAmt").asText());
                            }

                            // Set USD amounts if available
                            if (originalEntry.has("UsdDebitAmt")) {
                                line.setUsdDebitExact(originalEntry.get("UsdDebitAmt").asText());
                            }
                            if (originalEntry.has("UsdCreditAmt")) {
                                line.setUsdCreditExact(originalEntry.get("UsdCreditAmt").asText());
                            }

                            // Set exchange rate
                            if (originalEntry.has("ExchangeRate")) {
                                line.setExchangeRateExact(originalEntry.get("ExchangeRate").asText());
                            }

                            // Set currencies ONLY if not already set from DTO
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

                            // Set exchange rate date
                            if (originalEntry.has("ExchangeRateDate") && !originalEntry.get("ExchangeRateDate").isNull()) {
                                try {
                                    String dateStr = originalEntry.get("ExchangeRateDate").asText();
                                    if (dateStr != null && !dateStr.isEmpty() && !dateStr.equals("null")) {
                                        line.setExchangeRateDate(dateStr);
                                    } else {
                                        line.setExchangeRateDate(null);
                                    }
                                } catch (Exception e) {
                                    log.trace("Error parsing exchange rate date: {}", e.getMessage());
                                    line.setExchangeRateDate(null);
                                }
                            } else {
                                // If not provided or is null in JSON, try to get from piece
                                if (piece.getExchangeRateDate() != null) {
                                    line.setExchangeRateDate(piece.getExchangeRateDate().toString());
                                    log.info("📅 Set exchange rate date from piece: {}", piece.getExchangeRateDate());
                                } else {
                                    line.setExchangeRateDate(null);
                                }
                            }

                            // If still null, get from piece
                            if (line.getExchangeRateDate() == null && piece.getExchangeRateDate() != null) {
                                line.setExchangeRateDate(piece.getExchangeRateDate().toString());
                                log.info("📅 Set exchange rate date from piece: {}", piece.getExchangeRateDate());
                            }
                        }

                        // If still no currency info, set from piece or default
                        if (line.getOriginalCurrency() == null && line.getConvertedCurrency() == null) {
                            String currency = piece.getAiCurrency() != null ? piece.getAiCurrency() : DEFAULT_CURRENCY;
                            line.setOriginalCurrency(currency);
                            line.setConvertedCurrency(currency);
                            log.info("💱 Set default currency: {}", currency);
                        }
                    }

                    log.info("💱 Final Line currency info - Label: {}, Original: {}, Converted: {}, ExchangeRate: {}, OriginalDebit: {}, ConvertedDebit: {}",
                            line.getLabel(), line.getOriginalCurrency(), line.getConvertedCurrency(), line.getExchangeRate(), line.getOriginalDebit(), line.getConvertedDebit());

                    // Handle account creation
                    String accountNumber = line.getAccount().getAccount();
                    Account account = findOrCreateAccount(accountNumber, dossier, journal, line.getAccount().getLabel(), accountMap);
                    line.setAccount(account);
                }

                // Save Lines
                lineRepository.saveAll(ecriture.getLines());

            } catch (Exception e) {
                log.error("Error saving Ecriture: {}", e.getMessage(), e);
                throw e;
            }
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

        String tvaRateStr = firstEntry.has("TVARate") ? firstEntry.get("TVARate").asText() : "0";
        try {
            fd.setTaxRate(Double.parseDouble(tvaRateStr.replace("%", "").trim()));
        } catch (NumberFormatException e) {
            fd.setTaxRate(0.0);
        }

        return fd;
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

            log.info("Raw Ecritures JSON Node: {}", ecrituresNode);

            Map<String, Account> accountMap = accountRepository.findByDossierId(dossier.getId()).stream()
                    .collect(Collectors.toMap(Account::getAccount, Function.identity()));
            log.info("Initial Account Map: {}", accountMap);

            List<Ecriture> ecritures = new ArrayList<>();
            for (JsonNode ecritureNode : ecrituresNode) {
                Ecriture ecriture = objectMapper.treeToValue(ecritureNode, Ecriture.class);
                if (ecriture.getManuallyUpdated() == null) {
                    ecriture.setManuallyUpdated(false);
                }

                if (ecriture.getLines() != null) {
                    for (Line line : ecriture.getLines()) {
                        Account account = line.getAccount();
                        if (line.getManuallyUpdated() == null) {
                            line.setManuallyUpdated(false);
                        }
                        if (account != null) {
                            Account existingAccount = accountMap.get(account.getAccount());
                            if (existingAccount == null) {
                                existingAccount = accountRepository.findByAccountAndDossierId(account.getAccount(), dossier.getId());
                                if (existingAccount != null) {
                                    accountMap.put(existingAccount.getAccount(), existingAccount);
                                }
                            }

                            if (existingAccount != null) {
                                log.info("Matched existing Account: {}", existingAccount);
                                line.setAccount(existingAccount);
                            } else {
                                account.setDossier(dossier);
                                log.info("Prepared new Account with Dossier: {}", account);
                                accountMap.put(account.getAccount(), account);
                            }
                        } else {
                            log.warn("Line does not have an account: {}", line.getLabel());
                        }
                    }
                } else {
                    log.warn("Ecriture has no lines: {}", ecriture.getUniqueEntryNumber());
                }

                log.info("Deserialized Ecriture: {}", ecriture);
                ecritures.add(ecriture);
            }
            return ecritures;
        } catch (IOException e) {
            log.error("Failed to parse 'ecritures' JSON: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Invalid JSON format for 'ecritures': " + e.getMessage(), e);
        }
    }

    /**
     * Find or create account
     */
    private Account findOrCreateAccount(String accountNumber, Dossier dossier, Journal journal, String accountLabel, Map<String, Account> accountMap) {
        if (accountMap.containsKey(accountNumber)) {
            log.debug("✅ Found account in cache: {}", accountNumber);
            return accountMap.get(accountNumber);
        }

        try {
            Account account = accountCreationService.findOrCreateAccount(accountNumber, dossier, journal, accountLabel);
            accountMap.put(accountNumber, account);
            log.debug("✅ Created/Found account via service: {}", accountNumber);
            return account;
        } catch (Exception e) {
            log.error("❌ Error in findOrCreateAccount for account {}: {}", accountNumber, e.getMessage());
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
        if (piece.getFactureData() != null) {
            factureDataRepository.delete(piece.getFactureData());
            piece.setFactureData(null);
        }

        // Delete ecritures and lines
        if (piece.getEcritures() != null) {
            for (Ecriture ecriture : piece.getEcritures()) {
                lineRepository.deleteAll(ecriture.getLines());
            }
            ecritureRepository.deleteAll(piece.getEcritures());
            piece.getEcritures().clear();
        }

        return pieceRepository.save(piece);
    }

    /**
     * Create piece files as ZIP
     */
    public byte[] createPieceFilesZip(Long pieceId) {
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