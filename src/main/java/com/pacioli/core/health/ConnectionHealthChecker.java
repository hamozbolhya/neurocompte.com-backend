package com.pacioli.core.health;


import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Slf4j
public class ConnectionHealthChecker {

    @Autowired
    private DataSource dataSource;

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void checkConnectionPoolHealth() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolProxy = hikariDataSource.getHikariPoolMXBean();

            int activeConnections = poolProxy.getActiveConnections();
            int idleConnections = poolProxy.getIdleConnections();
            int totalConnections = poolProxy.getTotalConnections();
            int threadsAwaiting = poolProxy.getThreadsAwaitingConnection();

            log.info("📊 Connection Pool Stats: Active={}, Idle={}, Total={}, Waiting={}",
                    activeConnections, idleConnections, totalConnections, threadsAwaiting);

            if (threadsAwaiting > 5) {
                log.warn("⚠️ High connection wait: {} threads waiting", threadsAwaiting);
            }

            // Soft evict idle connections if pool is full
            if (idleConnections > 15 && totalConnections >= hikariDataSource.getMaximumPoolSize() - 5) {
                log.info("🧹 Soft evicting idle connections");
                poolProxy.softEvictConnections();
            }
        }
    }
}