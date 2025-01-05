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

            // **Map Account to AccountDTO**
            AccountDTO accountDTO = new AccountDTO();
            if (line.getAccount() != null) {
                accountDTO.setId(line.getAccount().getId());
                accountDTO.setLabel(line.getAccount().getLabel());
                accountDTO.setAccount(line.getAccount().getAccount());
            }
            lineDTO.setAccount(accountDTO); // Attach AccountDTO to LineDTO

            lineDTO.setLabel(line.getLabel());
            lineDTO.setDebit(line.getDebit());
            lineDTO.setCredit(line.getCredit());
            return lineDTO;
        }).collect(Collectors.toList());

        dto.setLines(lineDTOs);

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
                lineDTO.setAccount(accountDTO); // Set account as an object, not as plain string
            }

            lineDTO.setLabel(line.getLabel());
            lineDTO.setDebit(line.getDebit());
            lineDTO.setCredit(line.getCredit());
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

            if (ecriture.getPiece().getDossier() != null) {
                pieceDTO.setDossierName(ecriture.getPiece().getDossier().getName());
                pieceDTO.setDossierId(ecriture.getPiece().getDossier().getId());
            }

            dto.setPiece(pieceDTO);
        }

        return dto;
    }


   /* @Transactional
    @Override
    public Ecriture updateEcriture(Long ecritureId, Ecriture ecritureRequest) {
        // Step 1: Use the custom query to fetch the existing Ecriture
        Ecriture existingEcriture = ecritureRepository.findEcritureByIdCustom(ecritureId)
                .orElseThrow(() -> new IllegalArgumentException("Ecriture non trouvée avec l'identifiant : " + ecritureId));

        // Step 2: Validate and update Journal
        if (existingEcriture.getJournal() == null ||
                !existingEcriture.getJournal().getId().equals(ecritureRequest.getJournal().getId())) {

            Journal newJournal = journalRepository.findById(ecritureRequest.getJournal().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Journal non trouvé avec l'identifiant : " + ecritureRequest.getJournal().getId()));

            // If the existing journal is not null, ensure it belongs to the same Dossier
            if (existingEcriture.getJournal() != null &&
                    !existingEcriture.getJournal().getDossier().getId().equals(newJournal.getDossier().getId())) {
                throw new IllegalArgumentException("Le nouveau journal doit appartenir au même dossier.");
            }

            existingEcriture.setJournal(newJournal);
        }

        // Step 3: Update `entryDate`
        existingEcriture.setEntryDate(ecritureRequest.getEntryDate());
        log.info("existingEcriture ------- {}" ,existingEcriture);
        // Step 4: Update Ecriture Lines
        updateEcritureLines(existingEcriture, ecritureRequest.getLines());

        // Step 5: Validate debit equals credit
        double totalDebit = existingEcriture.getLines().stream()
                .mapToDouble(line -> line.getDebit() != null ? line.getDebit() : 0)
                .sum();

        double totalCredit = existingEcriture.getLines().stream()
                .mapToDouble(line -> line.getCredit() != null ? line.getCredit() : 0)
                .sum();

        if (totalDebit != totalCredit) {
            throw new IllegalArgumentException("La somme du débit des lignes doit être égale à la somme du crédit.");
        }

        // Step 6: Save and return the updated Ecriture
        return ecritureRepository.save(existingEcriture);
    }*/


   /* private void updateEcritureLines(Ecriture existingEcriture, List<Line> updatedLines) {
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

                existingLine.setAccount(updatedLine.getAccount());
                existingLine.setLabel(updatedLine.getLabel());
                existingLine.setDebit(updatedLine.getDebit());
                existingLine.setCredit(updatedLine.getCredit());
            } else {
                // Add a new line
                Line newLine = new Line();
                newLine.setAccount(updatedLine.getAccount());
                newLine.setLabel(updatedLine.getLabel());
                newLine.setDebit(updatedLine.getDebit());
                newLine.setCredit(updatedLine.getCredit());
                newLine.setEcriture(existingEcriture);
                existingLines.add(newLine);
            }
        }
        // Update the Ecriture with the new list of lines
        existingEcriture.setLines(existingLines);
    }*/

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

        updateEcritureLines(existingEcriture, ecritureRequest.getLines());

        double totalDebit = existingEcriture.getLines().stream()
                .mapToDouble(line -> line.getDebit() != null ? line.getDebit() : 0)
                .sum();
        double totalCredit = existingEcriture.getLines().stream()
                .mapToDouble(line -> line.getCredit() != null ? line.getCredit() : 0)
                .sum();

        if (totalDebit != totalCredit) {
            throw new IllegalArgumentException("La somme du débit doit être égale à la somme du crédit.");
        }

        return ecritureRepository.save(existingEcriture);
    }

    private void updateEcritureLines(Ecriture existingEcriture, List<Line> updatedLines) {
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

                // Fetch the Account to ensure it is managed
                Account managedAccount = accountRepository.findById(updatedLine.getAccount().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Account non trouvé avec l'identifiant : " + updatedLine.getAccount().getId()));

                existingLine.setAccount(managedAccount);
                existingLine.setLabel(updatedLine.getLabel());
                existingLine.setDebit(updatedLine.getDebit());
                existingLine.setCredit(updatedLine.getCredit());
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
