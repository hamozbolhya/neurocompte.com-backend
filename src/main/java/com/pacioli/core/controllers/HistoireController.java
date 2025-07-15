package com.pacioli.core.controllers;

import com.pacioli.core.services.HistoireService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/histoire")
@RequiredArgsConstructor
public class HistoireController {

    private final HistoireService histoireService;

    /**
     * Upload a historical file to the AI service
     * Using ** to capture the full path including nested folders and special characters
     *
     * @param file The file to upload
     * @param request HTTP request to extract the full path
     * @return Success response or error details
     */
    @PutMapping(value = "/**", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadHistorique(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileType", required = true) String fileType,
            HttpServletRequest request) {

        String requestId = UUID.randomUUID().toString();

        // Extract the filename from the request URI
        String requestUri = request.getRequestURI();
        String pathAfterApi = requestUri.substring("/api/histoire/".length());
        String dossierId = pathAfterApi.split("/")[0];

        log.info("[{}] Request to upload historical file: {} with type: {} for dossier: {}",
                requestId, file.getOriginalFilename(), fileType, dossierId);

        // Log request details for debugging
        log.debug("[{}] Full request URI: {}", requestId, requestUri);
        log.debug("[{}] File size: {} bytes", requestId, file.getSize());
        log.debug("[{}] File content type: {}", requestId, file.getContentType());

        try {
            // Upload file through service
            String result = histoireService.uploadHistoriqueFile(dossierId, file, fileType);
            log.info("[{}] File upload completed successfully", requestId);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            // Client errors (validation failures)
            log.error("[{}] Validation error: {}", requestId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (RuntimeException e) {
            // Server errors (AI service failures, etc.)
            log.error("[{}] Service error: {}", requestId, e.getMessage(), e);

            // Check if the error message contains specific HTTP status information
            if (e.getMessage().contains("400 BAD_REQUEST")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            } else if (e.getMessage().contains("401 UNAUTHORIZED") || e.getMessage().contains("403 FORBIDDEN")) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Erreur d'authentification avec le service IA");
            } else if (e.getMessage().contains("404 NOT_FOUND")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service IA non disponible");
            } else if (e.getMessage().contains("500 INTERNAL_SERVER_ERROR")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erreur du service IA");
            } else {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        } catch (Exception e) {
            // Unexpected errors
            log.error("[{}] Unexpected error during file upload", requestId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Une erreur inattendue est survenue lors du traitement du fichier");
        }
    }

    /**
     * Simple GET method for testing CORS
     */
    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        return ResponseEntity.ok("Histoire endpoint is working!");
    }
}