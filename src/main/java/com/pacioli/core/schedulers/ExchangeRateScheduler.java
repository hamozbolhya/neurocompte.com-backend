package com.pacioli.core.schedulers;

import com.pacioli.core.services.ExchangeRateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
public class ExchangeRateScheduler {

    @Autowired
    private ExchangeRateService exchangeRateService;

    /**
     * Initialize on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationStart() {
        log.info("Initializing exchange rate data on application startup");
        exchangeRateService.updateMissingDays();
    }

    /**
     * Fetch exchange rates every day at 1:00 AM
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void fetchDailyExchangeRates() {
        log.info("Running scheduled exchange rate update");
        exchangeRateService.fetchAndSaveLatestRates();
    }
}