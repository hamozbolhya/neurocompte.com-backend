package com.pacioli.core.services.serviceImp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.*;
import com.pacioli.core.repositories.*;
import com.pacioli.core.services.PieceService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        piece.setStatus(PieceStatus.PROCESSING);

        // **Step 6: Save the Piece in the database**
        return pieceRepository.save(piece);
    }

    @Override
    @Transactional
    public Piece saveEcrituresAndFacture(Long pieceId, Long dossierId, String pieceData) {
        // ** Step 1: Fetch the Piece by ID **
        Piece piece = pieceRepository.findById(pieceId)
                .orElseThrow(() -> new IllegalArgumentException("Piece not found for ID: " + pieceId));

        // ** Step 2: Update the amount and status of the Piece **
        piece.setStatus(PieceStatus.PROCESSED);
        piece.setAmount(calculateAmountFromEcritures(pieceData)); // Calculate amount from Ecritures
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
        List<Account> accounts = accountRepository.findByDossierId(dossierId);
        List<Journal> journals = journalRepository.findByDossierId(dossierId);

        for (Ecriture ecriture : deserializeEcritures(pieceData)) {
            ecriture.setPiece(piece);

            Journal journal = journals.stream()
                    .filter(j -> j.getName().equalsIgnoreCase(ecriture.getJournal().getName()))
                    .findFirst().orElse(null);
            ecriture.setJournal(journal);

            Ecriture savedEcriture = ecritureRepository.save(ecriture);

            for (Line line : ecriture.getLines()) {
                line.setEcriture(savedEcriture);

                Account account = accounts.stream()
                        .filter(a -> a.getAccount().equals(line.getAccount().getAccount()))
                        .findFirst().orElse(null);
                line.setAccount(account);
            }

            lineRepository.saveAll(ecriture.getLines());
        }
    }

    private List<Ecriture> deserializeEcritures(String pieceData) {
        try {
            // Extract 'ecritures' from the JSON body
            JsonNode rootNode = objectMapper.readTree(pieceData);

            // Extract the 'ecritures' field from the main pieceData
            JsonNode ecrituresNode = rootNode.get("ecritures");

            // Deserialize the 'ecritures' field as a List<Ecriture>
            return objectMapper.readValue(ecrituresNode.toString(), new TypeReference<List<Ecriture>>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse 'ecritures' JSON: " + e.getMessage());
        }
    }


    private Double calculateAmountFromEcritures(String pieceData) {
        List<Ecriture> ecritures = deserializeEcritures(pieceData);
        return ecritures.stream()
                .flatMap(e -> e.getLines().stream())
                .mapToDouble(Line::getDebit)
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

