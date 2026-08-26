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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    public EcritureServiceImpl(EcritureRepository ecritureRepository, LineRepository lineRepository,
                               JournalRepository journalRepository, AccountRepository accountRepository,
                               PieceRepository pieceRepository, AuditService auditService, UserService userService) {
        this.ecritureRepository = ecritureRepository;
        this.lineRepository = lineRepository;
        this.journalRepository = journalRepository;
        this.accountRepository = accountRepository;
        this.pieceRepository = pieceRepository;
        this.auditService = auditService;
        this.userService = userService;
    }

    // ✅ Méthode utilitaire pour récupérer le cabinet cible à partir d'une écriture
    private Long getTargetCabinetId(Ecriture ecriture) {
        if (ecriture != null && ecriture.getPiece() != null &&
                ecriture.getPiece().getDossier() != null &&
                ecriture.getPiece().getDossier().getCabinet() != null) {
            return ecriture.getPiece().getDossier().getCabinet().getId();
        }
        return null;
    }

    // ✅ Méthode utilitaire pour récupérer le nom du cabinet cible
    private String getTargetCabinetName(Ecriture ecriture) {
        if (ecriture != null && ecriture.getPiece() != null &&
                ecriture.getPiece().getDossier() != null &&
                ecriture.getPiece().getDossier().getCabinet() != null) {
            return ecriture.getPiece().getDossier().getCabinet().getName();
        }
        return null;
    }

    // ✅ Méthode utilitaire à partir d'un ID d'écriture
    private Long getTargetCabinetId(@NonNull Long ecritureId) {
        try {
            Ecriture ecriture = ecritureRepository.findById(ecritureId).orElse(null);
            return getTargetCabinetId(ecriture);
        } catch (Exception e) {
            log.warn("Could not get target cabinet ID for ecriture: {}", ecritureId);
            return null;
        }
    }

    // ✅ Méthode utilitaire pour le nom du cabinet cible à partir d'un ID
    private String getTargetCabinetName(@NonNull Long ecritureId) {
        try {
            Ecriture ecriture = ecritureRepository.findById(ecritureId).orElse(null);
            return getTargetCabinetName(ecriture);
        } catch (Exception e) {
            log.warn("Could not get target cabinet name for ecriture: {}", ecritureId);
            return null;
        }
    }

    @Override
    public List<Ecriture> getEcrituresByPieceId(@NonNull Long pieceId) {
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

        List<EcritureDTO> paginatedDTOs = new ArrayList<>(allDTOs.subList(start, end));

        // Return as Page
        return new PageImpl<>(paginatedDTOs, PageRequest.of(page, size), allDTOs.size());
    }

    private EcritureDTO mapToDTO(Ecriture ecriture) {
        EcritureDTO dto = new EcritureDTO();
        dto.setId(ecriture.getId());
        dto.setUniqueEntryNumber(ecriture.getUniqueEntryNumber());
        dto.setEntryDate(ecriture.getEntryDate());
        dto.setExchangeRate(ecriture.getExchangeRate());
        dto.setOriginalCurrency(ecriture.getOriginalCurrency());
        dto.setConvertedCurrency(ecriture.getConvertedCurrency());
        dto.setExchangeRateDate(ecriture.getExchangeRateDate());

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
            lineDTO.setTaxRate(line.getTaxRate());

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
    public Ecriture updateEcriture(@NonNull Ecriture ecriture) {
        Ecriture savedEcriture = ecritureRepository.save(ecriture);

        // ✅ Audit avec cabinet cible
        Long targetCabinetId = getTargetCabinetId(savedEcriture);
        String targetCabinetName = getTargetCabinetName(savedEcriture);

        auditService.logSuccessWithTargetCabinet(
                userService.getCurrentUser(),
                "UPDATE",
                "Ecriture",
                savedEcriture.getId(),
                "Ecriture-" + savedEcriture.getId(),
                null,
                savedEcriture,
                targetCabinetId,
                targetCabinetName
        );

        return savedEcriture;
    }

    @Override
    public Ecriture getEcritureById(@NonNull Long id) {
        return ecritureRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public void deleteEcritures(@NonNull List<Long> ecritureIds) {
        // Récupérer les écritures pour avoir le cabinet cible
        List<Ecriture> ecritures = ecritureRepository.findAllById(ecritureIds);

        // Audit avant suppression pour chaque écriture
        for (Ecriture ecriture : ecritures) {
            Long targetCabinetId = getTargetCabinetId(ecriture);
            String targetCabinetName = getTargetCabinetName(ecriture);

            auditService.logSuccessWithTargetCabinet(
                    userService.getCurrentUser(),
                    "DELETE",
                    "Ecriture",
                    ecriture.getId(),
                    "Ecriture-" + ecriture.getId(),
                    ecriture,
                    null,
                    targetCabinetId,
                    targetCabinetName
            );
        }

        // Validate that all IDs exist before deletion
        ecritureIds.forEach(id -> {
            Long nid = Objects.requireNonNull(id, "ecriture id");
            if (!ecritureRepository.existsById(nid)) {
                String errorMessage = "Ecriture with ID " + nid + " does not exist";

                Long targetCabinetId = getTargetCabinetId(nid);
                String targetCabinetName = getTargetCabinetName(nid);

                // Audit échec
                auditService.logFailureWithTargetCabinet(
                        userService.getCurrentUser(),
                        "DELETE",
                        "Ecriture",
                        nid,
                        "Ecriture-" + nid,
                        errorMessage,
                        targetCabinetId,
                        targetCabinetName
                );

                throw new RuntimeException(errorMessage);
            }
        });

        ecritureRepository.deleteAllById(ecritureIds);
    }

    @Transactional
    @Override
    public void updateCompte(String accountId, @NonNull List<Long> ecritureIds) {
        // 1️⃣ Convert the account ID (String) to a Long
        long accountPk = Long.parseLong(accountId);
        Long accountLongId = accountPk;

        // 2️⃣ Fetch the account from the database
        Account account = accountRepository.findById(accountLongId).orElseThrow(() -> {
            auditService.logFailure(
                    userService.getCurrentUser(),
                    "UPDATE_COMPTE",
                    "Line",
                    accountLongId,
                    "Account-" + accountId,
                    "Account not found with ID: " + accountId
            );
            return new IllegalArgumentException("Account not found with ID: " + accountId);
        });

        log.info("When Change accounting account here's the account ID ----> {}", account);

        // 3️⃣ Update the Line with the fetched Account object
        lineRepository.updateCompteByIds(account, ecritureIds);

        // ✅ Audit avec cabinet cible (récupéré à partir de la première écriture)
        Long targetCabinetId = null;
        String targetCabinetName = null;
        if (!ecritureIds.isEmpty()) {
            Long firstEcritureId = Objects.requireNonNull(ecritureIds.get(0), "ecriture id");
            targetCabinetId = getTargetCabinetId(firstEcritureId);
            targetCabinetName = getTargetCabinetName(firstEcritureId);
        }

        auditService.logSuccessWithTargetCabinet(
                userService.getCurrentUser(),
                "UPDATE_COMPTE",
                "Line",
                null,
                "Updated " + ecritureIds.size() + " lines",
                Map.of("oldAccountId", accountId, "ecritureIds", ecritureIds),
                Map.of("newAccountId", accountId),
                targetCabinetId,
                targetCabinetName
        );
    }

    @Override
    @Transactional
    public EcritureDTO getEcritureDetails(@NonNull Long ecritureId) {
        Ecriture ecriture = ecritureRepository.findEcritureByIdWithDetails(ecritureId)
                .orElseThrow(() -> new RuntimeException("Ecriture not found with ID: " + ecritureId));

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
        dto.setExchangeRate(ecriture.getExchangeRate());
        dto.setOriginalCurrency(ecriture.getOriginalCurrency());
        dto.setConvertedCurrency(ecriture.getConvertedCurrency());
        dto.setExchangeRateDate(ecriture.getExchangeRateDate());

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
            lineDTO.setTaxRate(line.getTaxRate());

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

            if (ecriture.getPiece().getDossier() != null) {
                pieceDTO.setDossierName(ecriture.getPiece().getDossier().getName());
                pieceDTO.setDossierId(ecriture.getPiece().getDossier().getId());

                // If dossier has a country with currency, you can set dossierCurrency
                if (ecriture.getPiece().getDossier().getCountry() != null &&
                        ecriture.getPiece().getDossier().getCountry().getCurrency() != null) {
                    pieceDTO.setDossierCurrency(ecriture.getPiece().getDossier().getCountry().getCurrency().getCode());
                }
            }

            dto.setPiece(pieceDTO);
        }

        return dto;
    }

    @Transactional
    @Override
    public Ecriture updateEcriture(@NonNull Long ecritureId, @NonNull Ecriture ecritureRequest) {
        Ecriture existingEcriture = ecritureRepository.findEcritureByIdCustom(ecritureId)
                .orElseThrow(() -> {
                    Long targetCabinetId = getTargetCabinetId(ecritureId);
                    String targetCabinetName = getTargetCabinetName(ecritureId);

                    auditService.logFailureWithTargetCabinet(
                            userService.getCurrentUser(),
                            "UPDATE",
                            "Ecriture",
                            ecritureId,
                            "Ecriture-" + ecritureId,
                            "Ecriture non trouvée avec l'identifiant : " + ecritureId,
                            targetCabinetId,
                            targetCabinetName
                    );
                    return new IllegalArgumentException("Ecriture non trouvée avec l'identifiant : " + ecritureId);
                });

        // ✅ Récupérer le cabinet cible
        Long targetCabinetId = getTargetCabinetId(existingEcriture);
        String targetCabinetName = getTargetCabinetName(existingEcriture);

        log.info("Existing Ecriture: {}", existingEcriture);
        log.info("Update Request: {}", ecritureRequest);

        if (ecritureRequest.getEntryDate() == null) {
            String errorMessage = "La date d'entrée est obligatoire.";
            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "UPDATE",
                    "Ecriture",
                    ecritureId,
                    "Ecriture-" + ecritureId,
                    errorMessage,
                    targetCabinetId,
                    targetCabinetName
            );
            throw new IllegalArgumentException(errorMessage);
        }

        if (ecritureRequest.getJournal() == null || ecritureRequest.getJournal().getId() == null) {
            String errorMessage = "Le journal est obligatoire.";
            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "UPDATE",
                    "Ecriture",
                    ecritureId,
                    "Ecriture-" + ecritureId,
                    errorMessage,
                    targetCabinetId,
                    targetCabinetName
            );
            throw new IllegalArgumentException(errorMessage);
        }

        Long newJournalId = Objects.requireNonNull(ecritureRequest.getJournal().getId(), "journal id");
        Journal newJournal = journalRepository.findById(newJournalId)
                .orElseThrow(() -> {
                    auditService.logFailure(
                            userService.getCurrentUser(),
                            "UPDATE",
                            "Journal",
                            newJournalId,
                            "Journal-" + newJournalId,
                            "Journal non trouvé avec l'identifiant : " + newJournalId
                    );
                    return new IllegalArgumentException("Journal non trouvé avec l'identifiant : " + newJournalId);
                });

        if (existingEcriture.getJournal() != null &&
                !existingEcriture.getJournal().getDossier().getId().equals(newJournal.getDossier().getId())) {
            String errorMessage = "Le nouveau journal doit appartenir au même dossier.";
            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "UPDATE",
                    "Ecriture",
                    ecritureId,
                    "Ecriture-" + ecritureId,
                    errorMessage,
                    targetCabinetId,
                    targetCabinetName
            );
            throw new IllegalArgumentException(errorMessage);
        }

        // ✅ GET DECIMAL PRECISION FROM DOSSIER/COUNTRY
        int decimalPrecision = 2; // Default
        if (existingEcriture.getPiece() != null &&
                existingEcriture.getPiece().getDossier() != null &&
                existingEcriture.getPiece().getDossier().getCountry() != null &&
                existingEcriture.getPiece().getDossier().getDecimalPrecision() != null) {
            decimalPrecision = existingEcriture.getPiece().getDossier().getDecimalPrecision();
        }

        // ✅ VALIDATE BEFORE PROCESSING
        Map<String, String> balanceErrors = EcritureValidationUtil.validateEcritureBalance(ecritureRequest, decimalPrecision);

        if (!balanceErrors.isEmpty()) {
            String errorMessage = balanceErrors.values().stream().findFirst().orElse("Erreur de validation");
            log.error("❌ Validation errors: {}", balanceErrors);

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "UPDATE",
                    "Ecriture",
                    ecritureId,
                    "Ecriture-" + ecritureId,
                    errorMessage,
                    targetCabinetId,
                    targetCabinetName
            );

            throw new IllegalArgumentException(errorMessage);
        }

        Map<String, String> exchangeRateErrors = EcritureValidationUtil.validateExchangeRate(ecritureRequest);
        if (!exchangeRateErrors.isEmpty()) {
            String errorMessage = exchangeRateErrors.values().stream().findFirst().orElse("Erreur de validation du taux de change");
            log.error("❌ Exchange rate validation errors: {}", exchangeRateErrors);

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "UPDATE",
                    "Ecriture",
                    ecritureId,
                    "Ecriture-" + ecritureId,
                    errorMessage,
                    targetCabinetId,
                    targetCabinetName
            );

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
        existingEcriture.setExchangeRate(ecritureRequest.getExchangeRate());
        existingEcriture.setOriginalCurrency(ecritureRequest.getOriginalCurrency());
        existingEcriture.setConvertedCurrency(ecritureRequest.getConvertedCurrency());
        existingEcriture.setExchangeRateDate(ecritureRequest.getExchangeRateDate());

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
        Double previousPieceExchangeRate = associatedPiece != null ? associatedPiece.getExchangeRate() : null;
        if (associatedPiece != null && ecritureRequest.getExchangeRate() != null) {
            if (associatedPiece.getExchangeRate() == null || !associatedPiece.getExchangeRate().equals(ecritureRequest.getExchangeRate())) {
                associatedPiece.setExchangeRateUpdated(true);
            }
            associatedPiece.setExchangeRate(ecritureRequest.getExchangeRate());
            associatedPiece.setAiCurrency(ecritureRequest.getOriginalCurrency());
            associatedPiece.setConvertedCurrency(ecritureRequest.getConvertedCurrency());
            associatedPiece.setExchangeRateDate(ecritureRequest.getExchangeRateDate());

            if (associatedPiece.getFactureData() != null) {
                associatedPiece.getFactureData().setExchangeRate(ecritureRequest.getExchangeRate());
                associatedPiece.getFactureData().setOriginalCurrency(ecritureRequest.getOriginalCurrency());
                associatedPiece.getFactureData().setConvertedCurrency(ecritureRequest.getConvertedCurrency());
                associatedPiece.getFactureData().setExchangeRateDate(ecritureRequest.getExchangeRateDate());
            }

            pieceRepository.save(associatedPiece);
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
        updateEcritureLines(existingEcriture, ecritureRequest.getLines(), hasExchangeRate, exchangeRate,
                ecritureRequest.getManuallyUpdated(), ecritureRequest.getAmountUpdated(), decimalPrecision,
                previousPieceExchangeRate);

        Ecriture updatedEcriture = ecritureRepository.save(existingEcriture);

        // ✅ Audit avec cabinet cible
        auditService.logSuccessWithTargetCabinet(
                userService.getCurrentUser(),
                "UPDATE",
                "Ecriture",
                ecritureId,
                "Ecriture-" + ecritureId,
                oldEcriture,
                updatedEcriture,
                targetCabinetId,
                targetCabinetName
        );

        return updatedEcriture;
    }

    private void updateEcritureLines(Ecriture existingEcriture, List<Line> updatedLines,
                                     boolean hasExchangeRate, double exchangeRate, Boolean manuallyUpdated,
                                     Boolean amountUpdated, int decimalPrecision, Double previousPieceExchangeRate) {
        List<Line> existingLines = existingEcriture.getLines();

        // Step 1: Remove lines that no longer exist
        List<Long> updatedLineIds = updatedLines.stream()
                .filter(line -> line.getId() != null)
                .map(Line::getId)
                .toList();

        List<Line> linesToRemove = existingLines.stream()
                .filter(line -> !updatedLineIds.contains(line.getId()))
                .toList();

        existingLines.removeAll(linesToRemove);

        // Step 2: Add new or update existing lines
        for (Line updatedLine : updatedLines) {
            if (updatedLine.getId() != null) {
                // Update existing line
                Line existingLine = existingLines.stream()
                        .filter(line -> line.getId().equals(updatedLine.getId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Ligne non trouvée avec l'identifiant : " + updatedLine.getId()));

                // ✅ SAVE EXISTING VALUES BEFORE UPDATING
                String existingExchangeRateDate = String.valueOf(existingLine.getExchangeRateDate());
                String existingOriginalCurrency = existingLine.getOriginalCurrency();
                String existingConvertedCurrency = existingLine.getConvertedCurrency();
                Double existingExchangeRate = existingLine.getExchangeRate();
                Double existingDebit = existingLine.getDebit();
                Double existingCredit = existingLine.getCredit();

                // Fetch the Account to ensure it is managed
                Long lineAccountId = Objects.requireNonNull(
                        Objects.requireNonNull(updatedLine.getAccount(), "line account").getId(), "account id");
                Account managedAccount = accountRepository.findById(lineAccountId)
                        .orElseThrow(() -> new IllegalArgumentException("Account non trouvé avec l'identifiant : " + lineAccountId));

                existingLine.setAccount(managedAccount);
                existingLine.setLabel(updatedLine.getLabel());
                existingLine.setTaxRate(updatedLine.getTaxRate());

                // ✅ PRESERVE CURRENCY FIELDS IF NOT PROVIDED IN UPDATE
                if (isValidCurrency(updatedLine.getOriginalCurrency())) {
                    existingLine.setOriginalCurrency(updatedLine.getOriginalCurrency());
                } else if (existingOriginalCurrency != null) {
                    existingLine.setOriginalCurrency(existingOriginalCurrency);
                } else {
                    existingLine.setOriginalCurrency(null);
                }

                if (isValidCurrency(updatedLine.getConvertedCurrency())) {
                    existingLine.setConvertedCurrency(updatedLine.getConvertedCurrency());
                } else if (existingConvertedCurrency != null) {
                    existingLine.setConvertedCurrency(existingConvertedCurrency);
                } else {
                    existingLine.setConvertedCurrency(null);
                }

                if (updatedLine.getExchangeRate() != null && updatedLine.getExchangeRate() > 0) {
                    existingLine.setExchangeRate(updatedLine.getExchangeRate());
                } else if (existingExchangeRate != null) {
                    existingLine.setExchangeRate(existingExchangeRate);
                } else {
                    existingLine.setExchangeRate(null);
                }

                if (!Boolean.TRUE.equals(amountUpdated) &&
                        shouldRecalculateConvertedAmounts(existingLine) &&
                        !hasManualAmountChange(existingLine, updatedLine)) {
                    recalculateExistingLineAmounts(existingLine, updatedLine, existingDebit, existingCredit,
                            existingExchangeRate, previousPieceExchangeRate, decimalPrecision);
                } else {
                    applyRequestedLineAmounts(existingLine, updatedLine);
                }
                existingLine.setUsdDebit(updatedLine.getUsdDebit());
                existingLine.setUsdCredit(updatedLine.getUsdCredit());

                // ✅ PRESERVE EXCHANGE RATE DATE
                boolean hasNewExchangeRateDate = updatedLine.getExchangeRateDate() != null &&
                        !updatedLine.getExchangeRateDate().toString().isEmpty() &&
                        !updatedLine.getExchangeRateDate().toString().equals("null");

                if (hasNewExchangeRateDate) {
                    existingLine.setExchangeRateDate(updatedLine.getExchangeRateDate().toString());
                    log.debug("📅 Updated exchange rate date from request: {}", updatedLine.getExchangeRateDate());
                } else if (existingExchangeRateDate != null &&
                        !existingExchangeRateDate.equals("null") &&
                        !existingExchangeRateDate.isEmpty()) {
                    log.debug("📅 Preserving existing exchange rate date: {}", existingExchangeRateDate);
                } else if (existingEcriture.getPiece() != null &&
                        existingEcriture.getPiece().getExchangeRateDate() != null) {
                    existingLine.setExchangeRateDate(existingEcriture.getPiece().getExchangeRateDate().toString());
                    log.info("📅 Set exchange rate date from piece: {}", existingEcriture.getPiece().getExchangeRateDate());
                } else {
                    existingLine.setExchangeRateDate(null);
                    log.debug("📅 No exchange rate date available, setting to null");
                }

                // Manual update tracking
                if (manuallyUpdated != null && manuallyUpdated) {
                    existingLine.setManuallyUpdated(true);
                    existingLine.setManualUpdateDate(LocalDate.now());
                }

            } else {
                // Add a new line
                Long newLineAccountId = Objects.requireNonNull(
                        Objects.requireNonNull(updatedLine.getAccount(), "line account").getId(), "account id");
                Account managedAccount = accountRepository.findById(newLineAccountId)
                        .orElseThrow(() -> new IllegalArgumentException("Account non trouvé avec l'identifiant : " + newLineAccountId));

                Line newLine = new Line();
                newLine.setAccount(managedAccount);
                newLine.setLabel(updatedLine.getLabel());
                newLine.setEcriture(existingEcriture);
                newLine.setTaxRate(updatedLine.getTaxRate());

                newLine.setOriginalCurrency(isValidCurrency(updatedLine.getOriginalCurrency()) ? updatedLine.getOriginalCurrency() : null);
                newLine.setConvertedCurrency(isValidCurrency(updatedLine.getConvertedCurrency()) ? updatedLine.getConvertedCurrency() : null);
                newLine.setExchangeRate(updatedLine.getExchangeRate() != null && updatedLine.getExchangeRate() > 0 ? updatedLine.getExchangeRate() : null);
                newLine.setDebit(updatedLine.getDebit());
                newLine.setCredit(updatedLine.getCredit());
                newLine.setOriginalDebit(updatedLine.getOriginalDebit());
                newLine.setOriginalCredit(updatedLine.getOriginalCredit());
                newLine.setConvertedDebit(updatedLine.getConvertedDebit());
                newLine.setConvertedCredit(updatedLine.getConvertedCredit());
                newLine.setUsdDebit(updatedLine.getUsdDebit());
                newLine.setUsdCredit(updatedLine.getUsdCredit());

                if (updatedLine.getExchangeRateDate() != null &&
                        !updatedLine.getExchangeRateDate().toString().isEmpty() &&
                        !updatedLine.getExchangeRateDate().toString().equals("null")) {
                    newLine.setExchangeRateDate(updatedLine.getExchangeRateDate().toString());
                } else if (existingEcriture.getPiece() != null &&
                        existingEcriture.getPiece().getExchangeRateDate() != null) {
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

    private boolean shouldRecalculateConvertedAmounts(Line line) {
        return line.getExchangeRate() != null && line.getExchangeRate() > 0 &&
                isValidCurrency(line.getOriginalCurrency()) &&
                isValidCurrency(line.getConvertedCurrency()) &&
                !line.getOriginalCurrency().equals(line.getConvertedCurrency());
    }

    private boolean hasManualAmountChange(Line existingLine, Line updatedLine) {
        return !approximatelyEqual(existingLine.getDebit(), updatedLine.getDebit()) ||
                !approximatelyEqual(existingLine.getCredit(), updatedLine.getCredit());
    }

    private void applyRequestedLineAmounts(Line existingLine, Line updatedLine) {
        if (shouldRecalculateConvertedAmounts(existingLine)) {
            Double finalDebit = firstNonNull(updatedLine.getConvertedDebit(), updatedLine.getDebit());
            Double finalCredit = firstNonNull(updatedLine.getConvertedCredit(), updatedLine.getCredit());

            existingLine.setDebit(finalDebit);
            existingLine.setCredit(finalCredit);
            existingLine.setConvertedDebit(finalDebit);
            existingLine.setConvertedCredit(finalCredit);
            existingLine.setOriginalDebit(firstNonNull(updatedLine.getOriginalDebit(),
                    divideByRate(finalDebit, existingLine.getExchangeRate())));
            existingLine.setOriginalCredit(firstNonNull(updatedLine.getOriginalCredit(),
                    divideByRate(finalCredit, existingLine.getExchangeRate())));
        } else {
            existingLine.setDebit(updatedLine.getDebit());
            existingLine.setCredit(updatedLine.getCredit());
            existingLine.setOriginalDebit(updatedLine.getOriginalDebit());
            existingLine.setOriginalCredit(updatedLine.getOriginalCredit());
            existingLine.setConvertedDebit(updatedLine.getConvertedDebit());
            existingLine.setConvertedCredit(updatedLine.getConvertedCredit());
        }
    }

    private Double firstNonNull(Double first, Double fallback) {
        return first != null ? first : fallback;
    }

    private void recalculateExistingLineAmounts(Line existingLine, Line updatedLine, Double existingDebit,
                                                Double existingCredit, Double existingExchangeRate,
                                                Double previousPieceExchangeRate,
                                                int decimalPrecision) {
        Double originalDebit = resolveOriginalAmount(existingLine.getOriginalDebit(), existingDebit,
                existingExchangeRate, previousPieceExchangeRate, updatedLine.getOriginalDebit());
        Double originalCredit = resolveOriginalAmount(existingLine.getOriginalCredit(), existingCredit,
                existingExchangeRate, previousPieceExchangeRate, updatedLine.getOriginalCredit());

        Double convertedDebit = multiplyByRate(originalDebit, existingLine.getExchangeRate(), decimalPrecision);
        Double convertedCredit = multiplyByRate(originalCredit, existingLine.getExchangeRate(), decimalPrecision);

        existingLine.setOriginalDebit(originalDebit);
        existingLine.setOriginalCredit(originalCredit);
        existingLine.setDebit(convertedDebit);
        existingLine.setCredit(convertedCredit);
        existingLine.setConvertedDebit(convertedDebit);
        existingLine.setConvertedCredit(convertedCredit);
    }

    private Double resolveOriginalAmount(Double existingOriginal, Double existingConverted,
                                         Double existingExchangeRate, Double previousPieceExchangeRate,
                                         Double requestedOriginal) {
        if (existingOriginal != null) {
            if (existingConverted != null &&
                    existingExchangeRate != null && existingExchangeRate > 0 &&
                    previousPieceExchangeRate != null && previousPieceExchangeRate > 0 &&
                    !existingExchangeRate.equals(previousPieceExchangeRate) &&
                    approximatelyEqual(existingOriginal, existingConverted)) {
                return BigDecimal.valueOf(existingConverted)
                        .divide(BigDecimal.valueOf(previousPieceExchangeRate), 10, RoundingMode.HALF_UP)
                        .doubleValue();
            }
            return existingOriginal;
        }

        if (existingConverted != null && existingExchangeRate != null && existingExchangeRate > 0) {
            return BigDecimal.valueOf(existingConverted)
                    .divide(BigDecimal.valueOf(existingExchangeRate), 10, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return requestedOriginal;
    }

    private boolean approximatelyEqual(Double left, Double right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return Math.abs(left - right) < 0.000001;
    }

    private Double divideByRate(Double amount, Double exchangeRate) {
        if (amount == null || exchangeRate == null || exchangeRate <= 0) {
            return null;
        }

        return BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(exchangeRate), 10, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Double multiplyByRate(Double amount, Double exchangeRate, int decimalPrecision) {
        if (amount == null || exchangeRate == null) {
            return null;
        }

        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(exchangeRate))
                .setScale(decimalPrecision, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private boolean isValidCurrency(String currency) {
        return currency != null && !currency.isEmpty() && !currency.equals("NAN&") &&
                !currency.equals("null") && !currency.equals("undefined") && currency.matches("[A-Z]{3}");
    }

    @Override
    public List<EcritureExportDTO> exportEcritures(@NonNull Long dossierId, Long exerciseId, Long journalId,
                                                   LocalDate startDate, LocalDate endDate) {
        return ecritureRepository.findEcrituresByFilters(dossierId, exerciseId, journalId, startDate, endDate);
    }
}
