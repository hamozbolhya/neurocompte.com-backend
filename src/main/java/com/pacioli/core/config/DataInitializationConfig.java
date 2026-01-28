package com.pacioli.core.config;

import com.pacioli.core.services.CountryService;
import com.pacioli.core.services.CurrencyService;
import com.pacioli.core.services.ExchangeRateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Slf4j
@Configuration
public class DataInitializationConfig {

    @Autowired
    private CountryService countryService;

    @Autowired
    private CurrencyService currencyService;

    @Autowired
    private ExchangeRateService exchangeRateService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeData() {
        log.info("Starting data initialization...");

        // Initialize currencies first (since countries depend on them)
        currencyService.initializeCurrencies();

        // Initialize countries with currency relationships
        countryService.initializeCountries();

        // Initialize exchange rates (reusing the existing service)
        exchangeRateService.updateMissingDays();

        log.info("Data initialization completed");
    }
}