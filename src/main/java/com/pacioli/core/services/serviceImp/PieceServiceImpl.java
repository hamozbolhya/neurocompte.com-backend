package com.pacioli.core.services.serviceImp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacioli.core.DTO.*;
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
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Autowired
    private DuplicateDetectionService duplicateDetectionService;

    private final ObjectMapper objectMapper;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Value("${file.upload.dir:Files/}")
    private String uploadDir;

    @Value("${ai.service.url}")
    private String aiApiBaseUrl;

    @Value("${ai.service.api-key}")
    private String aiApiKey;

    public PieceServiceImpl(PieceRepository pieceRepository, FactureDataRepository factureDataRepository, EcritureRepository ecritureRepository, LineRepository lineRepository, JournalRepository journalRepository, AccountRepository accountRepository, ObjectMapper objectMapper, DossierRepository dossierRepository) {
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

            // Step 2: Check for technical duplicates BEFORE processing
            //Optional<Piece> technicalDuplicate = duplicateDetectionService.checkTechnicalDuplicate(dossierId, piece.getOriginalFileName());

         /*   if (technicalDuplicate.isPresent()) {
                log.warn("🚫 Technical duplicate detected, marking as duplicate");
                duplicateDetectionService.markAsDuplicate(piece, technicalDuplicate.get());

                // Still save the piece but with DUPLICATE status
                piece.setUploadDate(new Date());
                piece.setStatus(PieceStatus.DUPLICATE);

                Piece savedPiece = pieceRepository.save(piece);
                log.info("Duplicate piece saved with ID: {}", savedPiece.getId());
                return savedPiece;
            }*/

            // Step 3: Fetch Dossier from the database
            Dossier dossier = dossierRepository.findById(dossierId).orElseThrow(() -> new IllegalArgumentException("Dossier not found for ID: " + dossierId));
            log.info("Dossier found with ID: {}", dossierId);

            // Step 4: Link Dossier to Piece
            piece.setDossier(dossier);
            piece.setOriginalFileName(piece.getOriginalFileName());
            piece.setUploadDate(new Date());
            piece.setIsDuplicate(false); // Initialize as not duplicate

            // Generate a single formatted filename with UUID for all pages
            String originalFilename = piece.getFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
            String uuid = UUID.randomUUID().toString();
            String formattedFilename = uuid + "." + extension;

            // Step 5: Check file type and handle conversion if needed
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
        boolean hasContentType = (contentType != null && (contentType.equals("application/pdf") || contentType.equals("application/x-pdf")));

        log.info("Has PDF extension: {}, Has PDF content type: {}", hasExtension, hasContentType);

        // Only check header if both filename and content-type checks fail
        if (!hasExtension && !hasContentType) {
            try {
                // Use a BufferedInputStream to support mark/reset
                InputStream inputStream = file.getInputStream();
                if (!inputStream.markSupported()) {
                    inputStream = new BufferedInputStream(inputStream);
                }

                inputStream.mark(5); // Mark the position
                byte[] header = new byte[5];
                int bytesRead = inputStream.read(header);
                inputStream.reset(); // Reset to the marked position

                if (bytesRead >= 5) {
                    String headerStr = new String(header);
                    boolean hasPdfHeader = headerStr.startsWith("%PDF-");
                    log.info("Has PDF header: {}", hasPdfHeader);
                    return hasPdfHeader;
                }
            } catch (Exception e) {
                log.warn("Error checking file header: {}", e.getMessage());
            }
        }

        return hasExtension || hasContentType;
    }

    private List<MultipartFile> convertPdfToImages(MultipartFile pdfFile) throws IOException {
        List<MultipartFile> imageFiles = new ArrayList<>();
        final long MAX_SIZE_BYTES = 2 * 1024 * 1024; // 2MB in bytes

        try {
            // Log that we're starting conversion
            log.info("Starting PDF conversion for file: {}", pdfFile.getOriginalFilename());

            PDDocument document = PDDocument.load(pdfFile.getInputStream());
            log.info("PDF loaded successfully. Number of pages: {}", document.getNumberOfPages());

            PDFRenderer pdfRenderer = new PDFRenderer(document);

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                log.info("Processing page {}", (page + 1));

                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
                log.info("Page {} rendered. Image dimensions: {}x{}", (page + 1), image.getWidth(), image.getHeight());

                ByteArrayOutputStream imageStream = new ByteArrayOutputStream();

                // Start with high quality compression
                float quality = 1.0f;
                byte[] imageBytes;

                // Compress until size is below MAX_SIZE_BYTES
                do {
                    imageStream.reset(); // Clear the stream for retry

                    // Use JPEGImageWriter for better compression control
                    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
                    ImageWriter writer = writers.next();

                    ImageWriteParam param = writer.getDefaultWriteParam();
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);

                    ImageOutputStream ios = ImageIO.createImageOutputStream(imageStream);
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(image, null, null), param);
                    writer.dispose();
                    ios.close();

                    imageBytes = imageStream.toByteArray();
                    log.info("Page {} compressed with quality={}, size={} bytes", (page + 1), quality, imageBytes.length);

                    // Reduce quality for next iteration if needed
                    quality -= 0.1f;
                } while (imageBytes.length > MAX_SIZE_BYTES && quality > 0.1f);

                // If still too large after compression attempts, log a warning
                if (imageBytes.length > MAX_SIZE_BYTES) {
                    log.warn("Page {} still exceeds max size after compression: {} bytes", (page + 1), imageBytes.length);
                }

                String imageName = "page-" + (page + 1) + ".jpg"; // Changed to .jpg since we're using JPEG format

                MultipartFile imageFile = new InMemoryMultipartFile(imageName, imageName, "image/jpeg", // Changed content type to JPEG
                        imageBytes);

                imageFiles.add(imageFile);
                log.info("Added image file to list: {} (size: {} bytes)", imageName, imageBytes.length);
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
                String urlPath = baseUrl + "/" + dossierId;
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
                try (BufferedReader br = new BufferedReader(new InputStreamReader(responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()))) {
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
    public Piece saveEcrituresAndFacture(Long pieceId, Long dossierId, String pieceData, JsonNode originalAiResponse) {
        // ** Step 1: Fetch the Piece by ID **
        Dossier dossier = dossierRepository.findById(dossierId).orElseThrow(() -> new IllegalArgumentException("Dossier not found for ID: " + dossierId));
        Piece piece = pieceRepository.findById(pieceId).orElseThrow(() -> new IllegalArgumentException("Piece not found for ID: " + pieceId));

        try {
            // ** Step 2: Save FactureData first to enable duplicate detection **
            saveFactureDataForPiece(piece, pieceData, originalAiResponse);

            // ** Step 3: Save Ecritures temporarily to enable comprehensive duplicate detection **
            saveEcrituresForPiece(piece, dossierId, pieceData, originalAiResponse);

            // ** Step 4: Perform comprehensive duplicate check BEFORE setting status to PROCESSED **
            Optional<Piece> comprehensiveDuplicate = duplicateDetectionService.performComprehensiveDuplicateCheck(piece);

            if (comprehensiveDuplicate.isPresent()) {
                log.warn("🚫 Comprehensive duplicate detected, marking piece {} as duplicate of piece {}", piece.getId(), comprehensiveDuplicate.get().getId());

                // Remove the ecritures that were just created since this is a duplicate
                if (piece.getEcritures() != null) {
                    piece.getEcritures().clear();
                    pieceRepository.save(piece);
                }

                duplicateDetectionService.markAsDuplicate(piece, comprehensiveDuplicate.get());

                log.info("⏭️ Marked piece {} as duplicate and removed ecritures", piece.getId());

                // ** IMPORTANT: Always notify WebSocket regardless of duplicate status **
                notifyPiecesUpdate(dossierId);

                return piece;
            }

            // ** Step 5: Update the amount and status of the Piece (only if not duplicate) **
            piece.setStatus(PieceStatus.PROCESSED);
            piece.setAmount(calculateAmountFromEcritures(pieceData, dossier, originalAiResponse)); // Updated method
            piece = pieceRepository.save(piece); // Save changes to the Piece

            log.info("✅ Piece {} successfully processed without duplicates", piece.getId());

        } catch (Exception e) {
            log.error("💥 Error in saveEcrituresAndFacture for piece {}: {}", piece.getId(), e.getMessage(), e);

            // Mark as rejected if there's an error
            piece.setStatus(PieceStatus.REJECTED);
            pieceRepository.save(piece);

        } finally {
            // ** ALWAYS notify WebSocket in finally block to ensure notification happens **
            try {
                notifyPiecesUpdate(dossierId);
            } catch (Exception notifyException) {
                log.error("Failed to notify WebSocket for dossier {}: {}", dossierId, notifyException.getMessage());
            }
        }

        return piece;
    }

    private void saveFactureDataForPiece(Piece piece, String pieceData, JsonNode originalAiResponse) {
        FactureData factureData = deserializeFactureData(pieceData);

        if (factureData == null) {
            log.warn("⚠️ No FactureData found in the pieceData for piece {}", piece.getId());
            return;
        }

        factureData.setPiece(piece); // Always associate the piece

        // Parse original AI response to get exact string values
        try {
            String responseText = originalAiResponse.asText();
            JsonNode parsedOriginal = objectMapper.readTree(responseText);
            JsonNode originalEcritures = parsedOriginal.get("ecritures");
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

        // 👉 Set ICE if missing
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

        // 👉 Set exchange info if missing (existing logic remains the same)
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

        // 👉 Save or update FactureData (existing logic remains the same)
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
            JsonNode rootNode = objectMapper.readTree(pieceData);

            // ✅ Case 1: "factureData" exists in JSON
            JsonNode factureDataNode = rootNode.get("factureData");
            if (factureDataNode != null && !factureDataNode.isNull()) {
                return objectMapper.treeToValue(factureDataNode, FactureData.class);
            }

            // ✅ Case 2: try to extract from "Ecritures" if no "factureData"
            JsonNode ecrituresNode = rootNode.get("ecritures");
            if (ecrituresNode == null) ecrituresNode = rootNode.get("Ecritures");

            if (ecrituresNode != null && ecrituresNode.isArray() && ecrituresNode.size() > 0) {
                FactureData fd = new FactureData();

                // Get the first entry to extract base values
                JsonNode first = ecrituresNode.get(0);

                fd.setInvoiceNumber(first.has("FactureNum") ? first.get("FactureNum").asText() : null);
                fd.setDevise(first.has("Devise") ? first.get("Devise").asText() : "MAD");

                String tvaRateStr = first.has("TVARate") ? first.get("TVARate").asText() : "0";
                if (tvaRateStr == null || tvaRateStr.trim().isEmpty()) tvaRateStr = "0";
                try {
                    fd.setTaxRate(Double.parseDouble(tvaRateStr.replace("%", "").trim()));
                } catch (NumberFormatException e) {
                    fd.setTaxRate(0.0);
                }

                // Optionally: calculate totalTTC, totalHT, totalTVA here if needed

                return fd;
            }

            return null;

        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse factureData or ecritures: " + e.getMessage(), e);
        }
    }

    @Transactional
    private void saveEcrituresForPiece(Piece piece, Long dossierId, String pieceData, JsonNode originalAiResponse) {
        log.info("Processing Piece ID: {}, Dossier ID: {}", piece.getId(), dossierId);

        // Fetch the Dossier explicitly
        Dossier dossier = dossierRepository.findById(dossierId).orElseThrow(() -> new IllegalArgumentException("Dossier not found for ID: " + dossierId));
        log.info("Fetched Dossier ID: {}", dossier.getId());

        // Parse original AI response to get exact string values
        JsonNode originalEcritures = null;
        try {
            String responseText = originalAiResponse.asText();
            JsonNode parsedOriginal = objectMapper.readTree(responseText);
            originalEcritures = parsedOriginal.get("ecritures");
            if (originalEcritures == null) {
                originalEcritures = parsedOriginal.get("Ecritures");
            }
        } catch (Exception e) {
            log.error("Error parsing original AI response: {}", e.getMessage(), e);
        }

        // Fetch existing Accounts and Journals for the Dossier
        Map<String, Account> accountMap = accountRepository.findByDossierId(dossierId).stream().collect(Collectors.toMap(Account::getAccount, Function.identity()));
        List<Journal> journals = journalRepository.findByDossierId(dossierId);

        List<Ecriture> ecritures = deserializeEcritures(pieceData, dossier);

        for (int i = 0; i < ecritures.size(); i++) {
            Ecriture ecriture = ecritures.get(i);
            ecriture.setPiece(piece);

            // Find or create Journal (existing logic)
            Journal journal = journals.stream().filter(j -> j.getName().equalsIgnoreCase(ecriture.getJournal().getName())).findFirst().orElseGet(() -> {
                log.info("Creating new Journal: {}", ecriture.getJournal().getName());
                Journal newJournal = new Journal(ecriture.getJournal().getName(), ecriture.getJournal().getType(), dossier.getCabinet(), dossier);
                Journal savedJournal = journalRepository.save(newJournal);
                journals.add(savedJournal);
                return savedJournal;
            });
            ecriture.setJournal(journal);

            try {
                Ecriture savedEcriture = ecritureRepository.save(ecriture);

                // Process lines with exact precision from original AI response
                for (int j = 0; j < ecriture.getLines().size(); j++) {
                    Line line = ecriture.getLines().get(j);
                    line.setEcriture(savedEcriture);

                    // Use exact precision from original AI response if available
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

                        // Set currencies
                        if (originalEntry.has("OriginalDevise")) {
                            line.setOriginalCurrency(originalEntry.get("OriginalDevise").asText());
                        }
                        if (originalEntry.has("Devise")) {
                            line.setConvertedCurrency(originalEntry.get("Devise").asText());
                        }
                        // Set exchange rate date
                        if (originalEntry.has("ExchangeRateDate")) {
                            try {
                                line.setExchangeRateDate(originalEntry.get("ExchangeRateDate").asText());
                            } catch (Exception e) {
                                log.trace("Error parsing exchange rate date: {}", e.getMessage());
                            }
                        }
                    }

                    // FIXED: Handle account creation with proper duplicate prevention
                    String accountNumber = line.getAccount().getAccount();
                    Account account = findOrCreateAccount(accountNumber, dossier, journal, line.getAccount().getLabel(), accountMap);
                    line.setAccount(account);
                }

                // Save Lines associated with the Ecriture
                lineRepository.saveAll(ecriture.getLines());

            } catch (Exception e) {
                log.error("Error saving Ecriture: {}", e.getMessage(), e);
                throw e;
            }
        }
    }

    /**
     * Thread-safe method to find or create an account
     */
    private Account findOrCreateAccount(String accountNumber, Dossier dossier, Journal journal, String accountLabel, Map<String, Account> accountMap) {
        // First check in-memory cache
        Account account = accountMap.get(accountNumber);
        if (account != null) {
            return account;
        }

        // Try to find existing account in database with proper Optional handling
        Optional<Account> existingAccount = Optional.ofNullable(accountRepository.findByAccountAndDossierId(accountNumber, dossier.getId()));
        if (existingAccount.isPresent()) {
            account = existingAccount.get();
            accountMap.put(accountNumber, account);
            return account;
        }

        // Account doesn't exist, try to create it with retry logic for concurrent scenarios
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Account newAccount = new Account();
                newAccount.setAccount(accountNumber);
                newAccount.setLabel(accountLabel);
                newAccount.setDossier(dossier);
                newAccount.setJournal(journal);
                newAccount.setHasEntries(true);

                log.info("Creating new Account (attempt {}): {}", attempt, accountNumber);
                account = accountRepository.save(newAccount);
                accountMap.put(accountNumber, account);
                return account;

            } catch (DataIntegrityViolationException e) {
                log.warn("Account creation conflict detected on attempt {} for account: {}", attempt, accountNumber);

                if (attempt == maxRetries) {
                    log.error("Failed to create account after {} attempts: {}", maxRetries, accountNumber);
                    throw new RuntimeException("Failed to create account after " + maxRetries + " attempts: " + accountNumber, e);
                }

                // Wait a bit and retry to fetch the account that might have been created by another thread
                try {
                    Thread.sleep(100 + (attempt * 50)); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted while waiting to retry account creation", ie);
                }

                // Try to fetch the account again - it might have been created by another thread
                Optional<Account> retryAccount = Optional.ofNullable(accountRepository.findByAccountAndDossierId(accountNumber, dossier.getId()));
                if (retryAccount.isPresent()) {
                    account = retryAccount.get();
                    accountMap.put(accountNumber, account);
                    return account;
                }

                // If we still can't find it, continue to next attempt
                log.warn("Account still not found after conflict, retrying creation...");
            }
        }

        throw new RuntimeException("Failed to find or create account: " + accountNumber);
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

            Map<String, Account> accountMap = accountRepository.findByDossierId(dossier.getId()).stream().collect(Collectors.toMap(Account::getAccount, Function.identity()));
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


    private Double calculateAmountFromEcritures(String pieceData, Dossier dossier, JsonNode originalAiResponse) {
        try {
            // Try to get exact amounts from original AI response first
            if (originalAiResponse != null) {
                String responseText = originalAiResponse.asText();
                JsonNode parsedOriginal = objectMapper.readTree(responseText);
                JsonNode originalEcritures = parsedOriginal.get("ecritures");
                if (originalEcritures == null) {
                    originalEcritures = parsedOriginal.get("Ecritures");
                }

                if (originalEcritures != null && originalEcritures.isArray()) {
                    BigDecimal maxAmount = BigDecimal.ZERO;

                    for (JsonNode entry : originalEcritures) {
                        String debitStr = entry.get("DebitAmt").asText("0");
                        String creditStr = entry.get("CreditAmt").asText("0");

                        BigDecimal debit = new BigDecimal(debitStr);
                        BigDecimal credit = new BigDecimal(creditStr);

                        maxAmount = maxAmount.max(debit.max(credit));
                    }

                    return maxAmount.doubleValue();
                }
            }
        } catch (Exception e) {
            log.warn("Error getting exact amount from original AI response, falling back to regular parsing: {}", e.getMessage());
        }

        // Fallback to existing logic
        List<Ecriture> ecritures = deserializeEcritures(pieceData, dossier);

        return ecritures.stream().flatMap(e -> e.getLines().stream()).flatMapToDouble(line -> DoubleStream.of(line.getDebit(), line.getCredit())).max().orElse(0.0);
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
        try {
            log.info("📡 Notifying WebSocket for dossier: {}", dossierId);

            List<Piece> pieces = pieceRepository.findByDossierId(dossierId);

            // Eagerly load collections before leaving the transaction
            pieces.forEach(piece -> {
                try {
                    if (piece.getEcritures() != null) {
                        Hibernate.initialize(piece.getEcritures());
                    }
                    if (piece.getFactureData() != null) {
                        Hibernate.initialize(piece.getFactureData());
                    }
                    // Also initialize duplicate-related fields
                    if (piece.getOriginalPiece() != null) {
                        Hibernate.initialize(piece.getOriginalPiece());
                    }
                } catch (Exception e) {
                    log.warn("Failed to initialize collections for piece {}: {}", piece.getId(), e.getMessage());
                }
            });

            List<PieceDTO> dtos = pieces.stream().map(piece -> {
                try {
                    PieceDTO dto = new PieceDTO();
                    dto.setId(piece.getId());
                    dto.setFilename(piece.getFilename());
                    dto.setOriginalFileName(piece.getOriginalFileName());
                    dto.setType(piece.getType());
                    dto.setStatus(piece.getStatus());
                    dto.setUploadDate(piece.getUploadDate());
                    dto.setAmount(piece.getAmount());
                    dto.setDossierId(piece.getDossier().getId());
                    dto.setDossierName(piece.getDossier().getName());
                    dto.setIsForced(piece.getIsForced());
                    // Add duplicate information to DTO
                    dto.setIsDuplicate(piece.getIsDuplicate());
                    if (piece.getOriginalPiece() != null) {
                        dto.setOriginalPieceId(piece.getOriginalPiece().getId());
                        dto.setOriginalPieceName(piece.getOriginalPiece().getOriginalFileName());
                    }

                    // Add AI currency and amount info
                    dto.setAiCurrency(piece.getAiCurrency());
                    dto.setAiAmount(piece.getAiAmount());

                    if (piece.getFactureData() != null) {
                        FactureDataDTO factureDataDTO = new FactureDataDTO();
                        factureDataDTO.setInvoiceNumber(piece.getFactureData().getInvoiceNumber());
                        factureDataDTO.setTotalTVA(piece.getFactureData().getTotalTVA());
                        factureDataDTO.setTaxRate(piece.getFactureData().getTaxRate());
                        factureDataDTO.setInvoiceDate(piece.getFactureData().getInvoiceDate());

                        // Add currency information
                        factureDataDTO.setDevise(piece.getFactureData().getDevise());
                        factureDataDTO.setOriginalCurrency(piece.getFactureData().getOriginalCurrency());
                        factureDataDTO.setConvertedCurrency(piece.getFactureData().getConvertedCurrency());
                        factureDataDTO.setExchangeRate(piece.getFactureData().getExchangeRate());

                        dto.setFactureData(factureDataDTO);
                    }

                    if (piece.getEcritures() != null && !piece.getEcritures().isEmpty()) {
                        List<EcrituresDTO2> ecrituresDTOs = piece.getEcritures().stream().map(ecriture -> {
                            EcrituresDTO2 dto2 = new EcrituresDTO2();
                            dto2.setUniqueEntryNumber(ecriture.getUniqueEntryNumber());
                            dto2.setEntryDate(ecriture.getEntryDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

                            // Add journal information
                            if (ecriture.getJournal() != null) {
                                JournalDTO journalDTO = new JournalDTO();
                                journalDTO.setName(ecriture.getJournal().getName());
                                journalDTO.setType(ecriture.getJournal().getType());
                                dto2.setJournal(journalDTO);
                            }

                            return dto2;
                        }).collect(Collectors.toList());
                        dto.setEcritures(ecrituresDTOs);
                    }

                    return dto;
                } catch (Exception e) {
                    log.error("Error mapping piece {} to DTO: {}", piece.getId(), e.getMessage());
                    // Return minimal DTO with error info
                    PieceDTO errorDto = new PieceDTO();
                    errorDto.setId(piece.getId());
                    errorDto.setFilename(piece.getFilename());
                    errorDto.setOriginalFileName(piece.getOriginalFileName());
                    errorDto.setStatus(piece.getStatus());
                    errorDto.setDossierId(piece.getDossier().getId());
                    return errorDto;
                }
            }).collect(Collectors.toList());

            // Send WebSocket message
            messagingTemplate.convertAndSend("/topic/dossier-pieces/" + dossierId, dtos);
            log.info("✅ Successfully notified WebSocket for dossier {} with {} pieces", dossierId, dtos.size());

        } catch (Exception e) {
            log.error("💥 Failed to notify WebSocket for dossier {}: {}", dossierId, e.getMessage(), e);

            // Send error notification to WebSocket
            try {
                Map<String, Object> errorMessage = new HashMap<>();
                errorMessage.put("error", true);
                errorMessage.put("message", "Failed to load pieces: " + e.getMessage());
                errorMessage.put("dossierId", dossierId);
                errorMessage.put("timestamp", new Date());

                messagingTemplate.convertAndSend("/topic/dossier-pieces/" + dossierId, errorMessage);
            } catch (Exception notifyError) {
                log.error("Failed to send error notification to WebSocket: {}", notifyError.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void deletePiece(Long id) {
        pieceRepository.deleteById(id);
    }

    @Override
    public Piece getPieceById(Long id) {
        return pieceRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Piece with id " + id + " not found"));
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


    @Override
    public byte[] getPieceFilesAsZip(Long pieceId) {
        try {
            // Get requested piece
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
            return null;
        }
    }

    private byte[] createZipFromPieces(List<Piece> pieces) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        for (Piece piece : pieces) {
            // Read file from upload directory
            Path filePath = Paths.get(uploadDir, piece.getFilename());

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

        zos.close();
        return baos.toByteArray();
    }


    @Override
    @Transactional
    public Piece forcePieceNotDuplicate(Long pieceId) {
        Piece piece = pieceRepository.findById(pieceId).orElseThrow(() -> new IllegalArgumentException("Piece not found with ID: " + pieceId));
        // ✅ Vérifie que la pièce est bien en statut DUPLICATE
        if (!Boolean.TRUE.equals(piece.getIsDuplicate()) || piece.getStatus() != PieceStatus.DUPLICATE) {
            throw new IllegalStateException("Seules les pièces dupliquées peuvent être forcées à être considérées comme non dupliquées.");
        }
        // ✅ Set force flag
        piece.setIsForced(true);
        piece.setIsDuplicate(false);
        // ✅ Reset data fields
        piece.setAmount(null);
        piece.setStatus(PieceStatus.UPLOADED);
        piece.setAiAmount(null);
        piece.setAiCurrency(null);
        piece.setExchangeRate(null);
        piece.setConvertedCurrency(null);
        piece.setExchangeRateDate(null);
        piece.setExchangeRateUpdated(false);
        // ✅ Delete factureData if exists
        if (piece.getFactureData() != null) {
            factureDataRepository.delete(piece.getFactureData());
            piece.setFactureData(null);
        }
        // ✅ Delete ecritures and lines
        if (piece.getEcritures() != null) {
            for (Ecriture ecriture : piece.getEcritures()) {
                lineRepository.deleteAll(ecriture.getLines());
            }
            ecritureRepository.deleteAll(piece.getEcritures());
            piece.getEcritures().clear(); // ✅ vide la liste proprement
        }
        return pieceRepository.save(piece);
    }
}
