package com.pacioli.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.service")
public class AiServiceProperties {

    private String apiKey;
    private String baseUrl;
}
