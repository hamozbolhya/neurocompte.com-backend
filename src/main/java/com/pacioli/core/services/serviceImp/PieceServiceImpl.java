package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.*;
import com.pacioli.core.repositories.*;
import com.pacioli.core.services.PieceService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PieceServiceImpl implements PieceService {
    @Autowired
    private EntityManager entityManager; // Inject the entity manager
    private final PieceRepository pieceRepository;
    private final FactureDataRepository factureDataRepository;
    private final EcritureRepository ecritureRepository;
    private final LineRepository lineRepository;
    private final JournalRepository journalRepository;
    private final AccountRepository accountRepository;

    public PieceServiceImpl(
            PieceRepository pieceRepository,
            FactureDataRepository factureDataRepository,
            EcritureRepository ecritureRepository, LineRepository lineRepository, JournalRepository journalRepository, AccountRepository accountRepository) {
        this.pieceRepository = pieceRepository;
        this.factureDataRepository = factureDataRepository;
        this.ecritureRepository = ecritureRepository;
        this.lineRepository = lineRepository;
        this.journalRepository = journalRepository;
        this.accountRepository = accountRepository;
    }


    @Transactional
    public Piece savePiece(Piece piece) {

        if (piece.getDossier() == null) {
            throw new IllegalArgumentException("Dossier cannot be null when saving a Piece.");
        }

        // **Fetch Accounts for the Dossier** (Step 1)
        List<Account> dossierAccounts = accountRepository.findByDossierId(piece.getDossier().getId());
        Map<String, Account> accountMap = dossierAccounts.stream()
                .collect(Collectors.toMap(Account::getAccount, account -> account)); // Map by 'account' (4011, 4015, etc.)

        // Temporarily detach or remove transient entities
        List<Ecriture> ecritures = piece.getEcritures();
        piece.setEcritures(null); // Detach Ecritures temporarily

        FactureData factureData = piece.getFactureData();
        piece.setFactureData(null); // Detach FactureData temporarily

        // Save the Piece
        Piece savedPiece = pieceRepository.save(piece);

        // Re-associate and save FactureData if present
        if (factureData != null) {
            factureData.setPiece(savedPiece);
            factureDataRepository.save(factureData);
        }

        // **Re-associate and save Ecritures and Lines** (Step 2)
        if (ecritures != null && !ecritures.isEmpty()) {
            for (Ecriture ecriture : ecritures) {
                ecriture.setPiece(savedPiece); // Link Ecriture to saved Piece

                try {
                    // **Fetch Journal**
                    if (ecriture.getJournal() != null && ecriture.getJournal().getName() != null) {
                        Journal journal = journalRepository
                                .findByNameAndDossierId(ecriture.getJournal().getName(), piece.getDossier().getId())
                                .orElse(null); // Don't throw error, just continue
                        ecriture.setJournal(journal);
                    }
                } catch (Exception e) {
                    log.info("Journal not found for Ecriture. Details: {}" , e.getMessage());
                    ecriture.setJournal(null); // Leave the journal as null
                }

                // Save Ecriture
                Ecriture savedEcriture = ecritureRepository.save(ecriture);

                // **Link Accounts in Lines** (Step 3)
                if (ecriture.getLines() != null && !ecriture.getLines().isEmpty()) {
                    for (Line line : ecriture.getLines()) {
                        line.setEcriture(savedEcriture); // Link Line to saved Ecriture

                        try {
                            if (line.getAccount() != null && line.getAccount().getAccount() != null) {
                                // Get account name from incoming request
                                String accountName = line.getAccount().getAccount();
                                Account matchedAccount = accountMap.get(accountName); // Lookup in the map

                                if (matchedAccount != null) {
                                    line.setAccount(matchedAccount); // Associate the line with the correct account
                                } else {
                                    log.info("Account not found for Line. Account: {}" , accountName);
                                    line.setAccount(null); // Leave the account as null
                                }
                            }
                        } catch (Exception e) {
                            log.info("Error while matching account for Line. Details: {}" , e.getMessage());
                            line.setAccount(null);
                        }
                    }
                    lineRepository.saveAll(ecriture.getLines());
                }
            }
        }

        return savedPiece;
    }



    private void attachAccountsToLines(List<Line> lines, Long dossierId) {
        List<String> accountNames = lines.stream()
                .map(line -> line.getAccount() != null ? line.getAccount().getAccount() : null)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<Account> accounts = accountRepository.findByAccountInAndDossierId(accountNames, dossierId);
        Map<String, Account> accountMap = accounts.stream()
                .collect(Collectors.toMap(Account::getAccount, account -> account));

        for (Line line : lines) {
            if (line.getAccount() != null && line.getAccount().getAccount() != null) {
                Account existingAccount = accountMap.get(line.getAccount().getAccount());
                line.setAccount(existingAccount);
            }
        }
    }





    @Override
    public List<Piece> getPiecesByDossier(Long dossierId) {
        return pieceRepository.findByDossierId(dossierId);
    }

    @Override
    public List<Piece> getAllPieces() {
        return pieceRepository.findAll();
    }

    @Override
    @Transactional
    public void deletePiece(Long id) {
        pieceRepository.deleteById(id);
    }

    @Override
    public Piece getPieceById(Long id) {
        return pieceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Piece with id " + id + " not found"));
    }

}

