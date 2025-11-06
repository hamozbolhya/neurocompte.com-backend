package com.pacioli.core.config.batch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
class BatchProcessingConfigTest {

    @Autowired
    private BatchProcessingConfig batchConfig;

    @Test
    void testConfigurationLoading() {
        assertThat(batchConfig).isNotNull();
        assertThat(batchConfig.getBatchSize()).isEqualTo(20);
        assertThat(batchConfig.getMaxRetries()).isEqualTo(4);
        assertThat(batchConfig.getRetryDelayMs()).isEqualTo(30000L);
        assertThat(batchConfig.getThreadPoolSize()).isEqualTo(20);
    }

    @Test
    void testDefaultValues() {
        assertThat(batchConfig.getCorePoolSize()).isEqualTo(10);
        assertThat(batchConfig.getMaxPoolSize()).isEqualTo(50);
        assertThat(batchConfig.getQueueCapacity()).isEqualTo(100);
    }
}
