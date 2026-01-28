package com.pacioli.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bank.api")
public class BankApiProperties {
    private String url;
    private String stage;
    private String maxFileSize;
    private String allowedExtensions;
}