package com.pacioli.core.services.serviceImp;

import com.pacioli.core.DTO.Company;
import com.pacioli.core.config.AiServiceProperties;
import com.pacioli.core.services.CompanyAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyAiServiceImpl implements CompanyAiService {

    private final RestTemplate restTemplate;
    private final AiServiceProperties properties;

    @Override
    public Company createCompany(Company company) {
        String requestId = UUID.randomUUID().toString();
        log.info("API Request [{}] - Creating company: {}", requestId, company);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", properties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Company> requestEntity = new HttpEntity<>(company, headers);

        String url = properties.getBaseUrl();
        log.debug("API Request [{}] - URL: {}, Headers: {}, Body: {}", requestId, url, headers, company);

        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<Company> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    Company.class
            );
            long duration = System.currentTimeMillis() - startTime;

            HttpStatus statusCode = (HttpStatus) responseEntity.getStatusCode();
            Company responseBody = responseEntity.getBody();
            HttpHeaders responseHeaders = responseEntity.getHeaders();

            log.info("API Response [{}] - Status: {}, Duration: {}ms", requestId, statusCode, duration);
            log.debug("API Response [{}] - Headers: {}", requestId, responseHeaders);
            log.debug("API Response [{}] - Body: {}", requestId, responseBody);

            if (statusCode.is2xxSuccessful()) {
                log.info("API Request [{}] - Company created successfully with ID: {}", requestId,
                        responseBody != null ? responseBody.getId() : "unknown");
                return responseBody;
            } else {
                log.error("API Request [{}] - Non-success status code: {}", requestId, statusCode);
                throw new RuntimeException("Failed to create company: " + statusCode);
            }
        } catch (HttpStatusCodeException e) {
            // For HTTP error status codes (4xx, 5xx)
            logHttpError(requestId, e);
            throw new RuntimeException("API Error - Failed to create company: " + e.getStatusCode() +
                    ", Response: " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            // For connectivity issues
            log.error("API Request [{}] - Connection error: {}", requestId, e.getMessage(), e);
            throw new RuntimeException("API Connectivity Error - Failed to create company: " + e.getMessage(), e);
        } catch (RestClientException e) {
            // Other REST client errors
            log.error("API Request [{}] - REST client error: {}", requestId, e.getMessage(), e);
            throw new RuntimeException("API Client Error - Failed to create company: " + e.getMessage(), e);
        } catch (Exception e) {
            // Unexpected errors
            log.error("API Request [{}] - Unexpected error: {}", requestId, e.getMessage(), e);
            throw new RuntimeException("Unexpected error creating company: " + e.getMessage(), e);
        }
    }

    @Override
    public Company updateCompany(Long companyId, Company company) {
        String requestId = UUID.randomUUID().toString();
        log.info("API Request [{}] - Updating company with ID: {}, Company data: {}", requestId, companyId, company);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", properties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Company> requestEntity = new HttpEntity<>(company, headers);

        String url = properties.getBaseUrl() + "/" + companyId;
        log.debug("API Request [{}] - URL: {}, Headers: {}, Body: {}", requestId, url, headers, company);

        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<Company> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    requestEntity,
                    Company.class
            );
            long duration = System.currentTimeMillis() - startTime;

            HttpStatus statusCode = (HttpStatus) responseEntity.getStatusCode();
            Company responseBody = responseEntity.getBody();
            HttpHeaders responseHeaders = responseEntity.getHeaders();

            log.info("API Response [{}] - Status: {}, Duration: {}ms", requestId, statusCode, duration);
            log.debug("API Response [{}] - Headers: {}", requestId, responseHeaders);
            log.debug("API Response [{}] - Body: {}", requestId, responseBody);

            if (statusCode.is2xxSuccessful()) {
                log.info("API Request [{}] - Company updated successfully with ID: {}", requestId,
                        responseBody != null ? responseBody.getId() : "unknown");
                return responseBody;
            } else {
                log.error("API Request [{}] - Non-success status code: {}", requestId, statusCode);
                throw new RuntimeException("Failed to update company: " + statusCode);
            }
        } catch (HttpStatusCodeException e) {
            // For HTTP error status codes (4xx, 5xx)
            logHttpError(requestId, e);
            throw new RuntimeException("API Error - Failed to update company: " + e.getStatusCode() +
                    ", Response: " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            // For connectivity issues
            log.error("API Request [{}] - Connection error: {}", requestId, e.getMessage(), e);
            throw new RuntimeException("API Connectivity Error - Failed to update company: " + e.getMessage(), e);
        } catch (RestClientException e) {
            // Other REST client errors
            log.error("API Request [{}] - REST client error: {}", requestId, e.getMessage(), e);
            throw new RuntimeException("API Client Error - Failed to update company: " + e.getMessage(), e);
        } catch (Exception e) {
            // Unexpected errors
            log.error("API Request [{}] - Unexpected error: {}", requestId, e.getMessage(), e);
            throw new RuntimeException("Unexpected error updating company: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteCompany(Long companyId) {
        String requestId = UUID.randomUUID().toString();
        log.info("API Request [{}] - Deleting company with ID: {}", requestId, companyId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", properties.getApiKey());

        HttpEntity<?> requestEntity = new HttpEntity<>(headers);

        String url = properties.getBaseUrl() + "/" + companyId;
        log.debug("API Request [{}] - URL: {}, Headers: {}", requestId, url, headers);

        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<Void> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    requestEntity,
                    Void.class
            );
            long duration = System.currentTimeMillis() - startTime;

            HttpStatus statusCode = (HttpStatus) responseEntity.getStatusCode();
            HttpHeaders responseHeaders = responseEntity.getHeaders();

            log.info("API Response [{}] - Status: {}, Duration: {}ms", requestId, statusCode, duration);
            log.debug("API Response [{}] - Headers: {}", requestId, responseHeaders);

            if (statusCode.is2xxSuccessful()) {
                log.info("API Request [{}] - Company with ID {} deleted successfully", requestId, companyId);
                return true;
            } else {
                log.error("API Request [{}] - Non-success status code: {}", requestId, statusCode);
                return false;
            }
        } catch (HttpStatusCodeException e) {
            // For HTTP error status codes (4xx, 5xx)
            logHttpError(requestId, e);
            log.error("API Request [{}] - Failed to delete company with ID: {}", requestId, companyId);
            return false;
        } catch (ResourceAccessException e) {
            // For connectivity issues
            log.error("API Request [{}] - Connection error while deleting company with ID {}: {}",
                    requestId, companyId, e.getMessage(), e);
            return false;
        } catch (RestClientException e) {
            // Other REST client errors
            log.error("API Request [{}] - REST client error while deleting company with ID {}: {}",
                    requestId, companyId, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            // Unexpected errors
            log.error("API Request [{}] - Unexpected error while deleting company with ID {}: {}",
                    requestId, companyId, e.getMessage(), e);
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