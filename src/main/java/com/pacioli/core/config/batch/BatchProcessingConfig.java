package com.pacioli.core.config.batch;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "batch.processing")
public class BatchProcessingConfig {
    private int batchSize = 20;
    private int maxRetries = 4;
    private long retryDelayMs = 30000;
    private int threadPoolSize = 20;
    private int corePoolSize = 10;
    private int maxPoolSize = 50;
    private int queueCapacity = 100;
}