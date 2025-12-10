package com.pacioli.core.repositories;

import com.pacioli.core.models.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigurationRepository extends JpaRepository<Configuration, Long> {

    Optional<Configuration> findByConfigKey(String configKey);

    List<Configuration> findByIsActiveTrue();

    @Query("SELECT c FROM Configuration c WHERE c.configKey = :key AND c.isActive = true")
    Optional<Configuration> findActiveByKey(@Param("key") String key);

    @Modifying
    @Query("UPDATE Configuration c SET c.configValue = :value, c.updatedAt = CURRENT_TIMESTAMP, c.updatedBy = :updatedBy WHERE c.configKey = :key")
    void updateConfigValue(@Param("key") String key, @Param("value") String value, @Param("updatedBy") String updatedBy);
}