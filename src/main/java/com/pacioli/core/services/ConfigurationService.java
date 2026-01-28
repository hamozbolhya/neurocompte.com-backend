package com.pacioli.core.services;

import com.pacioli.core.models.Configuration;
import com.pacioli.core.repositories.ConfigurationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationService {

    private final ConfigurationRepository configurationRepository;

    private final Map<String, String> configCache = new HashMap<>();
    private Instant lastCacheRefresh = Instant.now();
    private static final Duration CACHE_TTL = Duration.ofMinutes(1); // Refresh every minute

    @PostConstruct
    public void init() {
        refreshCache();
        log.info("Configuration service initialized with {} configurations", configCache.size());
    }

    @Scheduled(fixedDelay = 60000) // Refresh cache every minute
    public void scheduledCacheRefresh() {
        refreshCache();
    }

    private synchronized void refreshCache() {
        try {
            List<Configuration> activeConfigs = configurationRepository.findByIsActiveTrue();
            Map<String, String> newCache = new HashMap<>();

            for (Configuration config : activeConfigs) {
                newCache.put(config.getConfigKey(), config.getConfigValue());
            }

            configCache.clear();
            configCache.putAll(newCache);
            lastCacheRefresh = Instant.now();

            log.debug("Configuration cache refreshed. {} configurations loaded", configCache.size());
        } catch (Exception e) {
            log.error("Failed to refresh configuration cache", e);
        }
    }

    public String getConfigValue(String key, String defaultValue) {
        // Check cache first
        if (configCache.containsKey(key)) {
            return configCache.get(key);
        }

        // If not in cache, try to get from database
        try {
            Optional<Configuration> config = configurationRepository.findActiveByKey(key);
            if (config.isPresent()) {
                String value = config.get().getConfigValue();
                configCache.put(key, value); // Update cache
                return value;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch configuration for key: {}", key, e);
        }

        return defaultValue;
    }

    public boolean getBooleanConfig(String key, boolean defaultValue) {
        String value = getConfigValue(key, String.valueOf(defaultValue));
        try {
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            log.warn("Invalid boolean value for config key {}: {}", key, value);
            return defaultValue;
        }
    }

    public int getIntConfig(String key, int defaultValue) {
        String value = getConfigValue(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            log.warn("Invalid integer value for config key {}: {}", key, value);
            return defaultValue;
        }
    }

    @Transactional
    public void updateConfig(String key, String value, String updatedBy) {
        Optional<Configuration> existingConfig = configurationRepository.findByConfigKey(key);

        if (existingConfig.isPresent()) {
            Configuration config = existingConfig.get();
            config.setConfigValue(value);
            config.setUpdatedBy(updatedBy);
            configurationRepository.save(config);
        } else {
            Configuration newConfig = new Configuration();
            newConfig.setConfigKey(key);
            newConfig.setConfigValue(value);
            newConfig.setDescription("Auto-generated configuration");
            newConfig.setCreatedBy(updatedBy);
            newConfig.setUpdatedBy(updatedBy);
            configurationRepository.save(newConfig);
        }

        // Update cache
        configCache.put(key, value);
        log.info("Configuration updated: {} = {} by {}", key, value, updatedBy);
    }

    @Transactional
    public void updateBooleanConfig(String key, boolean value, String updatedBy) {
        updateConfig(key, String.valueOf(value), updatedBy);
    }

    @Transactional
    public void updateIntConfig(String key, int value, String updatedBy) {
        updateConfig(key, String.valueOf(value), updatedBy);
    }

    public Map<String, String> getAllConfigurations() {
        return new HashMap<>(configCache);
    }

    public void forceRefreshCache() {
        refreshCache();
    }
}