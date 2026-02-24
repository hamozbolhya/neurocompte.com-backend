package com.pacioli.core.services.serviceImp;

import com.pacioli.core.DTO.*;
import com.pacioli.core.models.*;
import com.pacioli.core.repositories.*;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.EcritureService;
import com.pacioli.core.services.UserService;
import com.pacioli.core.utils.EcritureValidationUtil;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EcritureServiceImpl implements EcritureService {

    private final EcritureRepository ecritureRepository;
    private final JournalRepository journalRepository;
    private final LineRepository lineRepository;

    private final PieceRepository pieceRepository;
    private final AccountRepository accountRepository;

    private final AuditService auditService;
    private final UserService userService;

    @Autowired
    public EcritureServiceImpl(EcritureRepository ecritureRepository, LineRepository lineRepository, JournalRepository journalRepository, AccountRepository accountRepository, PieceRepository pieceRepository, AuditService auditService, UserService userService) {
        this.ecritureRepository = ecritureRepository;
        this.lineRepository = lineRepository;
        this.journalRepository = journalRepository;
        this.accountRepository = accountRepository;
        this.pieceRepository = pieceRepository;
        this.auditService = auditService;
        this.userService = userService;
    }

    @Override
    public List<Ecriture> getEcrituresByPieceId(Long pieceId) {
        return ecritureRepository.findByPieceId(pieceId);
    }


    @Override
    public Page<EcritureDTO> getEcrituresByExerciseAndCabinet(Long exerciseId, Long cabinetId, int page, int size) {
        // Get ALL ecritures using the old working query
        List<Ecriture> allEcritures = ecritureRepository.findEcrituresByExerciseAndCabinet(exerciseId, cabinetId);

        // Convert to DTOs
        List<EcritureDTO> allDTOs = allEcritures.stream().map(e -> mapToDTO(e)).collect(Collectors.toList());

        // Apply pagination in memory
        int start = page * size;
        int end = Math.min(start + size, allDTOs.size());

        List<EcritureDTO> paginatedDTOs = allDTOs.subList(start, end);

        // Return as Page
        return new PageImpl<>(paginatedDTOs, PageRequest.of(page, size), allDTOs.size()  // Total size = all ecritures
        );
    }


    private EcritureDTO mapToDTO(Ecriture ecriture) {
        EcritureDTO dto = new EcritureDTO();
        dto.setId(ecriture.getId());
        dto.setUniqueEntryNumber(ecriture.getUniqueEntryNumber());
        dto.setEntryDate(ecriture.getEntryDate());

        if (ecriture.getJournal() != null) {
            JournalDTO journalDTO = new JournalDTO();
            journalDTO.setId(ecriture.getJournal().getId());
            journalDTO.setName(ecriture.getJournal().getName());
            journalDTO.setType(ecriture.getJournal().getType());
            dto.setJournal(journalDTO);
        } else {
            dto.setJournal(null);
        }

        List<LineDTO> lineDTOs = ecriture.getLines().stream().map(line -> {
            LineDTO lineDTO = new LineDTO();
            lineDTO.setId(line.getId());

            AccountDTO accountDTO = new AccountDTO();
            if (line.getAccount() != null) {
                accountDTO.setId(line.getAccount().getId());
                accountDTO.setLabel(line.getAccount().getLabel());
                accountDTO.setAccount(line.getAccount().getAccount());
            }
            lineDTO.setAccount(accountDTO);

            lineDTO.setLabel(line.getLabel());
            lineDTO.setDebit(line.getDebit());
            lineDTO.setCredit(line.getCredit());

            lineDTO.setOriginalDebit(line.getOriginalDebit());
            lineDTO.setOriginalCredit(line.getOriginalCredit());
            lineDTO.setOriginalCurrency(line.getOriginalCurrency());
            lineDTO.setExchangeRate(line.getExchangeRate());
            lineDTO.setConvertedCurrency(line.getConvertedCurrency());
            lineDTO.setExchangeRateDate(line.getExchangeRateDate());
            lineDTO.setUsdDebit(line.getUsdDebit());
            lineDTO.setUsdCredit(line.getUsdCredit());
            lineDTO.setConvertedDebit(line.getConvertedDebit());
            lineDTO.setConvertedCredit(line.getConvertedCredit());

            return lineDTO;
        }).collect(Collectors.toList());

        dto.setLines(lineDTOs);

        if (ecriture.getPiece() != null) {
            PieceDTO pieceDTO = new PieceDTO();
            pieceDTO.setId(ecriture.getPiece().getId());
//            dto.setPiece(pieceDTO);
        }

        return dto;
    }

    @Override
    @Transactional
    public Ecriture updateEcriture(Ecriture ecriture) {
        Ecriture savedEcriture = ecritureRepository.save(ecriture);

        // Audit
        auditService.logSuccess(userService.getCurrentUser(), "UPDATE", "Ecriture", savedEcriture.getId(), "Ecriture-" + savedEcriture.getId(), null, savedEcriture);

        return savedEcriture;
    }

    @Override
    public Ecriture getEcritureById(Long id) {
        return ecritureRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public void deleteEcritures(List<Long> ecritureIds) {
        // Validate that all IDs exist before deletion
        ecritureIds.forEach(id -> {
            if (!ecritureRepository.existsById(id)) {
                String errorMessage = "Ecriture with ID " + id + " does not exist";

                // Audit échec
                auditService.logFailure(userService.getCurrentUser(), "DELETE", "Ecriture", id, "Ecriture-" + id, errorMessage);

                throw new RuntimeException(errorMessage);
            }
        });

        // Audit avant suppression
        auditService.logSuccess(userService.getCurrentUser(), "DELETE", "Ecriture", null, ecritureIds.size() + " ecritures", Map.of("ecritureIds", ecritureIds), null);

        ecritureRepository.deleteAllById(ecritureIds);
    }

    @Transactional
    @Override
    public void updateCompte(String accountId, List<Long> ecritureIds) {
        // 1️⃣ Convert the account ID (String) to a Long
        Long accountLongId = Long.valueOf(accountId);

        // 2️⃣ Fetch the account from the database
        Account account = accountRepository.findById(accountLongId).orElseThrow(() -> {
            auditService.logFailure(userService.getCurrentUser(), "UPDATE_COMPTE", "Line", accountLongId, "Account-" + accountId, "Account not found with ID: " + accountId);
            return new IllegalArgumentException("Account not found with ID: " + accountId);
        });

        log.info("When Change accounting account here's the account ID ----> {}", account);

        // 3️⃣ Update the Line with the fetched Account object
        lineRepository.updateCompteByIds(account, ecritureIds);

        // Audit
        auditService.logSuccess(userService.getCurrentUser(), "UPDATE_COMPTE", "Line", null, "Updated " + ecritureIds.size() + " lines", Map.of("oldAccountId", accountId, "ecritureIds", ecritureIds), Map.of("newAccountId", accountId));
    }


    @Override
    @Transactional
    public EcritureDTO getEcritureDetails(Long ecritureId) {
        Ecriture ecriture = ecritureRepository.findEcritureByIdWithDetails(ecritureId).orElseThrow(() -> new RuntimeException("Ecriture not found with ID: " + ecritureId));

        // Ensure amountUpdated has a default value if it's null (for backward compatibility)
        if (ecriture.getAmountUpdated() == null) {
            ecriture.setAmountUpdated(false);
        }

        return mapToDTOWithDossier(ecriture);
    }

    private EcritureDTO mapToDTOWithDossier(Ecriture ecriture) {
        EcritureDTO dto = new EcritureDTO();
        dto.setId(ecriture.getId());
        dto.setUniqueEntryNumber(ecriture.getUniqueEntryNumber());
        dto.setEntryDate(ecriture.getEntryDate());

        // Set the amountUpdated field
        dto.setAmountUpdated(ecriture.getAmountUpdated());
        dto.setManuallyUpdated(ecriture.getManuallyUpdated());
        dto.setManualUpdateDate(ecriture.getManualUpdateDate());
        // Map Journal only if it's not null
        if (ecriture.getJournal() != null) {
            JournalDTO journalDTO = new JournalDTO();
            journalDTO.setId(ecriture.getJournal().getId());
            journalDTO.setName(ecriture.getJournal().getName());
            journalDTO.setType(ecriture.getJournal().getType());
            dto.setJournal(journalDTO);
        } else {
            dto.setJournal(null);
        }

        // Map Lines
        List<LineDTO> lineDTOs = ecriture.getLines().stream().map(line -> {
            LineDTO lineDTO = new LineDTO();
            lineDTO.setId(line.getId());

            if (line.getAccount() != null) {
                AccountDTO accountDTO = new AccountDTO();
                accountDTO.setId(line.getAccount().getId());
                accountDTO.setLabel(line.getAccount().getLabel());
                accountDTO.setAccount(line.getAccount().getAccount());
                lineDTO.setAccount(accountDTO);
            }

            lineDTO.setLabel(line.getLabel());
            lineDTO.setDebit(line.getDebit());
            lineDTO.setCredit(line.getCredit());

            // Set currency conversion fields
            lineDTO.setOriginalDebit(line.getOriginalDebit());
            lineDTO.setOriginalCredit(line.getOriginalCredit());
            lineDTO.setOriginalCurrency(line.getOriginalCurrency());
            lineDTO.setExchangeRate(line.getExchangeRate());
            lineDTO.setConvertedCurrency(line.getConvertedCurrency());
            lineDTO.setExchangeRateDate(line.getExchangeRateDate());
            lineDTO.setUsdDebit(line.getUsdDebit());
            lineDTO.setUsdCredit(line.getUsdCredit());
            lineDTO.setConvertedDebit(line.getConvertedDebit());
            lineDTO.setConvertedCredit(line.getConvertedCredit());
            lineDTO.setManuallyUpdated(line.getManuallyUpdated());
            lineDTO.setManualUpdateDate(line.getManualUpdateDate());
            return lineDTO;
        }).collect(Collectors.toList());
        dto.setLines(lineDTOs);

        // Map Piece
        if (ecriture.getPiece() != null) {
            PieceDTO pieceDTO = new PieceDTO();
            pieceDTO.setId(ecriture.getPiece().getId());
            pieceDTO.setFilename(ecriture.getPiece().getFilename());
            pieceDTO.setType(ecriture.getPiece().getType());
            pieceDTO.setUploadDate(ecriture.getPiece().getUploadDate());
            pieceDTO.setAmount(ecriture.getPiece().getAmount());
            pieceDTO.setStatus(ecriture.getPiece().getStatus());

            // Set currency-related fields
            pieceDTO.setAiCurrency(ecriture.getPiece().getAiCurrency());
            pieceDTO.setAiAmount(ecriture.getPiece().getAiAmount());
            pieceDTO.setExchangeRate(ecriture.getPiece().getExchangeRate());
            pieceDTO.setConvertedCurrency(ecriture.getPiece().getConvertedCurrency());
            pieceDTO.setExchangeRateDate(ecriture.getPiece().getExchangeRateDate());

            pieceDTO.setExchangeRateUpdated(ecriture.getPiece().getExchangeRateUpdated());
            // These fields might not exist directly in the Piece entity but would come from
            // processing in AIPieceProcessingService - add them if they exist in your entity
            // pieceDTO.setOriginalCurrency(...);
            // pieceDTO.setDossierCurrency(...);

            if (ecriture.getPiece().getDossier() != null) {
                pieceDTO.setDossierName(ecriture.getPiece().getDossier().getName());
                pieceDTO.setDossierId(ecriture.getPiece().getDossier().getId());

                // If dossier has a country with currency, you can set dossierCurrency
                if (ecriture.getPiece().getDossier().getCountry() != null && ecriture.getPiece().getDossier().getCountry().getCurrency() != null) {
                    pieceDTO.setDossierCurrency(ecriture.getPiece().getDossier().getCountry().getCurrency().getCode());
                }
            }

            // Optionally, you could also map factureData and ecritures if needed
            // but this might create circular references or excessive data
            // pieceDTO.setFactureData(...);
            // pieceDTO.setEcritures(...);

            dto.setPiece(pieceDTO);
        }

        return dto;
    }


    @Transactional
    @Override
    public Ecriture updateEcriture(Long ecritureId, Ecriture ecritureRequest) {
        Ecriture existingEcriture = ecritureRepository.findEcritureByIdCustom(ecritureId).orElseThrow(() -> {
            auditService.logFailure(userService.getCurrentUser(), "UPDATE", "Ecriture", ecritureId, "Ecriture-" + ecritureId, "Ecriture non trouvée avec l'identifiant : " + ecritureId);
            return new IllegalArgumentException("Ecriture non trouvée avec l'identifiant : " + ecritureId);
        });

        log.info("Existing Ecriture: {}", existingEcriture);
        log.info("Update Request: {}", ecritureRequest);

        if (ecritureRequest.getEntryDate() == null) {
            String errorMessage = "La date d'entrée est obligatoire.";
            auditService.logFailure(userService.getCurrentUser(), "UPDATE", "Ecriture", ecritureId, "Ecriture-" + ecritureId, errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        if (ecritureRequest.getJournal() == null || ecritureRequest.getJournal().getId() == null) {
            String errorMessage = "Le journal est obligatoire.";
            auditService.logFailure(userService.getCurrentUser(), "UPDATE", "Ecriture", ecritureId, "Ecriture-" + ecritureId, errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        Journal newJournal = journalRepository.findById(ecritureRequest.getJournal().getId()).orElseThrow(() -> {
            auditService.logFailure(userService.getCurrentUser(), "UPDATE", "Journal", ecritureRequest.getJournal().getId(), "Journal-" + ecritureRequest.getJournal().getId(), "Journal non trouvé avec l'identifiant : " + ecritureRequest.getJournal().getId());
            return new IllegalArgumentException("Journal non trouvé avec l'identifiant : " + ecritureRequest.getJournal().getId());
        });

        if (existingEcriture.getJournal() != null && !existingEcriture.getJournal().getDossier().getId().equals(newJournal.getDossier().getId())) {
            String errorMessage = "Le nouveau journal doit appartenir au même dossier.";
            auditService.logFailure(userService.getCurrentUser(), "UPDATE", "Ecriture", ecritureId, "Ecriture-" + ecritureId, errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        // ✅ GET DECIMAL PRECISION FROM DOSSIER/COUNTRY
        int decimalPrecision = 2; // Default
        if (existingEcriture.getPiece() != null && existingEcriture.getPiece().getDossier() != null && existingEcriture.getPiece().getDossier().getCountry() != null && existingEcriture.getPiece().getDossier().getDecimalPrecision() != null) {
            decimalPrecision = existingEcriture.getPiece().getDossier().getDecimalPrecision();
        }

        // ✅ VALIDATE BEFORE PROCESSING
        Map<String, String> balanceErrors = EcritureValidationUtil.validateEcritureBalance(ecritureRequest, decimalPrecision);

        if (!balanceErrors.isEmpty()) {
            String errorMessage = balanceErrors.values().stream().findFirst().orElse("Erreur de validation");
            log.error("❌ Validation errors: {}", balanceErrors);

            auditService.logFailure(userService.getCurrentUser(), "UPDATE", "Ecriture", ecritureId, "Ecriture-" + ecritureId, errorMessage);

            throw new IllegalArgumentException(errorMessage);
        }

        Map<String, String> exchangeRateErrors = EcritureValidationUtil.validateExchangeRate(ecritureRequest);
        if (!exchangeRateErrors.isEmpty()) {
            String errorMessage = exchangeRateErrors.values().stream().findFirst().orElse("Erreur de validation du taux de change");
            log.error("❌ Exchange rate validation errors: {}", exchangeRateErrors);

            auditService.logFailure(userService.getCurrentUser(), "UPDATE", "Ecriture", ecritureId, "Ecriture-" + ecritureId, errorMessage);

            throw new IllegalArgumentException(errorMessage);
        }

        // ✅ VALIDATION PASSED - Continue with update
        Ecriture oldEcriture = new Ecriture();
        oldEcriture.setId(existingEcriture.getId());
        oldEcriture.setJournal(existingEcriture.getJournal());
        oldEcriture.setEntryDate(existingEcriture.getEntryDate());
        // Copy other fields as needed

        existingEcriture.setJournal(newJournal);
        existingEcriture.setEntryDate(ecritureRequest.getEntryDate());

        if (ecritureRequest.getManuallyUpdated() != null && ecritureRequest.getManuallyUpdated()) {
            existingEcriture.setManuallyUpdated(true);
            existingEcriture.setManualUpdateDate(LocalDate.now());
        }

        // Update the amountUpdated field if it's provided in the request
        if (ecritureRequest.getAmountUpdated() != null) {
            existingEcriture.setAmountUpdated(ecritureRequest.getAmountUpdated());
        }

        // Check if the exchange rate has been updated
        Piece associatedPiece = existingEcriture.getPiece();
        if (associatedPiece != null && ecritureRequest.getExchangeRate() != null) {
            if (associatedPiece.getExchangeRate() == null || !associatedPiece.getExchangeRate().equals(ecritureRequest.getExchangeRate())) {
                associatedPiece.setExchangeRateUpdated(true);
                pieceRepository.save(associatedPiece);
            }
        }

        // Check for exchange rate information
        double exchangeRate = 0;
        boolean hasExchangeRate = false;

        for (Line line : ecritureRequest.getLines()) {
            if (line.getExchangeRate() != null && line.getExchangeRate() > 0) {
                exchangeRate = line.getExchangeRate();
                hasExchangeRate = true;
                break;
            }
        }

        // Update the lines
        updateEcritureLines(existingEcriture, ecritureRequest.getLines(), hasExchangeRate, exchangeRate, ecritureRequest.getManuallyUpdated());

        Ecriture updatedEcriture = ecritureRepository.save(existingEcriture);

        // Audit
        auditService.logSuccess(userService.getCurrentUser(), "UPDATE", "Ecriture", ecritureId, "Ecriture-" + ecritureId, oldEcriture, updatedEcriture);

        return updatedEcriture;
    }

    private void updateEcritureLines(Ecriture existingEcriture, List<Line> updatedLines, boolean hasExchangeRate, double exchangeRate, Boolean manuallyUpdated) {
        List<Line> existingLines = existingEcriture.getLines();

        // Step 1: Remove lines that no longer exist
        List<Long> updatedLineIds = updatedLines.stream().filter(line -> line.getId() != null).map(Line::getId).toList();

        List<Line> linesToRemove = existingLines.stream().filter(line -> !updatedLineIds.contains(line.getId())).toList();

        existingLines.removeAll(linesToRemove);

        // Step 2: Add new or update existing lines
        for (Line updatedLine : updatedLines) {
            if (updatedLine.getId() != null) {
                // Update existing line
                Line existingLine = existingLines.stream().filter(line -> line.getId().equals(updatedLine.getId())).findFirst().orElseThrow(() -> new IllegalArgumentException("Ligne non trouvée avec l'identifiant : " + updatedLine.getId()));

                // ✅ SAVE EXISTING VALUES BEFORE UPDATING
                String existingExchangeRateDate = String.valueOf(existingLine.getExchangeRateDate());
                String existingOriginalCurrency = existingLine.getOriginalCurrency();
                String existingConvertedCurrency = existingLine.getConvertedCurrency();
                Double existingExchangeRate = existingLine.getExchangeRate();

                // Fetch the Account to ensure it is managed
                Account managedAccount = accountRepository.findById(updatedLine.getAccount().getId()).orElseThrow(() -> new IllegalArgumentException("Account non trouvé avec l'identifiant : " + updatedLine.getAccount().getId()));

                existingLine.setAccount(managedAccount);
                existingLine.setLabel(updatedLine.getLabel());
                existingLine.setDebit(updatedLine.getDebit());
                existingLine.setCredit(updatedLine.getCredit());

                // ✅ PRESERVE CURRENCY FIELDS IF NOT PROVIDED IN UPDATE
                if (isValidCurrency(updatedLine.getOriginalCurrency())) {
                    existingLine.setOriginalCurrency(updatedLine.getOriginalCurrency());
                } else if (existingOriginalCurrency != null) {
                    // Keep existing if new one is invalid
                    existingLine.setOriginalCurrency(existingOriginalCurrency);
                } else {
                    existingLine.setOriginalCurrency(null);
                }

                if (isValidCurrency(updatedLine.getConvertedCurrency())) {
                    existingLine.setConvertedCurrency(updatedLine.getConvertedCurrency());
                } else if (existingConvertedCurrency != null) {
                    // Keep existing if new one is invalid
                    existingLine.setConvertedCurrency(existingConvertedCurrency);
                } else {
                    existingLine.setConvertedCurrency(null);
                }

                // ✅ PRESERVE EXCHANGE RATE IF NOT PROVIDED
                if (updatedLine.getExchangeRate() != null && updatedLine.getExchangeRate() > 0) {
                    existingLine.setExchangeRate(updatedLine.getExchangeRate());
                } else if (existingExchangeRate != null) {
                    // Keep existing if new one is not provided
                    existingLine.setExchangeRate(existingExchangeRate);
                } else {
                    existingLine.setExchangeRate(null);
                }

                existingLine.setOriginalDebit(updatedLine.getOriginalDebit());
                existingLine.setOriginalCredit(updatedLine.getOriginalCredit());
                existingLine.setConvertedDebit(updatedLine.getConvertedDebit());
                existingLine.setConvertedCredit(updatedLine.getConvertedCredit());

                // ✅ PRESERVE EXCHANGE RATE DATE - PRIORITY ORDER:
                // 1. Use new value if provided and valid
                // 2. Keep existing value if present
                // 3. Try to get from piece
                // 4. Set to null as last resort

                boolean hasNewExchangeRateDate = updatedLine.getExchangeRateDate() != null && !updatedLine.getExchangeRateDate().toString().isEmpty() && !updatedLine.getExchangeRateDate().toString().equals("null");

                if (hasNewExchangeRateDate) {
                    // New valid value provided - use it
                    existingLine.setExchangeRateDate(updatedLine.getExchangeRateDate().toString());
                    log.debug("📅 Updated exchange rate date from request: {}", updatedLine.getExchangeRateDate());
                } else if (existingExchangeRateDate != null && !existingExchangeRateDate.equals("null") && !existingExchangeRateDate.isEmpty()) {
                    // Keep existing value - DON'T OVERWRITE WITH NULL
                    log.debug("📅 Preserving existing exchange rate date: {}", existingExchangeRateDate);
                    // existingLine already has this value, no need to set
                } else if (existingEcriture.getPiece() != null && existingEcriture.getPiece().getExchangeRateDate() != null) {
                    // Get from piece if available
                    existingLine.setExchangeRateDate(existingEcriture.getPiece().getExchangeRateDate().toString());
                    log.info("📅 Set exchange rate date from piece: {}", existingEcriture.getPiece().getExchangeRateDate());
                } else {
                    // Last resort - set to null
                    existingLine.setExchangeRateDate(null);
                    log.debug("📅 No exchange rate date available, setting to null");
                }

                // Manual update tracking
                if (manuallyUpdated != null && manuallyUpdated) {
                    existingLine.setManuallyUpdated(true);
                    existingLine.setManualUpdateDate(LocalDate.now());
//                    log.debug("Line {} marked as manually updated", existingLine.getId());
                }

            } else {
                // Add a new line
                Account managedAccount = accountRepository.findById(updatedLine.getAccount().getId()).orElseThrow(() -> new IllegalArgumentException("Account non trouvé avec l'identifiant : " + updatedLine.getAccount().getId()));

                Line newLine = new Line();
                newLine.setAccount(managedAccount);
                newLine.setLabel(updatedLine.getLabel());
                newLine.setDebit(updatedLine.getDebit());
                newLine.setCredit(updatedLine.getCredit());
                newLine.setEcriture(existingEcriture);

                // Set currency fields from request, use null if not provided/invalid
                newLine.setOriginalCurrency(isValidCurrency(updatedLine.getOriginalCurrency()) ? updatedLine.getOriginalCurrency() : null);

                newLine.setConvertedCurrency(isValidCurrency(updatedLine.getConvertedCurrency()) ? updatedLine.getConvertedCurrency() : null);

                newLine.setExchangeRate(updatedLine.getExchangeRate() != null && updatedLine.getExchangeRate() > 0 ? updatedLine.getExchangeRate() : null);

                newLine.setOriginalDebit(updatedLine.getOriginalDebit());
                newLine.setOriginalCredit(updatedLine.getOriginalCredit());
                newLine.setConvertedDebit(updatedLine.getConvertedDebit());
                newLine.setConvertedCredit(updatedLine.getConvertedCredit());

                // ✅ For new lines, try to get exchangeRateDate from piece or request
                if (updatedLine.getExchangeRateDate() != null && !updatedLine.getExchangeRateDate().toString().isEmpty() && !updatedLine.getExchangeRateDate().toString().equals("null")) {
                    newLine.setExchangeRateDate(updatedLine.getExchangeRateDate().toString());
                } else if (existingEcriture.getPiece() != null && existingEcriture.getPiece().getExchangeRateDate() != null) {
                    // Get from piece if not provided in request
                    newLine.setExchangeRateDate(existingEcriture.getPiece().getExchangeRateDate().toString());
                    log.info("📅 Set exchange rate date for new line from piece: {}", existingEcriture.getPiece().getExchangeRateDate());
                } else {
                    newLine.setExchangeRateDate(null);
                }

                if (manuallyUpdated != null && manuallyUpdated) {
                    newLine.setManuallyUpdated(true);
                    newLine.setManualUpdateDate(LocalDate.now());
                    log.debug("New line marked as manually updated");
                }

                existingLines.add(newLine);
            }
        }

        // Update the Ecriture with the new list of lines
        existingEcriture.setLines(existingLines);
    }

    private boolean isValidCurrency(String currency) {
        return currency != null && !currency.isEmpty() && !currency.equals("NAN&") && !currency.equals("null") && !currency.equals("undefined") && currency.matches("[A-Z]{3}");
    }

    @Override
    public List<EcritureExportDTO> exportEcritures(Long dossierId, Long exerciseId, Long journalId, LocalDate startDate, LocalDate endDate) {
        List<EcritureExportDTO> exports = ecritureRepository.findEcrituresByFilters(dossierId, exerciseId, journalId, startDate, endDate);

        // Audit
        auditService.logSuccess(userService.getCurrentUser(), "EXPORT", "Ecriture", dossierId, "Dossier-" + dossierId, Map.of("exerciseId", exerciseId, "journalId", journalId, "startDate", startDate, "endDate", endDate, "exportCount", exports.size()), null);

        return exports;
    }
}
