package com.pacioli.core.services.serviceImp;

import com.pacioli.core.config.HistoireAiProperties;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.HistoireService;
import com.pacioli.core.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoireServiceImpl implements HistoireService {

    private final HistoireAiProperties histoireAiProperties;
    private final AuditService auditService;
    private final UserService userService;

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

    // ✅ Méthode utilitaire pour récupérer le cabinet cible (à partir du dossier)
    private Long getTargetCabinetId(String dossierId) {
        // Ici, vous devriez récupérer le cabinet à partir du dossierId
        // Si vous avez un service pour ça, vous pouvez l'injecter
        // Pour l'instant, on retourne null si on ne peut pas le déterminer
        return null;
    }

    // ✅ Méthode utilitaire pour récupérer le nom du cabinet cible
    private String getTargetCabinetName(String dossierId) {
        return null;
    }

    @Override
    public String uploadHistoriqueFile(String dossierId, MultipartFile file, String fileType) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Starting AI file upload for dossier: {} with file type: {}", requestId, dossierId, fileType);

        Map<String, Object> fileDetails = new HashMap<>();
        fileDetails.put("dossierId", dossierId);
        fileDetails.put("fileType", fileType);
        fileDetails.put("fileName", file.getOriginalFilename());
        fileDetails.put("fileSize", file.getSize());

        // ✅ Récupérer le cabinet cible
        Long targetCabinetId = getTargetCabinetId(dossierId);
        String targetCabinetName = getTargetCabinetName(dossierId);

        try {
            validateFile(file);
            validateFileType(fileType);

            // Ensure base URL exists and clean it
            String baseUrl = histoireAiProperties.getBaseUrl();
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                throw new IllegalStateException("Histoire AI base URL is not configured");
            }
            baseUrl = baseUrl.replaceAll("/+$", "");

            // Determine filename based on file type
            String fileName;
            if ("balance".equals(fileType)) {
                fileName = "balance.csv";
            } else if ("fec".equals(fileType)) {
                fileName = "fec.csv";
            } else {
                throw new IllegalArgumentException("Type de fichier invalide. Utilisez 'balance' ou 'fec'");
            }

            // Compose final URL manually using %2F
            String finalUrl = baseUrl + "/" + dossierId + "%2F" + fileName;
            log.info("[{}] Final AI URL: {}, filename: {}", requestId, finalUrl, fileName);

            fileDetails.put("aiUrl", finalUrl);
            fileDetails.put("aiFilename", fileName);

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

            fileDetails.put("responseCode", responseCode);

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            fileDetails.put("responseMessage", response.toString());

            if (responseCode == 200) {
                log.info("[{}] Upload success: {}", requestId, response);

                // ✅ Audit avec cabinet cible
                auditService.logSuccessWithTargetCabinet(
                        userService.getCurrentUser(),
                        "AI_UPLOAD",
                        "Histoire",
                        dossierId,
                        "Dossier-" + dossierId,
                        null,
                        fileDetails,
                        targetCabinetId,
                        targetCabinetName
                );

                return "Le fichier a été transféré à l'IA avec succès !";
            } else {
                log.error("[{}] AI error: {} - {}", requestId, responseCode, response);

                // ✅ Audit d'échec avec cabinet cible
                auditService.logFailureWithTargetCabinet(
                        userService.getCurrentUser(),
                        "AI_UPLOAD",
                        "Histoire",
                        dossierId,
                        "Dossier-" + dossierId,
                        "AI error: " + responseCode + " - " + response.toString(),
                        targetCabinetId,
                        targetCabinetName
                );

                throw new RuntimeException("Erreur lors de l'envoi à l'IA: " + response.toString());
            }

        } catch (IllegalArgumentException e) {
            log.error("[{}] Validation failure: {}", requestId, e.getMessage());

            // ✅ Audit d'échec de validation avec cabinet cible
            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_UPLOAD",
                    "Histoire",
                    dossierId,
                    "Dossier-" + dossierId,
                    "Validation error: " + e.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );

            throw e;

        } catch (Exception e) {
            log.error("[{}] Upload failure: {}", requestId, e.getMessage(), e);

            // ✅ Audit d'échec inattendu avec cabinet cible
            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_UPLOAD",
                    "Histoire",
                    dossierId,
                    "Dossier-" + dossierId,
                    "Unexpected error: " + e.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );

            throw new RuntimeException("Erreur inattendue pendant l'envoi: " + e.getMessage());
        }
    }

    // Add this validation method
    private void validateFileType(String fileType) {
        if (fileType == null || fileType.trim().isEmpty()) {
            throw new IllegalArgumentException("Le type de fichier est requis");
        }

        if (!"balance".equals(fileType) && !"fec".equals(fileType)) {
            throw new IllegalArgumentException("Type de fichier invalide. Les types acceptés sont: 'balance' ou 'fec'");
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