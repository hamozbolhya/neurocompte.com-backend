package com.pacioli.core.services.serviceImp;

import com.pacioli.core.DTO.*;
import com.pacioli.core.models.Account;
import com.pacioli.core.models.Ecriture;
import com.pacioli.core.models.Journal;
import com.pacioli.core.models.Line;
import com.pacioli.core.repositories.AccountRepository;
import com.pacioli.core.repositories.EcritureRepository;
import com.pacioli.core.repositories.JournalRepository;
import com.pacioli.core.repositories.LineRepository;
import com.pacioli.core.services.EcritureService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final LineRepository lineRepository;
    private final JournalRepository journalRepository;
    private final AccountRepository accountRepository;

    @Autowired
    public EcritureServiceImpl(EcritureRepository ecritureRepository, LineRepository lineRepository, JournalRepository journalRepository, AccountRepository accountRepository) {
        this.ecritureRepository = ecritureRepository;
        this.lineRepository = lineRepository;
        this.journalRepository = journalRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public List<Ecriture> getAllEcritures() {
        return ecritureRepository.findAll();
    }

    @Override
    public List<Ecriture> getEcrituresByPieceId(Long pieceId) {
        return ecritureRepository.findByPieceId(pieceId);
    }

    @Override
    public List<Ecriture> getEcrituresByExercise(Long exerciseId) {
        if (exerciseId != null) {
            return ecritureRepository.findByPiece_Dossier_Exercises_Id(exerciseId);
        }
        return List.of(); // Return empty list if exerciseId is null
    }

    @Override
    public List<EcritureDTO> getEcrituresByExerciseAndCabinet(Long exerciseId, Long cabinetId) {
        List<Ecriture> ecritures = ecritureRepository.findEcrituresByExerciseAndCabinet(exerciseId, cabinetId);
        return ecritures.stream()
                .map(e -> mapToDTO(e))
                .collect(Collectors.toList());
    }

    private EcritureDTO mapToDTO(Ecriture ecriture) {
        EcritureDTO dto = new EcritureDTO();
        dto.setId(ecriture.getId());
        dto.setUniqueEntryNumber(ecriture.getUniqueEntryNumber());
        dto.setEntryDate(ecriture.getEntryDate());

        // Handle the case where the Journal is null
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

            // Map Account to AccountDTO
            AccountDTO accountDTO = new AccountDTO();
            if (line.getAccount() != null) {
                accountDTO.setId(line.getAccount().getId());
                accountDTO.setLabel(line.getAccount().getLabel());
                accountDTO.setAccount(line.getAccount().getAccount());
            }
            lineDTO.setAccount(accountDTO);

            // Map basic fields
            lineDTO.setLabel(line.getLabel());
            lineDTO.setDebit(line.getDebit());
            lineDTO.setCredit(line.getCredit());

            // Map currency conversion fields
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

        // Add piece if needed
        if (ecriture.getPiece() != null) {
            PieceDTO pieceDTO = new PieceDTO();
            pieceDTO.setId(ecriture.getPiece().getId());
            // Map other piece fields as needed
            dto.setPiece(pieceDTO);
        }

        return dto;
    }




    @Override
    @Transactional
    public Ecriture updateEcriture(Ecriture ecriture) {
        return ecritureRepository.save(ecriture);
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
                throw new RuntimeException("Ecriture with ID " + id + " does not exist");
            }
        });
        ecritureRepository.deleteAllById(ecritureIds);
    }

    @Transactional
    @Override
    public void updateCompte(String accountId, List<Long> ecritureIds) {
        // 1️⃣ Convert the account ID (String) to a Long
        Long accountLongId = Long.valueOf(accountId);

        // 2️⃣ Fetch the account from the database
        Account account = accountRepository.findById(accountLongId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + accountId));

        log.info("When Change accounting account here's the account ID ----> {}", account);

        // 3️⃣ Update the Line with the fetched Account object
        lineRepository.updateCompteByIds(account, ecritureIds);
    }


    @Override
    @Transactional
    public EcritureDTO getEcritureDetails(Long ecritureId) {
        Ecriture ecriture = ecritureRepository.findEcritureByIdWithDetails(ecritureId)
                .orElseThrow(() -> new RuntimeException("Ecriture not found with ID: " + ecritureId));

        return mapToDTOWithDossier(ecriture);
    }

    private EcritureDTO mapToDTOWithDossier(Ecriture ecriture) {
        EcritureDTO dto = new EcritureDTO();
        dto.setId(ecriture.getId());
        dto.setUniqueEntryNumber(ecriture.getUniqueEntryNumber());
        dto.setEntryDate(ecriture.getEntryDate());

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

            // These fields might not exist directly in the Piece entity but would come from
            // processing in AIPieceProcessingService - add them if they exist in your entity
            // pieceDTO.setOriginalCurrency(...);
            // pieceDTO.setDossierCurrency(...);

            if (ecriture.getPiece().getDossier() != null) {
                pieceDTO.setDossierName(ecriture.getPiece().getDossier().getName());
                pieceDTO.setDossierId(ecriture.getPiece().getDossier().getId());

                // If dossier has a country with currency, you can set dossierCurrency
                if (ecriture.getPiece().getDossier().getCountry() != null &&
                        ecriture.getPiece().getDossier().getCountry().getCurrency() != null) {
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
        Ecriture existingEcriture = ecritureRepository.findEcritureByIdCustom(ecritureId)
                .orElseThrow(() -> new IllegalArgumentException("Ecriture non trouvée avec l'identifiant : " + ecritureId));

        log.info("Existing Ecriture: {}", existingEcriture);
        log.info("Update Request: {}", ecritureRequest);

        if (ecritureRequest.getEntryDate() == null) {
            throw new IllegalArgumentException("La date d'entrée est obligatoire.");
        }

        if (ecritureRequest.getJournal() == null || ecritureRequest.getJournal().getId() == null) {
            throw new IllegalArgumentException("Le journal est obligatoire.");
        }

        Journal newJournal = journalRepository.findById(ecritureRequest.getJournal().getId())
                .orElseThrow(() -> new IllegalArgumentException("Journal non trouvé avec l'identifiant : " + ecritureRequest.getJournal().getId()));

        if (existingEcriture.getJournal() != null &&
                !existingEcriture.getJournal().getDossier().getId().equals(newJournal.getDossier().getId())) {
            throw new IllegalArgumentException("Le nouveau journal doit appartenir au même dossier.");
        }

        existingEcriture.setJournal(newJournal);
        existingEcriture.setEntryDate(ecritureRequest.getEntryDate());

        // Update exchange rate information if present in the request
        double exchangeRate = 0;
        boolean hasExchangeRate = false;

        // Check if any of the lines have exchange rate information
        for (Line line : ecritureRequest.getLines()) {
            if (line.getExchangeRate() != null && line.getExchangeRate() > 0) {
                exchangeRate = line.getExchangeRate();
                hasExchangeRate = true;
                break;
            }
        }

        updateEcritureLines(existingEcriture, ecritureRequest.getLines(), hasExchangeRate, exchangeRate);

        // Validate original currency amounts (debit = credit)
        double totalDebit = existingEcriture.getLines().stream()
                .mapToDouble(line -> line.getDebit() != null ? line.getDebit() : 0)
                .sum();
        double totalCredit = existingEcriture.getLines().stream()
                .mapToDouble(line -> line.getCredit() != null ? line.getCredit() : 0)
                .sum();

        if (Math.abs(totalDebit - totalCredit) > 0.01) {
            throw new IllegalArgumentException("La somme du débit doit être égale à la somme du crédit.");
        }

        // If we have exchange rate information, also validate converted amounts
        if (hasExchangeRate) {
            double totalConvertedDebit = existingEcriture.getLines().stream()
                    .mapToDouble(line -> line.getConvertedDebit() != null ? line.getConvertedDebit() : 0)
                    .sum();
            double totalConvertedCredit = existingEcriture.getLines().stream()
                    .mapToDouble(line -> line.getConvertedCredit() != null ? line.getConvertedCredit() : 0)
                    .sum();

            if (Math.abs(totalConvertedDebit - totalConvertedCredit) > 0.01) {
                throw new IllegalArgumentException("La somme du débit converti doit être égale à la somme du crédit converti.");
            }
        }

        return ecritureRepository.save(existingEcriture);
    }

    private void updateEcritureLines(Ecriture existingEcriture, List<Line> updatedLines, boolean hasExchangeRate, double exchangeRate) {
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

        // Get exchange rate information from the first line if available
        String originalCurrency = null;
        String convertedCurrency = null;
        String exchangeRateDate = null;  // Changed to String to match the field type

        // Extract currency information from updated lines if available
        for (Line line : updatedLines) {
            if (line.getOriginalCurrency() != null) {
                originalCurrency = line.getOriginalCurrency();
            }
            if (line.getConvertedCurrency() != null) {
                convertedCurrency = line.getConvertedCurrency();
            }
            if (line.getExchangeRateDate() != null) {
                // Convert LocalDate to String if needed
                if (line.getExchangeRateDate() instanceof LocalDate) {
                    LocalDate date = (LocalDate) line.getExchangeRateDate();
                    exchangeRateDate = date.toString();  // ISO format (yyyy-MM-dd)
                } else {
                    exchangeRateDate = line.getExchangeRateDate().toString();
                }
            }
            if (originalCurrency != null && convertedCurrency != null && exchangeRateDate != null) {
                break;
            }
        }

        // Default values if not provided
        if (originalCurrency == null) originalCurrency = "EUR";
        if (convertedCurrency == null) convertedCurrency = "MAD";
        if (exchangeRateDate == null) exchangeRateDate = LocalDate.now().toString();  // Current date as String

        // Step 2: Add new or update existing lines
        for (Line updatedLine : updatedLines) {
            if (updatedLine.getId() != null) {
                // Update existing line
                Line existingLine = existingLines.stream()
                        .filter(line -> line.getId().equals(updatedLine.getId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Ligne non trouvée avec l'identifiant : " + updatedLine.getId()));

                // Fetch the Account to ensure it is managed
                Account managedAccount = accountRepository.findById(updatedLine.getAccount().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Account non trouvé avec l'identifiant : " + updatedLine.getAccount().getId()));

                existingLine.setAccount(managedAccount);
                existingLine.setLabel(updatedLine.getLabel());
                existingLine.setDebit(updatedLine.getDebit());
                existingLine.setCredit(updatedLine.getCredit());

                // Handle exchange rate information if available
                if (hasExchangeRate) {
                    // Set original amounts (same as regular amounts for now)
                    existingLine.setOriginalDebit(updatedLine.getDebit());
                    existingLine.setOriginalCredit(updatedLine.getCredit());
                    existingLine.setOriginalCurrency(originalCurrency);

                    // Set exchange rate info
                    existingLine.setExchangeRate(exchangeRate);
                    existingLine.setConvertedCurrency(convertedCurrency);
                    existingLine.setExchangeRateDate(exchangeRateDate);  // Now passing a String

                    // Calculate and set converted amounts
                    if (updatedLine.getDebit() != null && updatedLine.getDebit() > 0) {
                        existingLine.setConvertedDebit(updatedLine.getDebit() * exchangeRate);
                    } else {
                        existingLine.setConvertedDebit(0.0);
                    }

                    if (updatedLine.getCredit() != null && updatedLine.getCredit() > 0) {
                        existingLine.setConvertedCredit(updatedLine.getCredit() * exchangeRate);
                    } else {
                        existingLine.setConvertedCredit(0.0);
                    }
                }
            } else {
                // Add a new line
                Account managedAccount = accountRepository.findById(updatedLine.getAccount().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Account non trouvé avec l'identifiant : " + updatedLine.getAccount().getId()));

                Line newLine = new Line();
                newLine.setAccount(managedAccount);
                newLine.setLabel(updatedLine.getLabel());
                newLine.setDebit(updatedLine.getDebit());
                newLine.setCredit(updatedLine.getCredit());
                newLine.setEcriture(existingEcriture);

                // Handle exchange rate information for new lines
                if (hasExchangeRate) {
                    // Set original amounts (same as regular amounts for now)
                    newLine.setOriginalDebit(updatedLine.getDebit());
                    newLine.setOriginalCredit(updatedLine.getCredit());
                    newLine.setOriginalCurrency(originalCurrency);

                    // Set exchange rate info
                    newLine.setExchangeRate(exchangeRate);
                    newLine.setConvertedCurrency(convertedCurrency);
                    newLine.setExchangeRateDate(exchangeRateDate);  // Now passing a String

                    // Calculate and set converted amounts
                    if (updatedLine.getDebit() != null && updatedLine.getDebit() > 0) {
                        newLine.setConvertedDebit(updatedLine.getDebit() * exchangeRate);
                    } else {
                        newLine.setConvertedDebit(0.0);
                    }

                    if (updatedLine.getCredit() != null && updatedLine.getCredit() > 0) {
                        newLine.setConvertedCredit(updatedLine.getCredit() * exchangeRate);
                    } else {
                        newLine.setConvertedCredit(0.0);
                    }
                }

                existingLines.add(newLine);
            }
        }

        // Update the Ecriture with the new list of lines
        existingEcriture.setLines(existingLines);
    }

    @Override
    public List<EcritureExportDTO> exportEcritures(Long dossierId, Long exerciseId, Long journalId, LocalDate startDate, LocalDate endDate) {
        return ecritureRepository.findEcrituresByFilters(dossierId, exerciseId, journalId, startDate, endDate);
    }
}
