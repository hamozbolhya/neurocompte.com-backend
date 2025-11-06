package com.pacioli.core.config.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Autowired
    private BatchProcessingConfig batchConfig;

    @Bean(name = "batchTaskExecutor")
    public Executor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(batchConfig.getCorePoolSize());
        executor.setMaxPoolSize(batchConfig.getMaxPoolSize());
        executor.setQueueCapacity(batchConfig.getQueueCapacity());
        executor.setThreadNamePrefix("BatchProcessor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("✅ Batch task executor configured: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                batchConfig.getCorePoolSize(), batchConfig.getMaxPoolSize(), batchConfig.getQueueCapacity());

        return executor;
    }
}