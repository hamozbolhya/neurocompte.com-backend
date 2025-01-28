package com.pacioli.core.batches;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacioli.core.DTO.*;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.PieceRepository;
import com.pacioli.core.services.PieceService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@EnableScheduling
public class AIPieceProcessingService {
    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY = 5000;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    @Value("${ai.service.api-key}")
    private String apiKey;

    @Value("${file.upload.dir:Files/}")
    private String uploadDir;

    @Autowired
    private PieceRepository pieceRepository;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private PieceService pieceService;
    @Autowired
    private ObjectMapper objectMapper;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void processPieceBatch() {
        // Only get UPLOADED pieces for new processing
        List<Piece> pendingPieces;
        pendingPieces = pieceRepository.findTop20ByStatusOrderByUploadDateAsc(PieceStatus.UPLOADED);
        if(pendingPieces.size() == 0) {
            pendingPieces = pieceRepository.findTop20ByStatusOrderByUploadDateAsc(PieceStatus.PROCESSING);
        }
        log.info("⭐️ Starting batch processing");
        log.info("Found {} new pieces to process", pendingPieces.size());

        ExecutorService executor = Executors.newFixedThreadPool(BATCH_SIZE);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Piece piece : pendingPieces) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    attemptAIProcessing(piece, 1);
                } catch (Exception e) {
                    log.error("Failed to process piece {}: {}", piece.getId(), e.getMessage());
                    log.info("❌Failed to process piece {}, moving to next. Error: {}", piece.getId(), e.getMessage());
                    rejectPiece(piece, "Processing failed: " + e.getMessage());
                }
            }, executor);
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            log.info("✅ Batch processing completed");
            executor.shutdown();
        }
    }

    private void attemptAIProcessing(Piece piece, int attempt) throws InterruptedException {
        // Reload piece from DB to get current status
        Piece currentPiece = pieceRepository.findById(piece.getId()).orElse(piece);

        // Skip if already processed
        if (currentPiece.getStatus() == PieceStatus.PROCESSED) {
            return;
        }

        if (attempt > 4) {
            rejectPiece(currentPiece, "Failed after 4 AI attempts");
            return;
        }

        // Only update if UPLOADED
        if (currentPiece.getStatus() == PieceStatus.UPLOADED) {
            currentPiece.setStatus(PieceStatus.PROCESSING);
            pieceRepository.save(currentPiece);
            log.info("DOSSIER ID 1 {}", currentPiece.getDossier().getId());
            pieceService.getPiecesByDossier(currentPiece.getDossier().getId());
        }

        try {
            String jsonResponse = callAIService(currentPiece.getFilename());
            JsonNode root = objectMapper.readTree(jsonResponse);

            if (!root.has("outputText") || !validateEcritures(root.get("outputText"))) {
                if (attempt < 4) {
                    Thread.sleep(30000);
                    attemptAIProcessing(currentPiece, attempt + 1);
                } else {
                    rejectPiece(currentPiece, "Invalid AI response after all attempts");
                    log.info("❌ File rejected response AI be like {}", jsonResponse);
                }
                return;
            }

            PieceDTO pieceDTO = buildPieceDTO(currentPiece, root.get("outputText"));
            pieceService.saveEcrituresAndFacture(
                    currentPiece.getId(),
                    currentPiece.getDossier().getId(),
                    objectMapper.writeValueAsString(pieceDTO)
            );
            pieceService.getPiecesByDossier(currentPiece.getDossier().getId());
        } catch (Exception e) {
            if (attempt < 4) {
                Thread.sleep(30000);
                attemptAIProcessing(currentPiece, attempt + 1);
            } else {
                rejectPiece(currentPiece, "Failed after all attempts");
            }
        }
    }


    private void rejectPiece(Piece piece, String reason) {
        log.error("Rejecting piece {}: {}", piece.getId(), reason);
        piece.setStatus(PieceStatus.REJECTED);
        pieceRepository.save(piece);
        log.info("DOSSIER ID 2 {}", piece.getDossier().getId());
        pieceService.getPiecesByDossier(piece.getDossier().getId());
    }


    private void processWithRetry(Piece piece, int attempt) {
        piece.setStatus(PieceStatus.PROCESSING);
        pieceRepository.save(piece);
        log.info("Processing piece: {} (attempt {}/4)", piece.getId(), attempt);
        log.info("DOSSIER ID 3 {}", piece.getDossier().getId());
        pieceService.getPiecesByDossier(piece.getDossier().getId());

        try {
            String jsonResponse = callAIService(piece.getFilename());
            JsonNode root = objectMapper.readTree(jsonResponse);

            if (!root.has("outputText") || !validateEcritures(root.get("outputText"))) {
                if (attempt < 4) {
                    Thread.sleep(30000); // Wait 30 seconds
                    processWithRetry(piece, attempt + 1);
                    return;
                }
                rejectPiece(piece, "Invalid AI response after 4 attempts");
                return;
            }

            PieceDTO pieceDTO = buildPieceDTO(piece, root.get("outputText"));
            pieceService.saveEcrituresAndFacture(
                    piece.getId(),
                    piece.getDossier().getId(),
                    objectMapper.writeValueAsString(pieceDTO)
            );
            log.info("DOSSIER ID 4 {}", piece.getDossier().getId());
            pieceService.getPiecesByDossier(piece.getDossier().getId());
        } catch (Exception e) {
            if (attempt < 4) {
                try {
                    Thread.sleep(30000);
                    processWithRetry(piece, attempt + 1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    rejectPiece(piece, "Retry interrupted");
                }
            } else {
                rejectPiece(piece, "Failed after 4 attempts");
            }
        }
    }
    private void handleFailure(Piece piece, String error, int attempt) {
        if (attempt < MAX_RETRIES) {
            log.info("⏳ Waiting {}ms before retry {}/{} for piece [ID: {}]",
                    RETRY_DELAY, attempt + 1, MAX_RETRIES, piece.getId());
            try {
                Thread.sleep(RETRY_DELAY);
                processWithRetry(piece, attempt + 1);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.info("🚫 Retry interrupted");
            }
        } else {
            log.info("❌ Final failure after {} attempts. Setting status REJECTED", MAX_RETRIES);
            piece.setStatus(PieceStatus.REJECTED);
            pieceRepository.save(piece);
        }
    }


    private String callAIService(String filename) throws IOException {
        Path filePath = Paths.get(uploadDir, filename);
        log.info("📂 Checking file at: {}", filePath);

        if (!Files.exists(filePath)) {
            log.info("❌ File not found: {}", filePath);
            throw new FileNotFoundException("File not found: " + filename);
        }

        String jsonFilename = filename.substring(0, filename.lastIndexOf(".")) + ".json";
        log.info("🔗 Calling AI service URL: {}", aiServiceUrl + jsonFilename);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);

        ResponseEntity<String> response = restTemplate.exchange(
                aiServiceUrl + jsonFilename,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        log.info("📡 AI service response status: {}", response.getStatusCode());

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("AI service failed with status: " + response.getStatusCode());
        }

        log.info("✅ AI service call successful");
        return response.getBody();
    }

    private boolean validateEcritures(JsonNode node) {
        try {
            JsonNode parsedJson = objectMapper.readTree(node.asText());
            JsonNode ecritures = parsedJson.get("ecritures");
            if (ecritures == null) {
                ecritures = parsedJson.get("Ecritures");
            }

            if (ecritures == null || !ecritures.isArray() || ecritures.size() == 0) {
                log.info("❌ No valid ecritures found in response {}", parsedJson);
                return false;
            }

            for (JsonNode entry : ecritures) {
                if (!validateEcritureFields(entry)) {
                    return false;
                }
            }
            return true;
        } catch (JsonProcessingException e) {
            log.info("💥 Error parsing JSON: {}", e.getMessage());
            return false;
        }
    }

    private boolean validateEcritureFields(JsonNode entry) {
        return entry != null &&
               entry.has("Date") && !entry.get("Date").asText().isEmpty() &&
               entry.has("JournalCode") && !entry.get("JournalCode").asText().isEmpty() &&
               entry.has("JournalLib") && !entry.get("JournalLib").asText().isEmpty() &&
               entry.has("FactureNum") && !entry.get("FactureNum").asText().isEmpty() &&
               entry.has("CompteNum") && !entry.get("CompteNum").asText().isEmpty() &&
               entry.has("CompteLib") && !entry.get("CompteLib").asText().isEmpty() &&
               entry.has("EcritLib") && !entry.get("EcritLib").asText().isEmpty() &&
               entry.has("DebitAmt") && entry.get("DebitAmt").isNumber() &&
               entry.has("CreditAmt") && entry.get("CreditAmt").isNumber() &&
               entry.has("TVARate") && entry.get("TVARate").isNumber() &&
               entry.has("Devise") && !entry.get("Devise").asText().isEmpty();
    }

    private PieceDTO buildPieceDTO(Piece piece, JsonNode aiResponse) throws JsonProcessingException {
        JsonNode parsedJson = objectMapper.readTree(aiResponse.asText());
        JsonNode ecrituresNode = parsedJson.get("ecritures");
        if (ecrituresNode == null) {
            ecrituresNode = parsedJson.get("Ecritures");
        }

        JsonNode firstEntry = ecrituresNode.get(0);

        PieceDTO pieceDTO = new PieceDTO();
        pieceDTO.setId(piece.getId());
        pieceDTO.setFilename(piece.getFilename());
        pieceDTO.setType(piece.getType());
        pieceDTO.setUploadDate(piece.getUploadDate());
        pieceDTO.setAmount(calculateLargestAmount(ecrituresNode));
        pieceDTO.setFactureData(buildFactureData(firstEntry));
        pieceDTO.setEcritures(buildEcritures(ecrituresNode));
        pieceDTO.setDossierId(piece.getDossier().getId());
        pieceDTO.setDossierName(piece.getDossier().getName());

        return pieceDTO;
    }

    private String formatDateToStandard(String dateStr) {
        try {
            // Try parsing different date formats
            List<DateTimeFormatter> formatters = Arrays.asList(
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    DateTimeFormatter.ofPattern("dd-MM-yyyy")
            );

            LocalDate date = null;
            for (DateTimeFormatter formatter : formatters) {
                try {
                    date = LocalDate.parse(dateStr, formatter);
                    break;
                } catch (DateTimeParseException e) {
                    continue;
                }
            }

            if (date == null) {
                log.trace("❌ Could not parse date: {}, using current date", dateStr);
                date = LocalDate.now();
            }

            // Convert to standard format dd/MM/yyyy
            return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            log.trace("❌ Date formatting failed for: {}", dateStr);
            return LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
    }

    private Double calculateLargestAmount(JsonNode ecritures) {
        double maxAmount = 0.0;
        for (JsonNode entry : ecritures) {
            double debit = entry.get("DebitAmt").asDouble();
            double credit = entry.get("CreditAmt").asDouble();
            maxAmount = Math.max(maxAmount, Math.max(debit, credit));
        }
        return maxAmount;
    }

    private List<EcrituresDTO2> buildEcritures(JsonNode ecrituresNode) {
        List<EcrituresDTO2> ecritures = new ArrayList<>();

        EcrituresDTO2 ecriture = new EcrituresDTO2();
        ecriture.setUniqueEntryNumber(UUID.randomUUID().toString());

        // Get date from first entry and format it
        String dateStr = ecrituresNode.get(0).get("Date").asText();
        String formattedDate = formatDateToStandard(dateStr);

        ecriture.setEntryDate(formattedDate);
        ecriture.setJournal(buildJournal(ecrituresNode.get(0)));
        ecriture.setLines(buildLines(ecrituresNode));

        ecritures.add(ecriture);
        return ecritures;
    }

    private String formatDate(String dateStr) {
        try {
            if (dateStr.contains("/")) {
                return dateStr; // Already in correct format
            }
            LocalDate date = LocalDate.parse(dateStr);
            return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            log.error("Error formatting date: {}", dateStr, e);
            return LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
    }

    private LocalDate parseDate(String dateStr) {
        try {
            // Handle dd/MM/yyyy format
            if (dateStr.contains("/")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                return LocalDate.parse(dateStr, formatter);
            }
            // Handle yyyy-MM-dd format
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.trace("📅 Date parsing failed for: {}. Using current date.", dateStr);
            return LocalDate.now();
        }
    }

    private FactureDataDTO buildFactureData(JsonNode entry) {
        FactureDataDTO factureData = new FactureDataDTO();
        factureData.setInvoiceNumber(entry.get("FactureNum").asText());
        factureData.setTotalTVA(entry.get("TVARate").asDouble());
        factureData.setTaxRate(entry.get("TVARate").asDouble());
        return factureData;
    }

    private JournalDTO buildJournal(JsonNode entry) {
        JournalDTO journal = new JournalDTO();
        journal.setName(entry.get("JournalCode").asText());
        journal.setType(entry.get("JournalLib").asText());
        return journal;
    }

    private List<LineDTO> buildLines(JsonNode ecrituresNode) {
        List<LineDTO> lines = new ArrayList<>();
        for (JsonNode entry : ecrituresNode) {
            LineDTO line = new LineDTO();
            line.setLabel(entry.get("EcritLib").asText());
            line.setDebit(entry.get("DebitAmt").asDouble());
            line.setCredit(entry.get("CreditAmt").asDouble());

            AccountDTO account = new AccountDTO();
            account.setAccount(entry.get("CompteNum").asText());
            account.setLabel(entry.get("CompteLib").asText());
            line.setAccount(account);

            lines.add(line);
        }
        return lines;
    }
}