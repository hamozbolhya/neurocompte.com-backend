package com.pacioli.core.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@Slf4j
public class LoggingConfiguration {

    @Value("${logs.dir}")
    private String logsDirectory;

    @PostConstruct
    public void initializeLoggingDirectories() {
        try {
            // Create LOGS directory if it doesn't exist
            Path logsPath = Paths.get(logsDirectory);
            if (!Files.exists(logsPath)) {
                Files.createDirectories(logsPath);
                log.info("✅ Created LOGS directory: {}", logsPath.toAbsolutePath());
            } else {
                log.info("📁 LOGS directory already exists: {}", logsPath.toAbsolutePath());
            }

            log.info("🗂️ Daily logs will be stored in: {}/DD-MM-YYYY.log format", logsDirectory);

        } catch (IOException e) {
            log.error("❌ Failed to create LOGS directory: {}", logsDirectory, e);
            throw new RuntimeException("Cannot initialize logging directory: " + logsDirectory, e);
        }
    }
}