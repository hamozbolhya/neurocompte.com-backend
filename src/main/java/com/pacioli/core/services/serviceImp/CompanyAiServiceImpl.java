package com.pacioli.core.services.serviceImp;

import com.pacioli.core.DTO.Company;
import com.pacioli.core.config.AiServiceProperties;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.CompanyAiService;
import com.pacioli.core.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyAiServiceImpl implements CompanyAiService {

    private final RestTemplate restTemplate;
    private final AiServiceProperties properties;
    private final AuditService auditService;
    private final UserService userService;

    // ✅ Méthode utilitaire pour récupérer le cabinet cible
    // Note: Pour CompanyAiService, le cabinet cible n'est pas directement accessible
    // On pourrait l'ajouter comme paramètre si nécessaire, ou le récupérer via le companyId
    private Long getTargetCabinetId(Long companyId) {
        // Si vous avez un moyen de récupérer le cabinet à partir du companyId
        // Par exemple via un repository, vous pouvez l'implémenter ici
        // Pour l'instant, on retourne null
        return null;
    }

    private String getTargetCabinetName(Long companyId) {
        return null;
    }

    @Override
    public Company createCompany(Company company) {
        String requestId = UUID.randomUUID().toString();
        log.info("API Request [{}] - Creating company: {}", requestId, company);

        // ✅ Récupérer le cabinet cible
        Long targetCabinetId = getTargetCabinetId(company.getId());
        String targetCabinetName = getTargetCabinetName(company.getId());

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("companyId", company.getId());
        auditDetails.put("companyName", company.getName());
        auditDetails.put("country", company.getCountry());
        auditDetails.put("activity", company.getActivity());
        auditDetails.put("requestId", requestId);
        auditDetails.put("apiUrl", properties.getBaseUrl());

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", properties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Company> requestEntity = new HttpEntity<>(company, headers);

        String url = properties.getBaseUrl();
        log.debug("API Request [{}] - URL: {}, Headers: {}, Body: {}", requestId, url, headers, company);

        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<Company> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Company.class);
            long duration = System.currentTimeMillis() - startTime;

            HttpStatus statusCode = (HttpStatus) responseEntity.getStatusCode();
            Company responseBody = responseEntity.getBody();
            HttpHeaders responseHeaders = responseEntity.getHeaders();

            log.info("API Response [{}] - Status: {}, Duration: {}ms", requestId, statusCode, duration);
            log.debug("API Response [{}] - Headers: {}", requestId, responseHeaders);
            log.debug("API Response [{}] - Body: {}", requestId, responseBody);

            if (statusCode.is2xxSuccessful()) {
                log.info("API Request [{}] - Company created successfully with ID: {}", requestId, responseBody != null ? responseBody.getId() : "unknown");

                // Audit succès avec cabinet cible
                auditDetails.put("responseCode", statusCode.value());
                auditDetails.put("duration", duration);
                auditDetails.put("responseCompanyId", responseBody != null ? responseBody.getId() : null);

                auditService.logSuccessWithTargetCabinet(
                        userService.getCurrentUser(),
                        "AI_CREATE",
                        "Company",
                        company.getId(),
                        company.getName(),
                        null,
                        auditDetails,
                        targetCabinetId,
                        targetCabinetName
                );

                return responseBody;
            } else {
                log.error("API Request [{}] - Non-success status code: {}", requestId, statusCode);

                // Audit échec avec cabinet cible
                auditDetails.put("responseCode", statusCode.value());
                auditDetails.put("errorMessage", "Non-success status code: " + statusCode);

                auditService.logFailureWithTargetCabinet(
                        userService.getCurrentUser(),
                        "AI_CREATE",
                        "Company",
                        company.getId(),
                        company.getName(),
                        "Failed to create company: " + statusCode,
                        targetCabinetId,
                        targetCabinetName
                );

                throw new RuntimeException("Failed to create company: " + statusCode);
            }
        } catch (HttpStatusCodeException e) {
            // For HTTP error status codes (4xx, 5xx)
            logHttpError(requestId, e);

            // Audit échec HTTP avec cabinet cible
            auditDetails.put("responseCode", e.getStatusCode().value());
            auditDetails.put("errorMessage", e.getResponseBodyAsString());

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_CREATE",
                    "Company",
                    company.getId(),
                    company.getName(),
                    "API Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(),
                    targetCabinetId,
                    targetCabinetName
            );

            throw new RuntimeException("API Error - Failed to create company: " + e.getStatusCode() + ", Response: " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            // For connectivity issues
            log.error("API Request [{}] - Connection error: {}", requestId, e.getMessage(), e);

            // Audit échec connexion avec cabinet cible
            auditDetails.put("errorMessage", e.getMessage());

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_CREATE",
                    "Company",
                    company.getId(),
                    company.getName(),
                    "Connection error: " + e.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );

            throw new RuntimeException("API Connectivity Error - Failed to create company: " + e.getMessage(), e);
        } catch (RestClientException e) {
            // Other REST client errors
            log.error("API Request [{}] - REST client error: {}", requestId, e.getMessage(), e);

            // Audit échec client avec cabinet cible
            auditDetails.put("errorMessage", e.getMessage());

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_CREATE",
                    "Company",
                    company.getId(),
                    company.getName(),
                    "REST client error: " + e.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );

            throw new RuntimeException("API Client Error - Failed to create company: " + e.getMessage(), e);
        } catch (Exception e) {
            // Unexpected errors
            log.error("API Request [{}] - Unexpected error: {}", requestId, e.getMessage(), e);

            // Audit erreur inattendue avec cabinet cible
            auditDetails.put("errorMessage", e.getMessage());

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_CREATE",
                    "Company",
                    company.getId(),
                    company.getName(),
                    "Unexpected error: " + e.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );

            throw new RuntimeException("Unexpected error creating company: " + e.getMessage(), e);
        }
    }

    @Override
    public Company updateCompany(Long companyId, Company company) {
        String requestId = UUID.randomUUID().toString();
        log.info("API Request [{}] - Updating company with ID: {}, Company data: {}", requestId, companyId, company);

        // ✅ Récupérer le cabinet cible
        Long targetCabinetId = getTargetCabinetId(companyId);
        String targetCabinetName = getTargetCabinetName(companyId);

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("companyId", companyId);
        auditDetails.put("companyName", company.getName());
        auditDetails.put("country", company.getCountry());
        auditDetails.put("activity", company.getActivity());
        auditDetails.put("requestId", requestId);
        auditDetails.put("apiUrl", properties.getBaseUrl() + "/" + companyId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", properties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Company> requestEntity = new HttpEntity<>(company, headers);

        String url = properties.getBaseUrl() + "/" + companyId;
        log.debug("API Request [{}] - URL: {}, Headers: {}, Body: {}", requestId, url, headers, company);

        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<Company> responseEntity = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, Company.class);
            long duration = System.currentTimeMillis() - startTime;

            HttpStatus statusCode = (HttpStatus) responseEntity.getStatusCode();
            Company responseBody = responseEntity.getBody();
            HttpHeaders responseHeaders = responseEntity.getHeaders();

            log.info("API Response [{}] - Status: {}, Duration: {}ms", requestId, statusCode, duration);
            log.debug("API Response [{}] - Headers: {}", requestId, responseHeaders);
            log.debug("API Response [{}] - Body: {}", requestId, responseBody);

            if (statusCode.is2xxSuccessful()) {
                log.info("API Request [{}] - Company updated successfully with ID: {}", requestId, responseBody != null ? responseBody.getId() : "unknown");

                // Audit succès avec cabinet cible
                auditDetails.put("responseCode", statusCode.value());
                auditDetails.put("duration", duration);
                auditDetails.put("responseCompanyId", responseBody != null ? responseBody.getId() : null);

                auditService.logSuccessWithTargetCabinet(
                        userService.getCurrentUser(),
                        "AI_UPDATE",
                        "Company",
                        companyId,
                        company.getName(),
                        null,
                        auditDetails,
                        targetCabinetId,
                        targetCabinetName
                );

                return responseBody;
            } else {
                log.error("API Request [{}] - Non-success status code: {}", requestId, statusCode);

                // Audit échec avec cabinet cible
                auditDetails.put("responseCode", statusCode.value());
                auditDetails.put("errorMessage", "Non-success status code: " + statusCode);

                auditService.logFailureWithTargetCabinet(
                        userService.getCurrentUser(),
                        "AI_UPDATE",
                        "Company",
                        companyId,
                        company.getName(),
                        "Failed to update company: " + statusCode,
                        targetCabinetId,
                        targetCabinetName
                );

                throw new RuntimeException("Failed to update company: " + statusCode);
            }
        } catch (HttpStatusCodeException e) {
            // For HTTP error status codes (4xx, 5xx)
            logHttpError(requestId, e);

            // Audit échec HTTP avec cabinet cible
            auditDetails.put("responseCode", e.getStatusCode().value());
            auditDetails.put("errorMessage", e.getResponseBodyAsString());

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_UPDATE",
                    "Company",
                    companyId,
                    company.getName(),
                    "API Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(),
                    targetCabinetId,
                    targetCabinetName
            );

            throw new RuntimeException("API Error - Failed to update company: " + e.getStatusCode() + ", Response: " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            // For connectivity issues
            log.error("API Request [{}] - Connection error: {}", requestId, e.getMessage(), e);

            // Audit échec connexion avec cabinet cible
            auditDetails.put("errorMessage", e.getMessage());

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_UPDATE",
                    "Company",
                    companyId,
                    company.getName(),
                    "Connection error: " + e.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );

            throw new RuntimeException("API Connectivity Error - Failed to update company: " + e.getMessage(), e);
        } catch (RestClientException e) {
            // Other REST client errors
            log.error("API Request [{}] - REST client error: {}", requestId, e.getMessage(), e);

            // Audit échec client avec cabinet cible
            auditDetails.put("errorMessage", e.getMessage());

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_UPDATE",
                    "Company",
                    companyId,
                    company.getName(),
                    "REST client error: " + e.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );

            throw new RuntimeException("API Client Error - Failed to update company: " + e.getMessage(), e);
        } catch (Exception e) {
            // Unexpected errors
            log.error("API Request [{}] - Unexpected error: {}", requestId, e.getMessage(), e);

            // Audit erreur inattendue avec cabinet cible
            auditDetails.put("errorMessage", e.getMessage());

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_UPDATE",
                    "Company",
                    companyId,
                    company.getName(),
                    "Unexpected error: " + e.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );

            throw new RuntimeException("Unexpected error updating company: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteCompany(Long companyId) {
        String requestId = UUID.randomUUID().toString();
        log.info("API Request [{}] - Deleting company with ID: {}", requestId, companyId);

        // ✅ Récupérer le cabinet cible
        Long targetCabinetId = getTargetCabinetId(companyId);
        String targetCabinetName = getTargetCabinetName(companyId);

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("companyId", companyId);
        auditDetails.put("requestId", requestId);
        auditDetails.put("apiUrl", properties.getBaseUrl() + "/" + companyId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", properties.getApiKey());

        HttpEntity<?> requestEntity = new HttpEntity<>(headers);

        String url = properties.getBaseUrl() + "/" + companyId;
        log.debug("API Request [{}] - URL: {}, Headers: {}", requestId, url, headers);

        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<Void> responseEntity = restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, Void.class);
            long duration = System.currentTimeMillis() - startTime;

            HttpStatus statusCode = (HttpStatus) responseEntity.getStatusCode();
            HttpHeaders responseHeaders = responseEntity.getHeaders();

            log.info("API Response [{}] - Status: {}, Duration: {}ms", requestId, statusCode, duration);
            log.debug("API Response [{}] - Headers: {}", requestId, responseHeaders);

            if (statusCode.is2xxSuccessful()) {
                log.info("API Request [{}] - Company with ID {} deleted successfully", requestId, companyId);

                // Audit succès avec cabinet cible
                auditDetails.put("responseCode", statusCode.value());
                auditDetails.put("duration", duration);
                auditDetails.put("deleted", true);

                auditService.logSuccessWithTargetCabinet(
                        userService.getCurrentUser(),
                        "AI_DELETE",
                        "Company",
                        companyId,
                        "Company-" + companyId,
                        null,
                        auditDetails,
                        targetCabinetId,
                        targetCabinetName
                );

                return true;
            } else {
                log.error("API Request [{}] - Non-success status code: {}", requestId, statusCode);

                // Audit échec avec cabinet cible
                auditDetails.put("responseCode", statusCode.value());
                auditDetails.put("deleted", false);
                auditDetails.put("errorMessage", "Non-success status code: " + statusCode);

                auditService.logFailureWithTargetCabinet(
                        userService.getCurrentUser(),
                        "AI_DELETE",
                        "Company",
                        companyId,
                        "Company-" + companyId,
                        "Failed to delete company: " + statusCode,
                        targetCabinetId,
                        targetCabinetName
                );

                return false;
            }
        } catch (HttpStatusCodeException e) {
            // For HTTP error status codes (4xx, 5xx)
            logHttpError(requestId, e);

            // Audit échec HTTP avec cabinet cible
            auditDetails.put("responseCode", e.getStatusCode().value());
            auditDetails.put("deleted", false);
            auditDetails.put("errorMessage", e.getResponseBodyAsString());

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_DELETE",
                    "Company",
                    companyId,
                    "Company-" + companyId,
                    "API Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(),
                    targetCabinetId,
                    targetCabinetName
            );

            log.error("API Request [{}] - Failed to delete company with ID: {}", requestId, companyId);
            return false;
        } catch (ResourceAccessException e) {
            // For connectivity issues
            log.error("API Request [{}] - Connection error while deleting company with ID {}: {}", requestId, companyId, e.getMessage(), e);

            // Audit échec connexion avec cabinet cible
            auditDetails.put("deleted", false);
            auditDetails.put("errorMessage", e.getMessage());

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_DELETE",
                    "Company",
                    companyId,
                    "Company-" + companyId,
                    "Connection error: " + e.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );

            return false;
        } catch (RestClientException e) {
            // Other REST client errors
            log.error("API Request [{}] - REST client error while deleting company with ID {}: {}", requestId, companyId, e.getMessage(), e);

            // Audit échec client avec cabinet cible
            auditDetails.put("deleted", false);
            auditDetails.put("errorMessage", e.getMessage());

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_DELETE",
                    "Company",
                    companyId,
                    "Company-" + companyId,
                    "REST client error: " + e.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );

            return false;
        } catch (Exception e) {
            // Unexpected errors
            log.error("API Request [{}] - Unexpected error while deleting company with ID {}: {}", requestId, companyId, e.getMessage(), e);

            // Audit erreur inattendue avec cabinet cible
            auditDetails.put("deleted", false);
            auditDetails.put("errorMessage", e.getMessage());

            auditService.logFailureWithTargetCabinet(
                    userService.getCurrentUser(),
                    "AI_DELETE",
                    "Company",
                    companyId,
                    "Company-" + companyId,
                    "Unexpected error: " + e.getMessage(),
                    targetCabinetId,
                    targetCabinetName
            );

            return false;
        }
    }

    private void logHttpError(String requestId, HttpStatusCodeException e) {
        HttpStatus statusCode = (HttpStatus) e.getStatusCode();
        String responseBody = e.getResponseBodyAsString();
        HttpHeaders responseHeaders = e.getResponseHeaders();

        log.error("API Request [{}] - HTTP error status: {}", requestId, statusCode);
        log.error("API Request [{}] - Error response headers: {}", requestId, responseHeaders);
        log.error("API Request [{}] - Error response body: {}", requestId, responseBody);
    }
}