package com.pacioli.core.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the Histoire AI service
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "histoire.ai")
public class HistoireAiProperties {

    /**
     * API key for authentication with the Histoire AI service
     */
    private String apiKey;

    /**
     * Base URL of the Histoire AI service
     */
    private String baseUrl;

    @PostConstruct
    public void logProperties() {
        log.info("Histoire AI Properties loaded:");
        log.info("  - Base URL: {}", baseUrl != null ? baseUrl : "NOT CONFIGURED");
        log.info("  - API Key: {}", apiKey != null ? "SET" : "NOT SET");

        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            log.error("Histoire AI base URL is not configured! Please check application.properties for 'histoire.ai.base-url'");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("Histoire AI API key is not configured! Please check application.properties for 'histoire.ai.api-key'");
        }
    }
}