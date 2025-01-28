package com.pacioli.core.services.serviceImp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacioli.core.DTO.EcrituresDTO2;
import com.pacioli.core.DTO.FactureDataDTO;
import com.pacioli.core.DTO.PieceDTO;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.*;
import com.pacioli.core.repositories.*;
import com.pacioli.core.services.PieceService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

@Slf4j
@Service
public class PieceServiceImpl implements PieceService {
    @Autowired
    private final PieceRepository pieceRepository;
    private final FactureDataRepository factureDataRepository;
    private final EcritureRepository ecritureRepository;
    private final LineRepository lineRepository;
    private final JournalRepository journalRepository;
    private final AccountRepository accountRepository;
    private final DossierRepository dossierRepository;

    private final ObjectMapper objectMapper;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Value("${file.upload.dir:Files/}")
    private String uploadDir;

    public PieceServiceImpl(
            PieceRepository pieceRepository,
            FactureDataRepository factureDataRepository,
            EcritureRepository ecritureRepository, LineRepository lineRepository, JournalRepository journalRepository,
            AccountRepository accountRepository, ObjectMapper objectMapper, DossierRepository dossierRepository) {
        this.pieceRepository = pieceRepository;
        this.factureDataRepository = factureDataRepository;
        this.ecritureRepository = ecritureRepository;
        this.lineRepository = lineRepository;
        this.journalRepository = journalRepository;
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper;
        this.dossierRepository = dossierRepository;
    }

    @Override
    @Transactional
    public Piece savePiece(String pieceData, MultipartFile file, Long dossierId) throws IOException {
        // **Step 1: Deserialize the Piece**
        Piece piece = deserializePiece(pieceData, dossierId);

        // **Step 2: Fetch Dossier from the database**
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new IllegalArgumentException("Dossier not found for ID: " + dossierId));

        // **Step 3: Link Dossier to Piece**
        piece.setDossier(dossier); // Attach the dossier to the piece
        piece.setOriginalFileName(piece.getOriginalFileName());
        // **Step 4: Use the formatted file name from the request (DO NOT CHANGE IT)**
        String formattedFilename = piece.getFilename(); // Use the filename sent from the frontend
        saveFileToDisk(file, formattedFilename); // Save file with the formatted filename
        piece.setFilename(formattedFilename); // Store the exact formatted filename in the Piece entity

        // **Step 5: Set status to PROCESSING**
        piece.setStatus(PieceStatus.UPLOADED);

