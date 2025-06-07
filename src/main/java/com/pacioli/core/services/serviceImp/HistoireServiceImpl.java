package com.pacioli.core.services.serviceImp;

import com.pacioli.core.config.HistoireAiProperties;
import com.pacioli.core.services.HistoireService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoireServiceImpl implements HistoireService {

    private final RestTemplate restTemplate;
    private final HistoireAiProperties histoireAiProperties;

    private static final List<String> VALID_EXTENSIONS = Arrays.asList(".xlsx", ".csv");
    private static final List<String> VALID_MIME_TYPES = Arrays.asList(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv",
            "application/csv",
            "application/x-csv",
            "text/x-csv",
            "application/vnd.ms-excel",
            "" // Firefox might report empty string for CSV
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    @Override
    public String uploadHistoriqueFile(String fileName, MultipartFile file) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Starting AI file upload for: {}", requestId, fileName);

        try {
            validateFile(file);

            // Ensure base URL exists and clean it
            String baseUrl = histoireAiProperties.getBaseUrl();
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                throw new IllegalStateException("Histoire AI base URL is not configured");
            }
            baseUrl = baseUrl.replaceAll("/+$", "");

            // Split folder and filename
            String[] parts = fileName.split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Le nom du fichier doit contenir un dossier et un nom de fichier, séparés par un '/' ");
            }

            String folder = parts[0];
            // Replace the original filename with "balance.csv"
            String nameOnly = "balance.csv";

            // Compose final URL manually using %2F
            String finalUrl = baseUrl + "/" + folder + "%2F" + nameOnly;
            log.info("[{}] Final AI URL: {}, name of file {}", requestId, finalUrl, nameOnly);

            // Setup connection
            URL url = new URL(finalUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setRequestProperty("x-api-key", histoireAiProperties.getApiKey());
            conn.setRequestProperty("Content-Type", file.getContentType() != null ? file.getContentType() : "application/octet-stream");

            byte[] data = file.getBytes();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }

            int responseCode = conn.getResponseCode();
            log.info("[{}] AI response code: {}", requestId, responseCode);

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            if (responseCode == 200) {
                log.info("[{}] Upload success: {}", requestId, response);
                return "Le fichier a été transféré à l'IA avec succès !";
            } else {
                log.error("[{}] AI error: {} - {}", requestId, responseCode, response);
                throw new RuntimeException("Erreur lors de l'envoi à l'IA: " + response.toString());
            }

        } catch (Exception e) {
            log.error("[{}] Upload failure: {}", requestId, e.getMessage(), e);
            throw new RuntimeException("Erreur inattendue pendant l'envoi: " + e.getMessage());
        }
    }


    @Override
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier ne peut pas être vide");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            log.warn("File size validation failed: {} bytes exceeds {} bytes limit",
                    file.getSize(), MAX_FILE_SIZE);
            throw new IllegalArgumentException("La taille du fichier dépasse la limite de 5 Mo");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("Le nom du fichier est requis");
        }

        boolean hasValidExtension = VALID_EXTENSIONS.stream()
                .anyMatch(ext -> fileName.toLowerCase().endsWith(ext));

        String contentType = file.getContentType();
        boolean hasValidMimeType = contentType == null || VALID_MIME_TYPES.contains(contentType);

        if (!hasValidExtension || !hasValidMimeType) {
            log.warn("File validation failed for: {} (type: {})", fileName, contentType);
            throw new IllegalArgumentException(
                    "Type de fichier non pris en charge. Veuillez sélectionner un fichier XLSX ou CSV"
            );
        }

        log.debug("File validation passed for: {} (size: {} bytes, type: {})",
                fileName, file.getSize(), contentType);
    }
}
