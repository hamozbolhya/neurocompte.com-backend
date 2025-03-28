package com.pacioli.core.batches;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pacioli.core.DTO.*;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.ExchangeRate;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.DossierRepository;
import com.pacioli.core.repositories.PieceRepository;
import com.pacioli.core.services.ExchangeRateService;
import com.pacioli.core.services.PieceService;
import com.pacioli.core.utils.NormalizeCurrencyCode;
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
    private DossierRepository dossierRepository;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private PieceService pieceService;
    @Autowired
    private ExchangeRateService exchangeRateService;
    @Autowired
    private ObjectMapper objectMapper;

    private NormalizeCurrencyCode normalizeCurrencyCode;

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
            // Add debugging logs to see what we're working with
            log.debug("Input node type: {}", node.getNodeType());
            String textValue = node.asText();
            log.debug("Text representation length: {}", textValue.length());

            // First, validate that the string is not empty
            if (textValue == null || textValue.trim().isEmpty()) {
                log.error("❌ Empty output text from AI service");
                return false;
            }

            // Try to parse the JSON string from the outputText field
            JsonNode parsedJson;
            try {
                parsedJson = objectMapper.readTree(textValue);
            } catch (Exception e) {
                log.error("❌ Failed to parse JSON from outputText: {}", e.getMessage());
                log.error("First 200 chars of text: {}", textValue.substring(0, Math.min(200, textValue.length())));
                return false;
            }

            // Look for ecritures field (case-insensitive)
            JsonNode ecritures = null;
            if (parsedJson.has("ecritures")) {
                ecritures = parsedJson.get("ecritures");
            } else if (parsedJson.has("Ecritures")) {
                ecritures = parsedJson.get("Ecritures");
            }

            // Validate we found an ecritures array
            if (ecritures == null) {
                log.error("❌ No 'ecritures' or 'Ecritures' field found in JSON");
                return false;
            }

            // Validate that ecritures is an array
            if (!ecritures.isArray()) {
                log.error("❌ The 'ecritures' field is not an array");
                return false;
            }

            // Validate that the array is not empty
            if (ecritures.size() == 0) {
                log.error("❌ The 'ecritures' array is empty");
                return false;
            }

            log.debug("Found {} ecritures to validate", ecritures.size());

            // Validate each ecriture entry
            boolean allValid = true;
            for (int i = 0; i < ecritures.size(); i++) {
                JsonNode entry = ecritures.get(i);
                if (!validateEcritureFields(entry)) {
                    log.error("❌ Invalid ecriture at index {}: {}", i, entry);
                    allValid = false;
                    // Don't return immediately, continue validation to log all issues
                }
            }

            if (!allValid) {
                return false;
            }

            log.info("✅ Successfully validated all ecritures");
            return true;
        } catch (Exception e) {
            // Use more detailed logging with the full stack trace
            log.error("💥 Error validating ecritures: {}", e.getMessage(), e);
            // Log a portion of the input to help diagnose the issue
            if (node != null) {
                String text = node.asText();
                log.error("Input excerpt (first 100 chars): {}",
                        text.length() > 100 ? text.substring(0, 100) + "..." : text);
            }
            return false;
        }
    }

    private boolean validateEcritureFields(JsonNode entry) {
        // Check for null entry
        if (entry == null) {
            log.error("❌ Ecriture entry is null");
            return false;
        }

        // Log the entry for debugging
        log.debug("Validating ecriture: {}", entry);

        // Validate required fields exist and aren't empty
        String[] requiredStringFields = {
                "Date", "JournalCode", "JournalLib", "FactureNum",
                "CompteNum", "CompteLib", "EcritLib", "Devise"
        };

        for (String field : requiredStringFields) {
            if (!entry.has(field)) {
                log.error("❌ Missing required field: {}", field);
                return false;
            }

            JsonNode fieldNode = entry.get(field);
            if (fieldNode.isNull() || fieldNode.asText().trim().isEmpty()) {
                log.error("❌ Required field is empty: {}", field);
                return false;
            }
        }

        // Validate numeric fields (DebitAmt and CreditAmt)
        String[] numericFields = {"DebitAmt", "CreditAmt"};
        for (String field : numericFields) {
            if (!entry.has(field)) {
                log.error("❌ Missing required numeric field: {}", field);
                return false;
            }

            // Get the raw value
            JsonNode fieldNode = entry.get(field);
            String rawValue = fieldNode.asText().trim();

            try {
                // Attempt to parse as double
                double value = Double.parseDouble(rawValue);

                // Check for invalid values
                if (Double.isInfinite(value) || Double.isNaN(value)) {
                    log.error("❌ Invalid numeric value for {}: {}", field, rawValue);
                    return false;
                }

                // All good with this field
                log.debug("Successfully validated numeric field {}: {}", field, value);
            } catch (NumberFormatException e) {
                log.error("❌ Field {} contains non-numeric value: '{}'", field, rawValue);
                return false;
            }
        }

        // All validations passed
        return true;
    }

    /**
     * Processes the AI service output text and builds a PieceDTO
     * This method has been enhanced to better handle the JSON structure from the AI service
     */
    private PieceDTO buildPieceDTO(Piece piece, JsonNode aiResponse) throws JsonProcessingException {
        try {
            // First, safely parse the JSON structure
            log.info("⚙️ Processing AI response for piece {}", piece.getId());

            // Parse the JSON text from the aiResponse node
            String responseText = aiResponse.asText();
            JsonNode parsedJson = objectMapper.readTree(responseText);

            // Look for ecritures array (handles both "ecritures" and "Ecritures" case variants)
            JsonNode ecrituresNode = parsedJson.get("ecritures");
            if (ecrituresNode == null) {
                ecrituresNode = parsedJson.get("Ecritures");
            }

            if (ecrituresNode == null || !ecrituresNode.isArray() || ecrituresNode.size() == 0) {
                throw new IllegalArgumentException("No valid ecritures array found in AI response");
            }

            log.info("📊 Found {} ecritures entries in AI response", ecrituresNode.size());

            // Get the first entry for invoice metadata
            JsonNode firstEntry = ecrituresNode.get(0);

            // Calculate and store the original amount from AI
            double originalAmount = calculateLargestAmount(ecrituresNode);
            log.info("💰 Calculated original amount: {}", originalAmount);
            piece.setAiAmount(originalAmount);

            // Process currency information
            String invoiceCurrencyCode = null;
            if (firstEntry.has("Devise") && !firstEntry.get("Devise").isNull() && !firstEntry.get("Devise").asText().isEmpty()) {
                // Get the raw currency value
                String rawCurrency = firstEntry.get("Devise").asText();

                // Normalize it using the injected service
                if (normalizeCurrencyCode == null) {
                    // If the service is not injected, create a new instance
                    log.warn("NormalizeCurrencyCode service not injected, creating new instance");
                    normalizeCurrencyCode = new NormalizeCurrencyCode();
                }

                invoiceCurrencyCode = normalizeCurrencyCode.normalizeCurrencyCode(rawCurrency);
                piece.setAiCurrency(invoiceCurrencyCode);
                log.info("💱 Set AI currency for piece {}: {} (normalized from '{}')",
                        piece.getId(), invoiceCurrencyCode, rawCurrency);
            } else {
                log.info("⚠️ No currency information found in AI response, using default USD");
                invoiceCurrencyCode = "USD";
                piece.setAiCurrency(invoiceCurrencyCode);
            }

            // Save the updated piece with AI currency and amount
            pieceRepository.save(piece);

            // Get the dossier currency
            Long dossierId = piece.getDossier().getId();
            Dossier dossier = dossierRepository.findById(dossierId)
                    .orElseThrow(() -> new RuntimeException("Dossier not found: " + dossierId));

            String dossierCurrencyCode = dossier.getCurrency() != null ?
                    normalizeCurrencyCode.normalizeCurrencyCode(dossier.getCurrency().getCode()) : "MAD";

            log.info("💱 Dossier {} has currency {}", dossierId, dossierCurrencyCode);

            // Get invoice date from the first entry
            String invoiceDateStr = firstEntry.get("Date").asText();
            LocalDate invoiceDate = parseDate(invoiceDateStr);
            log.info("📅 Invoice date is {}", invoiceDate);

            // Check if currency conversion is needed
            JsonNode convertedEcrituresNode;
            if (invoiceCurrencyCode != null && !invoiceCurrencyCode.equals(dossierCurrencyCode)) {
                log.info("💱 Currency conversion needed: {} to {}", invoiceCurrencyCode, dossierCurrencyCode);

                // Calculate exchange rate
                Double exchangeRate = calculateExchangeRate(invoiceDate, invoiceCurrencyCode, dossierCurrencyCode);
                LocalDate effectiveExchangeDate = determineEffectiveDate(invoiceDate);

                // Set values in the piece entity
                piece.setExchangeRate(exchangeRate);
                piece.setConvertedCurrency(dossierCurrencyCode);
                piece.setExchangeRateDate(effectiveExchangeDate);
                pieceRepository.save(piece);

                // Convert the currency values in ecritures
                convertedEcrituresNode = convertCurrencyIfNeeded(
                        ecrituresNode,
                        invoiceDate,
                        invoiceCurrencyCode,
                        dossierCurrencyCode
                );
            } else {
                log.info("💱 No currency conversion needed");
                convertedEcrituresNode = ecrituresNode;
            }

            // Build and return the PieceDTO
            PieceDTO pieceDTO = new PieceDTO();
            pieceDTO.setId(piece.getId());
            pieceDTO.setFilename(piece.getFilename());
            pieceDTO.setType(piece.getType());
            pieceDTO.setUploadDate(piece.getUploadDate());
            pieceDTO.setAmount(calculateLargestAmount(convertedEcrituresNode));
            pieceDTO.setFactureData(buildFactureData(firstEntry));
            pieceDTO.setEcritures(buildEcritures(convertedEcrituresNode));
            pieceDTO.setDossierId(piece.getDossier().getId());
            pieceDTO.setDossierName(piece.getDossier().getName());

            // Set AI-related fields
            pieceDTO.setAiCurrency(piece.getAiCurrency());
            pieceDTO.setAiAmount(piece.getAiAmount());

            // Add currency and exchange rate information to the DTO
            pieceDTO.setOriginalCurrency(invoiceCurrencyCode);
            pieceDTO.setDossierCurrency(dossierCurrencyCode);

            // If conversion was performed, add exchange rate info
            if (!invoiceCurrencyCode.equals(dossierCurrencyCode)) {
                pieceDTO.setExchangeRate(piece.getExchangeRate());
                pieceDTO.setConvertedCurrency(piece.getConvertedCurrency());
                pieceDTO.setExchangeRateDate(piece.getExchangeRateDate());
            }

            log.info("✅ Successfully built PieceDTO for piece {}", piece.getId());
            return pieceDTO;

        } catch (Exception e) {
            log.error("💥 Error building PieceDTO: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build PieceDTO from AI response", e);
        }
    }
    private JsonNode convertCurrencyIfNeeded(
            JsonNode ecrituresNode,
            LocalDate invoiceDate,
            String invoiceCurrencyCode,
            String dossierCurrencyCode
    ) {
        // Normalize currency codes to ensure consistent comparisons
        invoiceCurrencyCode = normalizeCurrencyCode.normalizeCurrencyCode(invoiceCurrencyCode);
        dossierCurrencyCode = normalizeCurrencyCode.normalizeCurrencyCode(dossierCurrencyCode);

        // If currencies are the same after normalization, no conversion needed
        if (invoiceCurrencyCode.equals(dossierCurrencyCode)) {
            return ecrituresNode;
        }

        // Calculate the exchange rate based on business rules
        double exchangeRate = calculateExchangeRate(invoiceDate, invoiceCurrencyCode, dossierCurrencyCode);
        LocalDate effectiveDate = determineEffectiveDate(invoiceDate);
        log.info("💱 Using exchange rate for conversion: {}", exchangeRate);

        // Create a new array with converted amounts
        ArrayNode convertedEcritures = objectMapper.createArrayNode();

        for (JsonNode entry : ecrituresNode) {
            // Create a copy of the entry
            ObjectNode convertedEntry = objectMapper.createObjectNode();

            // Copy all fields
            entry.fields().forEachRemaining(field -> {
                convertedEntry.set(field.getKey(), field.getValue());
            });

            // Get original amounts
            double debitAmt = entry.get("DebitAmt").asDouble();
            double creditAmt = entry.get("CreditAmt").asDouble();

            // Apply conversion
            double convertedDebitAmt = debitAmt * exchangeRate;
            double convertedCreditAmt = creditAmt * exchangeRate;

            // Optional: Calculate USD equivalents
            double usdDebitAmt = 0.0;
            double usdCreditAmt = 0.0;

            // If invoice currency is USD, use directly; otherwise calculate
            if ("USD".equals(invoiceCurrencyCode)) {
                usdDebitAmt = debitAmt;
                usdCreditAmt = creditAmt;
            } else {
                // Get USD rate for invoice currency
                try {
                    ExchangeRate invoiceUsdRate = exchangeRateService.getExchangeRate(invoiceCurrencyCode, effectiveDate);
                    if (invoiceUsdRate != null) {
                        usdDebitAmt = debitAmt / invoiceUsdRate.getRate();
                        usdCreditAmt = creditAmt / invoiceUsdRate.getRate();
                    }
                } catch (Exception e) {
                    log.warn("Could not calculate USD equivalents: {}", e.getMessage());
                }
            }

            if (debitAmt > 0) {
                log.info("💱 Converting debit: {} {} → {} {}",
                        debitAmt, invoiceCurrencyCode, convertedDebitAmt, dossierCurrencyCode);
            }
            if (creditAmt > 0) {
                log.info("💱 Converting credit: {} {} → {} {}",
                        creditAmt, invoiceCurrencyCode, convertedCreditAmt, dossierCurrencyCode);
            }

            // Store original values
            convertedEntry.put("OriginalDebitAmt", debitAmt);
            convertedEntry.put("OriginalCreditAmt", creditAmt);
            convertedEntry.put("OriginalDevise", invoiceCurrencyCode);

            // Store converted values - round to 2 decimal places
            convertedEntry.put("DebitAmt", Math.round(convertedDebitAmt * 100.0) / 100.0);
            convertedEntry.put("CreditAmt", Math.round(convertedCreditAmt * 100.0) / 100.0);
            convertedEntry.put("Devise", dossierCurrencyCode);

            // Store USD equivalents - round to 2 decimal places
            convertedEntry.put("UsdDebitAmt", Math.round(usdDebitAmt * 100.0) / 100.0);
            convertedEntry.put("UsdCreditAmt", Math.round(usdCreditAmt * 100.0) / 100.0);

            // Store conversion details
            convertedEntry.put("ExchangeRate", exchangeRate);
            convertedEntry.put("ExchangeRateDate", effectiveDate.toString());

            // Add to the converted array
            convertedEcritures.add(convertedEntry);
        }

        return convertedEcritures;
    }

    // Add this method to your service and call it to test
    public void testExchangeRateService() {
        LocalDate testDate = LocalDate.of(2024, 1, 1);
        try {
            ExchangeRate madRate = exchangeRateService.getExchangeRate("MAD", testDate);
            log.info("MAD exchange rate on {}: {}", testDate, madRate);
        } catch (Exception e) {
            log.error("Failed to get MAD rate: {}", e.getMessage(), e);
        }

        try {
            ExchangeRate usdRate = exchangeRateService.getExchangeRate("USD", testDate);
            log.info("USD exchange rate on {}: {}", testDate, usdRate);
        } catch (Exception e) {
            log.error("Failed to get USD rate: {}", e.getMessage(), e);
        }
    }

    // Fixed calculateExchangeRate method with proper null handling
    private double calculateExchangeRate(LocalDate invoiceDate, String invoiceCurrencyCode, String dossierCurrencyCode) {
        try {
            // If currencies are the same, no conversion needed
            if (invoiceCurrencyCode.equals(dossierCurrencyCode)) {
                log.info("💱 Same currency for invoice and dossier ({}), no conversion needed", invoiceCurrencyCode);
                return 1.0;
            }

            // Apply date rules to get the effective date for exchange rate lookup
            LocalDate effectiveDate = determineEffectiveDate(invoiceDate);
            log.info("📅 Using effective date for exchange rate: {} (original invoice date: {})",
                    effectiveDate, invoiceDate);

            // Get exchange rates for effective date
            ExchangeRate invoiceCurrencyRate = null;
            ExchangeRate dossierCurrencyRate = null;

            try {
                invoiceCurrencyRate = exchangeRateService.getExchangeRate(invoiceCurrencyCode, effectiveDate);
                log.info("💱 Got exchange rate for {}: {}", invoiceCurrencyCode, invoiceCurrencyRate);
            } catch (Exception e) {
                log.error("Failed to get exchange rate for {} on date {}: {}",
                        invoiceCurrencyCode, effectiveDate, e.getMessage());
                // Use a default fallback rate
                invoiceCurrencyRate = createFallbackExchangeRate(invoiceCurrencyCode, effectiveDate);
                log.info("💱 Using fallback exchange rate for {}: {}", invoiceCurrencyCode, invoiceCurrencyRate.getRate());
            }

            try {
                dossierCurrencyRate = exchangeRateService.getExchangeRate(dossierCurrencyCode, effectiveDate);
                log.info("💱 Got exchange rate for {}: {}", dossierCurrencyCode, dossierCurrencyRate);
            } catch (Exception e) {
                log.error("Failed to get exchange rate for {} on date {}: {}",
                        dossierCurrencyCode, effectiveDate, e.getMessage());
                // Use a default fallback rate
                dossierCurrencyRate = createFallbackExchangeRate(dossierCurrencyCode, effectiveDate);
                log.info("💱 Using fallback exchange rate for {}: {}", dossierCurrencyCode, dossierCurrencyRate.getRate());
            }

            // Make sure both rates are not null before calculating conversion
            if (invoiceCurrencyRate == null) {
                invoiceCurrencyRate = createFallbackExchangeRate(invoiceCurrencyCode, effectiveDate);
                log.warn("💱 Using emergency fallback rate for invoice currency: {}", invoiceCurrencyCode);
            }

            if (dossierCurrencyRate == null) {
                dossierCurrencyRate = createFallbackExchangeRate(dossierCurrencyCode, effectiveDate);
                log.warn("💱 Using emergency fallback rate for dossier currency: {}", dossierCurrencyCode);
            }

            // Apply currency conversion rules
            double rate = calculateConversionRate(invoiceCurrencyCode, dossierCurrencyCode,
                    invoiceCurrencyRate, dossierCurrencyRate);
            log.info("💱 Final exchange rate: 1 {} = {} {}", invoiceCurrencyCode, rate, dossierCurrencyCode);
            return rate;
        } catch (Exception e) {
            log.error("💥 Error calculating exchange rate: {}", e.getMessage(), e);
            // Return a default conversion rate as fallback
            if ("MAD".equals(invoiceCurrencyCode) && "USD".equals(dossierCurrencyCode)) {
                return 0.1; // 1 MAD = 0.1 USD
            } else if ("USD".equals(invoiceCurrencyCode) && "MAD".equals(dossierCurrencyCode)) {
                return 10.0; // 1 USD = 10 MAD
            } else if ("EUR".equals(invoiceCurrencyCode) && "USD".equals(dossierCurrencyCode)) {
                return 1.1; // 1 EUR = 1.1 USD
            } else if ("USD".equals(invoiceCurrencyCode) && "EUR".equals(dossierCurrencyCode)) {
                return 0.91; // 1 USD = 0.91 EUR
            } else {
                return 1.0; // Default to 1:1 for unknown currency pairs
            }
        }
    }

    // Helper method to create fallback exchange rates
    private ExchangeRate createFallbackExchangeRate(String currencyCode, LocalDate date) {
        ExchangeRate rate = new ExchangeRate();
        rate.setCurrencyCode(currencyCode);
        rate.setDate(date);
        rate.setBaseCurrency("USD");

        // Set fallback rates based on common currencies
        switch (currencyCode) {
            case "USD":
                rate.setRate(1.0);
                break;
            case "EUR":
                rate.setRate(1.1);
                break;
            case "MAD":
                rate.setRate(10.0);
                break;
            case "GBP":
                rate.setRate(1.3);
                break;
            default:
                // For unknown currencies, default to 1:1 with USD
                rate.setRate(1.0);
                log.warn("No fallback rate known for currency {}, using 1.0", currencyCode);
        }

        return rate;
    }

    private LocalDate determineEffectiveDate(LocalDate invoiceDate) {
        LocalDate today = LocalDate.now();
        LocalDate jan1st2024 = LocalDate.of(2024, 1, 1);

        // Rule 1: If invoice date is before 2024, use Jan 1, 2024
        if (invoiceDate.isBefore(jan1st2024)) {
            log.info("📅 Invoice date {} is before 2024, using Jan 1, 2024 for exchange rate", invoiceDate);
            return jan1st2024;
        }

        // Rule 2: If invoice date is after or equal to today, use yesterday
        if (invoiceDate.isEqual(today) || invoiceDate.isAfter(today)) {
            log.info("📅 Invoice date {} is today or in the future, using yesterday's date ({}) for exchange rate",
                    invoiceDate, today.minusDays(1));
            return today.minusDays(1);
        }

        // Rule 3: Otherwise use the invoice date
        log.info("📅 Using actual invoice date {} for exchange rate", invoiceDate);
        return invoiceDate;
    }


    // Fixed calculateConversionRate method with null safety
    private double calculateConversionRate(
            String invoiceCurrencyCode,
            String dossierCurrencyCode,
            ExchangeRate invoiceCurrencyRate,
            ExchangeRate dossierCurrencyRate
    ) {
        // Safety check - shouldn't happen due to fallbacks in calling method
        if (invoiceCurrencyRate == null || dossierCurrencyRate == null) {
            log.error("💥 Null exchange rates in calculateConversionRate despite fallbacks");
            log.error("💥 Invoice currency: {}, rate: {}", invoiceCurrencyCode, invoiceCurrencyRate);
            log.error("💥 Dossier currency: {}, rate: {}", dossierCurrencyCode, dossierCurrencyRate);

            // Emergency fallback values
            if ("MAD".equals(invoiceCurrencyCode) && "USD".equals(dossierCurrencyCode)) {
                return 0.1; // 1 MAD = 0.1 USD
            } else if ("USD".equals(invoiceCurrencyCode) && "MAD".equals(dossierCurrencyCode)) {
                return 10.0; // 1 USD = 10 MAD
            } else {
                return 1.0; // Default fallback
            }
        }

        // Case 1: If invoice currency is USD and dossier currency is not USD
        if ("USD".equals(invoiceCurrencyCode)) {
            log.info("💱 Case 1: Invoice currency is USD, using direct USD→{} rate", dossierCurrencyCode);
            return dossierCurrencyRate.getRate();
        }

        // Case 2: If dossier currency is USD and invoice currency is not USD
        if ("USD".equals(dossierCurrencyCode)) {
            log.info("💱 Case 2: Dossier currency is USD, using inverse of USD→{} rate", invoiceCurrencyCode);
            return 1.0 / invoiceCurrencyRate.getRate();
        }

        // Case 3: Neither currency is USD
        log.info("💱 Case 3: Neither currency is USD, calculating cross rate");
        double rate = dossierCurrencyRate.getRate() / invoiceCurrencyRate.getRate();
        log.info("💱 Cross rate calculation: {} / {} = {}",
                dossierCurrencyRate.getRate(), invoiceCurrencyRate.getRate(), rate);
        return rate;
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

    // Update the buildFactureData method to normalize currency codes/symbols

    private FactureDataDTO buildFactureData(JsonNode entry) {
        FactureDataDTO factureData = new FactureDataDTO();

        // Set invoice number
        factureData.setInvoiceNumber(entry.get("FactureNum").asText());

        // Set invoice date directly
        try {
            if (entry.has("Date")) {
                String dateStr = entry.get("Date").asText();
                LocalDate localDate = parseDate(dateStr);
                factureData.setInvoiceDate(java.sql.Date.valueOf(localDate));
                log.info("Set invoice date in DTO to: {}", factureData.getInvoiceDate());
            }
        } catch (Exception e) {
            log.error("Failed to set invoice date in buildFactureData: {}", e.getMessage());
        }

        // Default TVA rate to null
        Double tvaRate = null;

        try {
            JsonNode tvaNode = entry.get("TVARate");
            if (tvaNode != null && !tvaNode.isNull()) {
                if (tvaNode.isNumber()) {
                    // If it's a number, use it directly
                    tvaRate = tvaNode.asDouble();
                } else {
                    // For any string value, try to extract numbers
                    String tvaText = tvaNode.asText().trim();
                    if (!tvaText.isEmpty()) {
                        // Extract first sequence of numbers (with possible decimal point)
                        String numberStr = tvaText.replaceAll("[^0-9.]", "");
                        if (!numberStr.isEmpty()) {
                            try {
                                tvaRate = Double.parseDouble(numberStr);
                            } catch (NumberFormatException ignored) {
                                // If parsing fails, keep tvaRate as null
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If any error occurs during TVA processing, just log and continue
            log.trace("Error processing TVA rate: {}", e.getMessage());
        }

        // Set total TTC and HT from AI if available
        if (entry.has("TotalTTC")) {
            factureData.setTotalTTC(parseDouble(entry.get("TotalTTC").asText()));
        } else {
            // Try to derive TTC from amounts in the transaction
            // This is a simple calculation - you might need to adjust based on your accounting rules
            Double debit = entry.has("DebitAmt") ? entry.get("DebitAmt").asDouble() : 0.0;
            Double credit = entry.has("CreditAmt") ? entry.get("CreditAmt").asDouble() : 0.0;
            // Use the larger of debit or credit as the TTC
            factureData.setTotalTTC(Math.max(debit, credit));
        }

        // Try to calculate HT from TTC and TVA rate if not provided
        if (entry.has("TotalHT")) {
            factureData.setTotalHT(parseDouble(entry.get("TotalHT").asText()));
        } else if (factureData.getTotalTTC() != null && tvaRate != null) {
            // Calculate HT from TTC and TVA rate
            factureData.setTotalHT(factureData.getTotalTTC() / (1 + (tvaRate / 100)));
        }

        // Set the TVA rate
        factureData.setTotalTVA(tvaRate);
        factureData.setTaxRate(tvaRate);

        // Calculate TVA amount if not directly provided
        if (factureData.getTotalTTC() != null && factureData.getTotalHT() != null && factureData.getTotalTVA() == null) {
            factureData.setTotalTVA(factureData.getTotalTTC() - factureData.getTotalHT());
        }

        // Set the currency information if available and normalize it
        if (entry.has("Devise")) {
            String rawCurrency = entry.get("Devise").asText();
            String normalizedCurrency = normalizeCurrencyCode.normalizeCurrencyCode(rawCurrency);

            factureData.setDevise(normalizedCurrency);
            factureData.setOriginalCurrency(normalizedCurrency);

            log.info("Set normalized currency in FactureData: {} (from '{}')",
                    normalizedCurrency, rawCurrency);
        }

        // Add currency conversion information if available
        if (entry.has("OriginalDevise")) {
            String rawCurrency = entry.get("OriginalDevise").asText();
            factureData.setOriginalCurrency(normalizeCurrencyCode.normalizeCurrencyCode(rawCurrency));
        }

        if (entry.has("ConvertedDevise") || entry.has("Devise")) {
            String rawCurrency = entry.has("ConvertedDevise") ?
                    entry.get("ConvertedDevise").asText() :
                    entry.get("Devise").asText();

            factureData.setConvertedCurrency(normalizeCurrencyCode.normalizeCurrencyCode(rawCurrency));
        }

        if (entry.has("ExchangeRate")) {
            factureData.setExchangeRate(parseDouble(entry.get("ExchangeRate").asText()));

            // If we have exchange rate, calculate converted amounts
            double rate = factureData.getExchangeRate();

            if (factureData.getTotalTTC() != null) {
                factureData.setConvertedTotalTTC(factureData.getTotalTTC() * rate);
            }

            if (factureData.getTotalHT() != null) {
                factureData.setConvertedTotalHT(factureData.getTotalHT() * rate);
            }

            if (factureData.getTotalTVA() != null) {
                factureData.setConvertedTotalTVA(factureData.getTotalTVA() * rate);
            }
        }

        if (entry.has("ExchangeRateDate")) {
            try {
                factureData.setExchangeRateDate(LocalDate.parse(entry.get("ExchangeRateDate").asText()));
            } catch (Exception e) {
                log.trace("Error parsing exchange rate date: {}", e.getMessage());
            }
        }

        // Set USD equivalents if available
        if (entry.has("UsdTotalTTC")) {
            factureData.setUsdTotalTTC(parseDouble(entry.get("UsdTotalTTC").asText()));
        }

        if (entry.has("UsdTotalHT")) {
            factureData.setUsdTotalHT(parseDouble(entry.get("UsdTotalHT").asText()));
        }

        if (entry.has("UsdTotalTVA")) {
            factureData.setUsdTotalTVA(parseDouble(entry.get("UsdTotalTVA").asText()));
        }

        log.info("Built FactureData DTO: invoiceNumber={}, invoiceDate={}, currency={}",
                factureData.getInvoiceNumber(), factureData.getInvoiceDate(), factureData.getDevise());

        return factureData;
    }

    // Helper method to safely parse double values
    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.trace("Error parsing double value: {}", value);
            return null;
        }
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

            // Check if we have original amounts - if so, this means conversion happened
            if (entry.has("OriginalDebitAmt")) {
                // Set original values
                line.setOriginalDebit(entry.get("OriginalDebitAmt").asDouble());
                // Set regular debit to original (unconverted) amount
                line.setDebit(entry.get("OriginalDebitAmt").asDouble());
                // Set converted value
                line.setConvertedDebit(entry.get("DebitAmt").asDouble());
            } else {
                // No conversion happened, just set the regular debit
                line.setDebit(entry.get("DebitAmt").asDouble());
            }

            if (entry.has("OriginalCreditAmt")) {
                // Set original values
                line.setOriginalCredit(entry.get("OriginalCreditAmt").asDouble());
                // Set regular credit to original (unconverted) amount
                line.setCredit(entry.get("OriginalCreditAmt").asDouble());
                // Set converted value
                line.setConvertedCredit(entry.get("CreditAmt").asDouble());
            } else {
                // No conversion happened, just set the regular credit
                line.setCredit(entry.get("CreditAmt").asDouble());
            }

            // Set currency information
            if (entry.has("OriginalDevise")) {
                line.setOriginalCurrency(entry.get("OriginalDevise").asText());
            }

            if (entry.has("Devise")) {
                line.setConvertedCurrency(entry.get("Devise").asText());
            }

            // Set exchange rate information
            if (entry.has("ExchangeRate")) {
                line.setExchangeRate(entry.get("ExchangeRate").asDouble());
            }

            if (entry.has("ExchangeRateDate")) {
                line.setExchangeRateDate(LocalDate.parse(entry.get("ExchangeRateDate").asText()));
            }

            // Set USD equivalents if available
            if (entry.has("UsdDebitAmt")) {
                line.setUsdDebit(entry.get("UsdDebitAmt").asDouble());
            }

            if (entry.has("UsdCreditAmt")) {
                line.setUsdCredit(entry.get("UsdCreditAmt").asDouble());
            }

            // Set account information
            AccountDTO account = new AccountDTO();
            account.setAccount(entry.get("CompteNum").asText());
            account.setLabel(entry.get("CompteLib").asText());
            line.setAccount(account);

            lines.add(line);
        }
        return lines;
    }
    private LocalDate parseDate(String dateStr) {
        try {
            // Trim any whitespace
            dateStr = dateStr.trim();

            // List of potential formatters to try
            List<DateTimeFormatter> formatters = Arrays.asList(
                    DateTimeFormatter.ofPattern("yyyy-MM-d"),   // Handle single-digit day
                    DateTimeFormatter.ofPattern("yyyy-M-dd"),   // Handle single-digit month
                    DateTimeFormatter.ofPattern("yyyy-M-d"),    // Handle single-digit month and day
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),  // Standard format
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),  // Alternative format
                    DateTimeFormatter.ofPattern("yyyy-dd-MM")   // Alternate order
            );

            for (DateTimeFormatter formatter : formatters) {
                try {
                    LocalDate parsedDate = LocalDate.parse(dateStr, formatter);

                    // Optionally log successful parsing
                    log.debug("Successfully parsed date: {} using format {}", dateStr, formatter);

                    return parsedDate;
                } catch (DateTimeParseException e) {
                    // Continue to next formatter
                    continue;
                }
            }

            // If no formatter works, log and use current date
            log.warn("❌ Could not parse date: {}. Using current date.", dateStr);
            return LocalDate.now();

        } catch (Exception e) {
            log.error("💥 Unexpected error parsing date {}: {}", dateStr, e.getMessage());
            return LocalDate.now();
        }
    }
}