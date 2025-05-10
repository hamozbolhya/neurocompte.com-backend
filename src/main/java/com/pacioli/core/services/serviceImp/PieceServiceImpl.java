package com.pacioli.core.services.serviceImp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacioli.core.DTO.EcrituresDTO2;
import com.pacioli.core.DTO.FactureDataDTO;
import com.pacioli.core.DTO.PieceDTO;
import com.pacioli.core.DTO.PieceStatsDTO;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.*;
import com.pacioli.core.repositories.*;
import com.pacioli.core.services.PieceService;
import com.pacioli.core.utils.InMemoryMultipartFile;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
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

    @Value("${app.ai.api-url}")
    private String aiApiBaseUrl;

    @Value("${ai.service.api-key}")
    private String aiApiKey;

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
    public Piece savePiece(String pieceData, MultipartFile file, Long dossierId, String country) throws IOException {
        try {
            // Step 1: Deserialize the Piece
            Piece piece = deserializePiece(pieceData, dossierId);
            log.info("Piece deserialized: {}", piece.getOriginalFileName());

            // Step 2: Fetch Dossier from the database
            Dossier dossier = dossierRepository.findById(dossierId)
                    .orElseThrow(() -> new IllegalArgumentException("Dossier not found for ID: " + dossierId));
            log.info("Dossier found with ID: {}", dossierId);

            // Step 3: Link Dossier to Piece
            piece.setDossier(dossier);
            piece.setOriginalFileName(piece.getOriginalFileName());
            piece.setUploadDate(new Date());
            // Generate a single formatted filename with UUID for all pages
            String originalFilename = piece.getFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
            String uuid = UUID.randomUUID().toString();
            String formattedFilename = uuid + "." + extension;

            // Step 4: Check file type and handle conversion if needed
            boolean isPdf = isPdfFile(file);
            log.info("Is this a PDF file? {}", isPdf);

            if (isPdf) {
                log.info("PDF file detected, converting first page for storage");

                try {
                    // Convert PDF to images but only use first page for processing
                    List<MultipartFile> convertedImages = convertPdfToImages(file);
                    log.info("Conversion complete. Number of images: {}", convertedImages.size());

                    if (!convertedImages.isEmpty()) {
                        // Format the filename (just using UUID, not page specific)
                        formattedFilename = uuid + ".png";

                        sendFileToAI(convertedImages.get(0), formattedFilename, dossierId, country);

                        // Save first page as thumbnail/preview
                        saveFileToDisk(convertedImages.get(0), formattedFilename);
                        piece.setFilename(formattedFilename);
                        piece.setStatus(PieceStatus.UPLOADED);

                        Piece savedPiece = pieceRepository.save(piece);
                        log.info("Piece saved with ID: {}", savedPiece.getId());
                        return savedPiece;
                    } else {
                        log.warn("No images were converted from the PDF, falling back to save as-is");
                    }
                } catch (Exception e) {
                    log.error("Error during PDF conversion: {}", e.getMessage(), e);
                    log.info("Falling back to saving the PDF as-is");
                }
            }

            // Not a PDF or conversion failed - save as-is and send to AI
            log.info("Processing as regular file");

            // Send the file to AI (just once)
            sendFileToAI(file, formattedFilename, dossierId, country);

            saveFileToDisk(file, formattedFilename);
            piece.setFilename(formattedFilename);
            piece.setStatus(PieceStatus.UPLOADED);

            Piece savedPiece = pieceRepository.save(piece);
            log.info("Piece saved with ID: {}", savedPiece.getId());
            return savedPiece;

        } catch (Exception e) {
            log.error("Error in savePiece: {}", e.getMessage(), e);
            throw new IOException("Failed to save piece", e);
        }
    }
    private boolean isPdfFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();

        log.info("Checking if file is PDF. Filename: {}, Content Type: {}", filename, contentType);

        boolean hasExtension = (filename != null && filename.toLowerCase().endsWith(".pdf"));
        boolean hasContentType = (contentType != null &&
                (contentType.equals("application/pdf") ||
                        contentType.equals("application/x-pdf")));

        log.info("Has PDF extension: {}, Has PDF content type: {}", hasExtension, hasContentType);

        // Also try to peek at first few bytes if content type and extension checks fail
        if (!hasExtension && !hasContentType) {
            try {
                byte[] header = new byte[5];
                file.getInputStream().read(header);
                String headerStr = new String(header);
                boolean hasPdfHeader = headerStr.startsWith("%PDF-");
                log.info("Has PDF header: {}", hasPdfHeader);

                // Reset input stream for future readers
                file.getInputStream().reset();

                return hasPdfHeader;
            } catch (Exception e) {
                log.warn("Error checking file header: {}", e.getMessage());
            }
        }

        return hasExtension || hasContentType;
    }

    private List<MultipartFile> convertPdfToImages(MultipartFile pdfFile) throws IOException {
        List<MultipartFile> imageFiles = new ArrayList<>();

        try {
            // Log that we're starting conversion
            log.info("Starting PDF conversion for file: {}", pdfFile.getOriginalFilename());

            PDDocument document = PDDocument.load(pdfFile.getInputStream());
            log.info("PDF loaded successfully. Number of pages: {}", document.getNumberOfPages());

            PDFRenderer pdfRenderer = new PDFRenderer(document);

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                log.info("Processing page {}", (page + 1));

                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
                log.info("Page {} rendered. Image dimensions: {}x{}",
                        (page + 1), image.getWidth(), image.getHeight());

                ByteArrayOutputStream imageStream = new ByteArrayOutputStream();
                ImageIO.write(image, "png", imageStream);

                byte[] imageBytes = imageStream.toByteArray();
                log.info("Page {} converted to PNG. Size: {} bytes", (page + 1), imageBytes.length);

                String imageName = "page-" + (page + 1) + ".png";

                MultipartFile imageFile = new InMemoryMultipartFile(
                        imageName,
                        imageName,
                        "image/png",
                        imageBytes
                );

                imageFiles.add(imageFile);
                log.info("Added image file to list: {}", imageName);
            }

            document.close();
            log.info("PDF conversion completed. Total images: {}", imageFiles.size());
        } catch (Exception e) {
            log.error("Error converting PDF to images: {}", e.getMessage(), e);
            throw new IOException("Failed to convert PDF to images", e);
        }

        return imageFiles;
    }

    private void sendFileToAI(MultipartFile file, String filename, Long dossierID, String country) {
        try {
            log.info("============ START AI FILE UPLOAD TRACE ============");
            log.info("File details:");
            log.info("  - Original filename: {}", file.getOriginalFilename());
            log.info("  - Content type: {}", file.getContentType());
            log.info("  - Size: {} bytes", file.getSize());

            // Get necessary data for the API call
            String bucket = "invoice";
            String stage = "dev";
            String dossierId = String.valueOf(dossierID);

            try {
                // Extract UUID from filename
                String uuid = filename;
                if (filename.contains(".")) {
                    uuid = filename.substring(0, filename.lastIndexOf('.'));
                }

                // Build the base URL
                String baseUrl = aiApiBaseUrl;
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }

                // Keep the %2F format as before but eliminate page suffixes
                String urlPath = baseUrl + "/" + stage + "/" + bucket + "/" + dossierId;
                //old --> String urlPath = baseUrl + "/" + stage + "/" + bucket + "/" + country + dossierId;
                String finalUrl = urlPath + "%2F" + filename;  // Keep using %2F as it was working before

                log.info("Complete API URL: {}", finalUrl);

                // Set headers and send the request
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", file.getContentType());
                headers.put("x-api-key", aiApiKey);

                byte[] fileBytes = file.getBytes();
                log.info("File byte array size: {} bytes", fileBytes.length);

                URL url = new URL(finalUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("PUT");
                connection.setDoOutput(true);

                // Set headers
                headers.forEach(connection::setRequestProperty);

                // Write the file data
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(fileBytes);
                }

                // Get response
                int responseCode = connection.getResponseCode();
                log.info("Response code: {}", responseCode);

                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(
                                responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }

                if (responseCode == 200) {
                    log.info("File successfully sent to AI: {}", filename);
                } else {
                    log.warn("AI service responded with non-OK status: {} - {}", responseCode, response.toString());
                }

            } catch (IOException e) {
                log.error("Error constructing or opening URL connection: {}", e.getMessage(), e);
            }

            log.info("============ END AI FILE UPLOAD TRACE ============");

        } catch (Exception e) {
            log.error("Error in sendFileToAI: {}", e.getMessage(), e);
        }
    }

    private void saveFileToDisk(MultipartFile file, String formattedFilename) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath); // Create directory if it doesn't exist
        }

        // **DO NOT MODIFY THE FILE NAME - SAVE AS-IS**
        Path filePath = uploadPath.resolve(formattedFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING); // Copy the file to disk
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
            // Attach FactureData to the existing Piece
            factureData.setPiece(piece);

            // Try to extract Invoice Date from the AI data
            try {
                // Parse the pieceData JSON to extract the Date field
                JsonNode rootNode = objectMapper.readTree(pieceData);
                JsonNode ecrituresNode = rootNode.get("ecritures");

                // Check for case variations of "ecritures"
                if (ecrituresNode == null) {
                    ecrituresNode = rootNode.get("Ecritures");
                }

                if (ecrituresNode != null && ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                    // Get the first entry's Date field
                    JsonNode firstEntry = ecrituresNode.get(0);
                    if (firstEntry != null && firstEntry.has("Date")) {
                        String dateStr = firstEntry.get("Date").asText();
                        log.info("Found date in AI data: {}", dateStr);

                        // Convert string date to Date object
                        try {
                            LocalDate localDate = parseDate(dateStr);
                            Date invoiceDate = java.sql.Date.valueOf(localDate);
                            factureData.setInvoiceDate(invoiceDate);
                            log.info("Set invoice date to: {}", invoiceDate);
                        } catch (Exception e) {
                            log.error("Failed to convert date string to Date object: {}", e.getMessage(), e);
                        }
                    } else {
                        log.warn("First entry does not have a Date field");
                    }
                } else {
                    log.warn("No ecritures found in the data or empty ecritures array");
                }
            } catch (Exception e) {
                log.error("Error extracting invoice date from AI data: {}", e.getMessage(), e);
            }

            // Set ICE from dossier if not already set
            if (factureData.getIce() == null || factureData.getIce().isEmpty()) {
                try {
                    Dossier dossier = piece.getDossier();
                    if (dossier != null && dossier.getICE() != null) {
                        factureData.setIce(dossier.getICE());
                        log.info("Set ICE from dossier: {}", dossier.getICE());
                    }
                } catch (Exception e) {
                    log.error("Failed to set ICE from dossier: {}", e.getMessage(), e);
                }
            }

            // Copy currency conversion information from the piece if available
            if (piece.getExchangeRate() != null && factureData.getExchangeRate() == null) {
                factureData.setExchangeRate(piece.getExchangeRate());
                factureData.setOriginalCurrency(piece.getAiCurrency());
                factureData.setConvertedCurrency(piece.getConvertedCurrency());
                factureData.setExchangeRateDate(piece.getExchangeRateDate());

                // If we have exchange rate but no converted amounts, calculate them now
                double rate = factureData.getExchangeRate();

                if (factureData.getTotalTTC() != null && factureData.getConvertedTotalTTC() == null) {
                    factureData.setConvertedTotalTTC(factureData.getTotalTTC() * rate);
                }

                if (factureData.getTotalHT() != null && factureData.getConvertedTotalHT() == null) {
                    factureData.setConvertedTotalHT(factureData.getTotalHT() * rate);
                }

                if (factureData.getTotalTVA() != null && factureData.getConvertedTotalTVA() == null) {
                    factureData.setConvertedTotalTVA(factureData.getTotalTVA() * rate);
                }
            }

            // Log FactureData before saving
            log.info("Saving FactureData: invoiceNumber={}, invoiceDate={}, ice={}",
                    factureData.getInvoiceNumber(), factureData.getInvoiceDate(), factureData.getIce());

            factureDataRepository.save(factureData); // Save FactureData
        } else {
            log.warn("No FactureData found in the pieceData");
        }
    }

    // Helper method to parse dates from various formats
    private LocalDate parseDate(String dateStr) {
        try {
            log.info("Parsing date string: {}", dateStr);

            // Handle dd/MM/yyyy format
            if (dateStr.contains("/")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                return LocalDate.parse(dateStr, formatter);
            }
            // Handle yyyy-MM-dd format
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.error("Date parsing failed for: {}. Error: {}", dateStr, e.getMessage(), e);
            return LocalDate.now();
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

    @Transactional
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

                // Modify just the account creation part in your saveEcrituresForPiece method:

// Replace the current account handling code with this:
                for (Line line : ecriture.getLines()) {
                    line.setEcriture(savedEcriture);

                    // Get account number from the line
                    String accountNumber = line.getAccount().getAccount();

                    // Check if we already have it in our in-memory map
                    Account account = accountMap.get(accountNumber);

                    if (account == null) {
                        // Not in memory, try to get from database
                        account = accountRepository.findByAccountAndDossierId(accountNumber, dossier.getId());

                        if (account == null) {
                            // Account doesn't exist yet, try to create it with error handling
                            try {
                                // Create new account
                                Account newAccount = new Account();
                                newAccount.setAccount(accountNumber);
                                newAccount.setLabel(line.getAccount().getLabel());
                                newAccount.setDossier(dossier);
                                newAccount.setJournal(journal);
                                newAccount.setHasEntries(true);

                                log.info("Creating new Account: {}", newAccount);
                                account = accountRepository.save(newAccount);

                                // Add to our map for future lookups
                                accountMap.put(accountNumber, account);
                            } catch (DataIntegrityViolationException e) {
                                // Another thread/process created the account just before us
                                log.info("Account creation conflict detected. Retrying fetch for account: {}", accountNumber);

                                // Sleep briefly to allow the transaction to complete
                                try {
                                    Thread.sleep(50);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }

                                // Try to fetch the account again
                                account = accountRepository.findByAccountAndDossierId(accountNumber, dossier.getId());

                                if (account == null) {
                                    // This should be very rare - means we still can't find it after waiting
                                    throw new RuntimeException("Failed to find or create account after concurrent creation: " + accountNumber, e);
                                }

                                // Add to our map for future lookups
                                accountMap.put(accountNumber, account);
                            }
                        } else {
                            // Found in database, add to map
                            accountMap.put(accountNumber, account);
                        }
                    }

                    // Set the account to the line
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

    @Override
    public List<Piece> getPiecesByDossierIdSortedByDate(Long dossierId) {
        return pieceRepository.findByDossierIdWithDetailsOrderByUploadDateDesc(dossierId);
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

    @Override
    @Transactional
    public PieceStatsDTO getPieceStatsByDossier(Long dossierId) {
        // First try to get the stats using the custom query
        PieceStatsDTO stats = pieceRepository.getPieceStatsByDossierId(dossierId);

        // If no pieces exist for the dossier, create a stats object with zero counts
        if (stats == null) {
            Optional<Dossier> dossierOpt = dossierRepository.findById(dossierId);
            if (dossierOpt.isPresent()) {
                Dossier dossier = dossierOpt.get();
                stats = new PieceStatsDTO();
                stats.setDossierId(dossier.getId());
                stats.setDossierName(dossier.getName());
                stats.setTotalCount(0L);
                stats.setUploadedCount(0L);
                stats.setProcessedCount(0L);
                stats.setRejectedCount(0L);
                stats.setProcessingCount(0L);  // New line

                // Set currency and country if available
                if (dossier.getCountry() != null) {
                    stats.setCountryCode(dossier.getCountry().getCode());
                    if (dossier.getCountry().getCurrency() != null) {
                        stats.setDossierCurrency(dossier.getCountry().getCurrency().getCode());
                    }
                }
            }
        }

        return stats;
    }

    @Override
    @Transactional
    public List<PieceStatsDTO> getPieceStatsByCabinet(Long cabinetId) {
        return pieceRepository.getPieceStatsByCabinetId(cabinetId);
    }

}