        // **Step 6: Save the Piece in the database**
        return pieceRepository.save(piece);
    }

    @Override
    @Transactional
    public Piece saveEcrituresAndFacture(Long pieceId, Long dossierId, String pieceData) {
        // ** Step 1: Fetch the Piece by ID **
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new IllegalArgumentException("Dossier not found for ID: " + dossierId));
        Piece piece = pieceRepository.findById(pieceId)
                .orElseThrow(() -> new IllegalArgumentException("Piece not found for ID: " + pieceId));

        // ** Step 2: Update the amount and status of the Piece **
        piece.setStatus(PieceStatus.PROCESSED);
        piece.setAmount(calculateAmountFromEcritures(pieceData, dossier)); // Calculate amount from Ecritures
        piece = pieceRepository.save(piece); // Save changes to the Piece

        // ** Step 3: Save Ecritures and FactureData for the Piece **
        saveFactureDataForPiece(piece, pieceData);
        saveEcrituresForPiece(piece, dossierId, pieceData);

        return piece;
    }

    private void saveFactureDataForPiece(Piece piece, String pieceData) {
        FactureData factureData = deserializeFactureData(pieceData);

        if (factureData != null) {
            factureData.setPiece(piece); // Attach FactureData to the existing Piece
            factureDataRepository.save(factureData); // Save FactureData
        }
    }

    private FactureData deserializeFactureData(String pieceData) {
        try {
            // Parse the entire pieceData JSON to get the factureData part only
            JsonNode rootNode = objectMapper.readTree(pieceData);
            JsonNode factureDataNode = rootNode.get("factureData"); // Extract only "factureData"

            if (factureDataNode != null && !factureDataNode.isNull()) {
                return objectMapper.treeToValue(factureDataNode, FactureData.class); // Convert JSON node to FactureData
            } else {
                return null; // If factureData is not present, return null
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse 'factureData' JSON: " + e.getMessage());
        }
    }

    private void saveEcrituresForPiece(Piece piece, Long dossierId, String pieceData) {
        log.info("Processing Piece ID: {}, Dossier ID: {}", piece.getId(), dossierId);

        // Fetch the Dossier explicitly
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new IllegalArgumentException("Dossier not found for ID: " + dossierId));
        log.info("Fetched Dossier ID: {}", dossier.getId());

        // Fetch existing Accounts and Journals for the Dossier
        Map<String, Account> accountMap = accountRepository.findByDossierId(dossierId).stream()
                .collect(Collectors.toMap(Account::getAccount, Function.identity()));
        List<Journal> journals = journalRepository.findByDossierId(dossierId);

        for (Ecriture ecriture : deserializeEcritures(pieceData, dossier)) {
            ecriture.setPiece(piece);

            // Find or create Journal
            Journal journal = journals.stream()
                    .filter(j -> j.getName().equalsIgnoreCase(ecriture.getJournal().getName()))
                    .findFirst()
                    .orElseGet(() -> {
                        log.info("Creating new Journal: {}", ecriture.getJournal().getName());
                        Journal newJournal = new Journal(
                                ecriture.getJournal().getName(),
                                ecriture.getJournal().getType(),
                                dossier.getCabinet(),
                                dossier
                        );
                        Journal savedJournal = journalRepository.save(newJournal);
                        journals.add(savedJournal); // Add to the list to prevent redundant creations
                        return savedJournal;
                    });
            ecriture.setJournal(journal);

            log.info("Ecriture before saving: {}", ecriture);

            try {
                log.info("Attempting to save Ecriture: {}", ecriture);
                Ecriture savedEcriture = ecritureRepository.save(ecriture);
                log.info("Saved Ecriture: {}", savedEcriture);

                for (Line line : ecriture.getLines()) {
                    line.setEcriture(savedEcriture);

                    // Fetch or create Account
                    Account account = accountMap.computeIfAbsent(line.getAccount().getAccount(), accountNumber -> {
                        log.info("Checking Account for account number: {}", accountNumber);

                        // Fetch from the database
                        Account existingAccount = accountRepository.findByAccountAndDossierId(accountNumber, dossier.getId());
                        if (existingAccount != null) {
                            log.info("Matched existing Account: {}", existingAccount);
                            return existingAccount;
                        }

                        // Create new Account
                        Account newAccount = new Account();
                        newAccount.setAccount(accountNumber);
                        newAccount.setLabel(line.getAccount().getLabel());
                        newAccount.setDossier(dossier); // Explicitly set the Dossier
                        log.info("New Account Details before saving: {}", newAccount);
                        return accountRepository.save(newAccount);
                    });

                    // Set the matched or newly created account to the line
                    line.setAccount(account);
                }

                // Save Lines associated with the Ecriture
                lineRepository.saveAll(ecriture.getLines());
            } catch (Exception e) {
                log.error("Error saving Ecriture: {}", e.getMessage(), e);
                throw e; // Rethrow to propagate the exception
            }
        }
    }


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

                if (ecriture.getLines() != null) {
                    for (Line line : ecriture.getLines()) {
                        Account account = line.getAccount();

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
                                accountMap.put(account.getAccount(), account); // Add to map
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



    private Double calculateAmountFromEcritures(String pieceData, Dossier dossier) {
        List<Ecriture> ecritures = deserializeEcritures(pieceData, dossier);

        return ecritures.stream()
                .flatMap(e -> e.getLines().stream())
                .flatMapToDouble(line -> DoubleStream.of(line.getDebit(), line.getCredit())) // Include both Debit and Credit
                .max()
                .orElse(0.0);
    }




    /**
     * Deserializes the Piece from the provided data.
     */
    private Piece deserializePiece(String pieceData, Long dossierId) {
        Piece piece;
        try {
            piece = objectMapper.readValue(pieceData, Piece.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse 'piece' JSON data: " + e.getMessage());
        }

        // Attach the dossier to the Piece object
        Dossier dossier = new Dossier();
        dossier.setId(dossierId); // Set the dossier ID properly
        piece.setDossier(dossier); // Attach the Dossier to the Piece object

        return piece;
    }

    /**
     * Saves a file to the disk and returns the unique filename.
     */
    private void saveFileToDisk(MultipartFile file, String formattedFilename) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath); // Create directory if it doesn't exist
        }

        // **DO NOT MODIFY THE FILE NAME - SAVE AS-IS**
        Path filePath = uploadPath.resolve(formattedFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING); // Copy the file to disk
    }

    /**
     * Generates a unique filename to avoid collisions.
     */
    private String generateUniqueFilename(String filename, Path uploadPath) {
        String baseName = FilenameUtils.getBaseName(filename);
        String extension = FilenameUtils.getExtension(filename);
        String uniqueFilename = filename;

        int count = 1;
        while (Files.exists(uploadPath.resolve(uniqueFilename))) {
            uniqueFilename = baseName + "_pacioli_" + count + "." + extension;
            count++;
        }
        return uniqueFilename;
    }

    @Override
    @Transactional()
    public List<Piece> getPiecesByDossier(Long dossierId) {
        List<Piece> pieces = pieceRepository.findByDossierId(dossierId);
        messagingTemplate.convertAndSend("/topic/dossier-pieces/" + dossierId, pieces);
        return pieces;
    }

    @Transactional()
    public void notifyPiecesUpdate(Long dossierId) {
        List<Piece> pieces = pieceRepository.findByDossierId(dossierId);

        // Eagerly load collections before leaving the transaction
        pieces.forEach(piece -> {
            Hibernate.initialize(piece.getEcritures());
            Hibernate.initialize(piece.getFactureData());
        });

        List<PieceDTO> dtos = pieces.stream()
                .map(piece -> {
                    PieceDTO dto = new PieceDTO();
                    dto.setId(piece.getId());
                    dto.setFilename(piece.getFilename());
                    dto.setType(piece.getType());
                    dto.setStatus(piece.getStatus());
                    dto.setUploadDate(piece.getUploadDate());
                    dto.setAmount(piece.getAmount());
                    dto.setDossierId(piece.getDossier().getId());
                    dto.setDossierName(piece.getDossier().getName());

                    if (piece.getFactureData() != null) {
                        FactureDataDTO factureDataDTO = new FactureDataDTO();
                        factureDataDTO.setInvoiceNumber(piece.getFactureData().getInvoiceNumber());
                        factureDataDTO.setTotalTVA(piece.getFactureData().getTotalTVA());
                        factureDataDTO.setTaxRate(piece.getFactureData().getTaxRate());
                        dto.setFactureData(factureDataDTO);
                    }

                    if (piece.getEcritures() != null) {
                        List<EcrituresDTO2> ecrituresDTOs = piece.getEcritures().stream()
                                .map(ecriture -> {
                                    EcrituresDTO2 dto2 = new EcrituresDTO2();
                                    dto2.setUniqueEntryNumber(ecriture.getUniqueEntryNumber());
                                    dto2.setEntryDate(ecriture.getEntryDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                                    return dto2;
                                })
                                .collect(Collectors.toList());
                        dto.setEcritures(ecrituresDTOs);
                    }

                    return dto;
                })
                .collect(Collectors.toList());

        messagingTemplate.convertAndSend("/topic/dossier-pieces/" + dossierId, dtos);
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

    @Transactional
    public Piece updatePieceStatus(Long pieceId, String newStatus) {
        Optional<Piece> optionalPiece = pieceRepository.findById(pieceId);
        if (optionalPiece.isEmpty()) {
            log.warn("Piece with id {} not found", pieceId);
            return null;
        }

        Piece piece = optionalPiece.get();

        try {
            // Convert string to Enum
            PieceStatus status = PieceStatus.valueOf(newStatus.toUpperCase());

            // Update the status
            piece.setStatus(status);

            // Save the piece
            Piece updatedPiece = pieceRepository.save(piece);

            log.info("Successfully updated status for piece with id {} to {}", pieceId, status);

            return updatedPiece;

        } catch (IllegalArgumentException e) {
            log.error("Invalid status value: {}", newStatus);
            throw new IllegalArgumentException("Invalid status value: " + newStatus);
        }
    }

}

