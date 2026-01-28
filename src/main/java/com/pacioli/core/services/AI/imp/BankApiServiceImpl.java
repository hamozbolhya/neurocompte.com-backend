package com.pacioli.core.services.AI.imp;

import com.pacioli.core.DTO.AI.BankStatementGetResponse;
import com.pacioli.core.DTO.AI.BankStatementRequest;
import com.pacioli.core.DTO.AI.BankStatementResponse;
import com.pacioli.core.config.BankApiProperties;
import com.pacioli.core.services.AI.services.BankApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class BankApiServiceImpl implements BankApiService {

    private final RestTemplate restTemplate;
    private final BankApiProperties bankApiProperties;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("pdf", "jpeg", "jpg", "png");

    @Autowired
    public BankApiServiceImpl(RestTemplate restTemplate, BankApiProperties bankApiProperties) {
        this.restTemplate = restTemplate;
        this.bankApiProperties = bankApiProperties;
    }

    @Override
    public BankStatementResponse uploadBankStatement(BankStatementRequest request) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String originalFilename = request.getFile() != null ? request.getFile().getOriginalFilename() : "unknown";

        log.info("🏦 [{}] Starting bank statement upload - Dossier: {}, File: {}, Size: {} bytes",
                requestId, request.getDossierId(), originalFilename,
                request.getFile() != null ? request.getFile().getSize() : 0);

        try {
            // Validate request
            if (request.getDossierId() == null) {
                log.error("❌ [{}] Validation failed: Dossier ID is null", requestId);
                return BankStatementResponse.error("Dossier ID is required", HttpStatus.BAD_REQUEST, "NO_DOSSIER_ID");
            }

            if (request.getFile() == null || request.getFile().isEmpty()) {
                log.error("❌ [{}] Validation failed: File is null or empty", requestId);
                return BankStatementResponse.error("File is required", HttpStatus.BAD_REQUEST, "NO_FILE");
            }

            // Validate file extension
            if (!isValidFileExtension(request.getFile())) {
                log.error("❌ [{}] Validation failed: Invalid file extension for {}", requestId, originalFilename);
                return BankStatementResponse.error("Invalid file type. Allowed types: " + String.join(", ", ALLOWED_EXTENSIONS),
                        HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE");
            }

            // Validate file size
            if (!isValidFileSize(request.getFile())) {
                long maxSize = parseFileSize(bankApiProperties.getMaxFileSize());
                log.error("❌ [{}] Validation failed: File size {} bytes exceeds maximum {} bytes",
                        requestId, request.getFile().getSize(), maxSize);
                return BankStatementResponse.error("File size exceeds maximum allowed: " + bankApiProperties.getMaxFileSize(),
                        HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE");
            }

            // Generate file ID if not provided
            String fileId = request.getFileId();
            if (fileId == null || fileId.trim().isEmpty()) {
                fileId = UUID.randomUUID().toString();
                log.debug("🔧 [{}] Generated new file ID: {}", requestId, fileId);
            }

            // Get file extension
            String fileExtension = StringUtils.getFilenameExtension(originalFilename).toLowerCase();

            // Generate the URL
            String fileUrl = generateFileUrl(request.getDossierId(), fileId, fileExtension);
            log.info("🔄 [{}] Uploading to external bank API: {}", requestId, fileUrl);

            // Prepare the request with appropriate content type
            HttpHeaders headers = createHeadersForFileType(fileExtension);
            byte[] fileBytes = request.getFile().getBytes();
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileBytes, headers);

            log.debug("📤 [{}] Sending PUT request with {} bytes, Content-Type: {}",
                    requestId, fileBytes.length, headers.getContentType());

            // Make the PUT request with detailed response logging
            ResponseEntity<String> response = executeApiCall(requestId, fileUrl, requestEntity);

            return handleApiResponse(requestId, request, fileId, fileUrl, fileExtension, response);

        } catch (IOException e) {
            log.error("💥 [{}] IO Error reading file {}: {}", requestId, originalFilename, e.getMessage(), e);
            return BankStatementResponse.error("Error reading file: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, "FILE_READ_ERROR");
        } catch (Exception e) {
            log.error("💥 [{}] Unexpected error during bank statement upload: {}", requestId, e.getMessage(), e);
            return BankStatementResponse.error("Unexpected error: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR");
        }
    }

    private HttpHeaders createHeadersForFileType(String fileExtension) {
        HttpHeaders headers = new HttpHeaders();

        switch (fileExtension) {
            case "pdf":
                headers.setContentType(MediaType.APPLICATION_PDF);
                break;
            case "jpeg":
            case "jpg":
                headers.setContentType(MediaType.IMAGE_JPEG);
                break;
            case "png":
                headers.setContentType(MediaType.IMAGE_PNG);
                break;
            default:
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }

        return headers;
    }

    private ResponseEntity<String> executeApiCall(String requestId, String fileUrl, HttpEntity<byte[]> requestEntity) {
        try {
            long startTime = System.currentTimeMillis();

            // Use URI.create() to prevent RestTemplate from re-encoding
            URI uri = URI.create(fileUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    uri,  // Use URI instead of String URL
                    HttpMethod.PUT,
                    requestEntity,
                    String.class
            );

            long duration = System.currentTimeMillis() - startTime;

            log.info("✅ [{}] External API call successful - Status: {}, Duration: {}ms",
                    requestId, response.getStatusCode(), duration);
            log.debug("📥 [{}] External API response body: {}", requestId, response.getBody());

            return response;

        } catch (HttpClientErrorException e) {
            log.error("❌ [{}] Client error from external API - Status: {}, Response: {}",
                    requestId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw e;
        } catch (HttpServerErrorException e) {
            log.error("❌ [{}] Server error from external API - Status: {}, Response: {}",
                    requestId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw e;
        } catch (Exception e) {
            log.error("❌ [{}] Network/IO error calling external API: {}", requestId, e.getMessage(), e);
            throw e;
        }
    }

    private BankStatementResponse handleApiResponse(String requestId, BankStatementRequest request,
                                                    String fileId, String fileUrl, String fileExtension,
                                                    ResponseEntity<String> response) {
        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("🎉 [{}] Bank statement upload COMPLETED - Dossier: {}, File: {}, URL: {}",
                    requestId, request.getDossierId(), request.getFile().getOriginalFilename(), fileUrl);

            return BankStatementResponse.success(
                    fileUrl,
                    fileId,
                    request.getDossierId(),
                    request.getFile().getOriginalFilename(),
                    request.getFile().getSize(),
                    response.getBody()
            );
        } else {
            log.error("❌ [{}] External API returned non-success status: {} - Response: {}",
                    requestId, response.getStatusCode(), response.getBody());

            return BankStatementResponse.error(
                    "External API returned status: " + response.getStatusCode(),
                    HttpStatus.valueOf(response.getStatusCodeValue()),
                    response.getBody()
            );
        }
    }

    @Override
    public boolean isValidFileExtension(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) {
            return false;
        }

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null) {
            return false;
        }

        return ALLOWED_EXTENSIONS.contains(extension.toLowerCase());
    }

    @Override
    public String generateFileUrl(Long dossierId, String fileId, String fileExtension) {
        // Manual encoding - only encode the dossierId and fileId separately
        try {
            String encodedDossierId = URLEncoder.encode(dossierId.toString(), StandardCharsets.UTF_8);
            String encodedFilePath = encodedDossierId + "%2F" + fileId + "." + fileExtension;
            return bankApiProperties.getUrl() + "/" + encodedFilePath;
        } catch (Exception e) {
            log.error("Error encoding URL: {}", e.getMessage());
            // Fallback - simple concatenation
            return bankApiProperties.getUrl() + "/" + dossierId + "%2F" + fileId + "." + fileExtension;
        }
    }

    @Override
    public List<String> getAllowedExtensions() {
        return ALLOWED_EXTENSIONS;
    }

    @Override
    public boolean isValidFileSize(MultipartFile file) {
        long maxFileSize = parseFileSize(bankApiProperties.getMaxFileSize());
        return file.getSize() <= maxFileSize;
    }

    private long parseFileSize(String size) {
        if (size == null) {
            return 10 * 1024 * 1024; // Default 10MB
        }

        try {
            size = size.toUpperCase();
            if (size.endsWith("MB")) {
                return Long.parseLong(size.replace("MB", "").trim()) * 1024 * 1024;
            } else if (size.endsWith("KB")) {
                return Long.parseLong(size.replace("KB", "").trim()) * 1024;
            } else if (size.endsWith("B")) {
                return Long.parseLong(size.replace("B", "").trim());
            } else {
                return Long.parseLong(size);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid file size format: {}, using default 10MB", size);
            return 10 * 1024 * 1024;
        }
    }

    // Convenience method for direct usage
    public BankStatementResponse uploadBankStatement(Long dossierId, MultipartFile file) {
        return uploadBankStatement(new BankStatementRequest(dossierId, file));
    }

    public BankStatementResponse uploadBankStatement(Long dossierId, MultipartFile file, String fileId) {
        return uploadBankStatement(new BankStatementRequest(dossierId, file, fileId));
    }

    @Override
    public BankStatementGetResponse getBankStatementResult(String fileId) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.info("🏦 [{}] Getting bank statement result for fileId: {}", requestId, fileId);

        try {
            // Validate fileId
            if (fileId == null || fileId.trim().isEmpty()) {
                log.error("❌ [{}] Validation failed: File ID is null or empty", requestId);
                return BankStatementGetResponse.error("File ID is required", HttpStatus.BAD_REQUEST, "NO_FILE_ID");
            }

            // Remove .json extension if present (to avoid double extension)
            String cleanFileId = fileId.replace(".json", "");

            // Generate the URL
            String fileUrl = generateGetFileUrl(cleanFileId);
            log.info("🔄 [{}] Fetching from external bank API: {}", requestId, fileUrl);

            // Prepare headers with API key
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", "u4d9s6oPlh8BGnlfFkT4P1iuZKKP9heZ5uvJ57pL"); // Your API key
            headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

            HttpEntity<String> requestEntity = new HttpEntity<>(headers);

            // Make the GET request
            ResponseEntity<String> response = executeGetApiCall(requestId, fileUrl, requestEntity);

            return handleGetApiResponse(requestId, cleanFileId, response);

        } catch (Exception e) {
            log.error("💥 [{}] Unexpected error during bank statement retrieval: {}", requestId, e.getMessage(), e);
            return BankStatementGetResponse.error("Unexpected error: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR");
        }
    }

    /**
     * Generates the URL for GET requests
     */
    private String generateGetFileUrl(String fileId) {
        // Format: {baseUrl}/bank/{fileId}.json
        return bankApiProperties.getUrl() + "/" + fileId + ".json";
    }

    /**
     * Executes the GET API call
     */
    private ResponseEntity<String> executeGetApiCall(String requestId, String fileUrl, HttpEntity<String> requestEntity) {
        try {
            long startTime = System.currentTimeMillis();

            // Use URI.create() to prevent encoding issues
            URI uri = URI.create(fileUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );

            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ [{}] External GET API call successful - Status: {}, Duration: {}ms",
                    requestId, response.getStatusCode(), duration);
            log.debug("📥 [{}] External GET API response body: {}", requestId, response.getBody());

            return response;

        } catch (HttpClientErrorException e) {
            log.error("❌ [{}] Client error from external GET API - Status: {}, Response: {}",
                    requestId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw e;
        } catch (HttpServerErrorException e) {
            log.error("❌ [{}] Server error from external GET API - Status: {}, Response: {}",
                    requestId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw e;
        } catch (Exception e) {
            log.error("❌ [{}] Network/IO error calling external GET API: {}", requestId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Handles the GET API response
     */
    private BankStatementGetResponse handleGetApiResponse(String requestId, String fileId, ResponseEntity<String> response) {
        if (response.getStatusCode().is2xxSuccessful()) {
            String responseBody = response.getBody();
            log.info("🎉 [{}] Bank statement result retrieved successfully - FileId: {}, Response length: {}",
                    requestId, fileId, responseBody != null ? responseBody.length() : 0);

            return BankStatementGetResponse.success(
                    fileId,
                    responseBody, // The JSON content
                    responseBody  // Also store as raw response
            );
        } else {
            log.error("❌ [{}] External GET API returned non-success status: {} - Response: {}",
                    requestId, response.getStatusCode(), response.getBody());

            return BankStatementGetResponse.error(
                    "External API returned status: " + response.getStatusCode(),
                    HttpStatus.valueOf(response.getStatusCodeValue()),
                    response.getBody()
            );
        }
    }
}