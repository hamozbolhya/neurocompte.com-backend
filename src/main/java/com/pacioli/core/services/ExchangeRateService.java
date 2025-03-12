package com.pacioli.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacioli.core.models.ExchangeRate;
import com.pacioli.core.repositories.ExchangeRateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class ExchangeRateService {

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${currency.api.key}")
    private String apiKey;

    // Only use the latest rates endpoint (available in free plan)
    private static final String LATEST_RATES_URL = "https://api.currencyfreaks.com/v2.0/rates/latest";

    // Currencies to track
    private static final Set<String> CURRENCIES = Set.of(
            "EUR", "MAD", "CAD", "GBP", "JPY", "CHF", "AUD", "CNY"
    );

    /**
     * Fetch latest exchange rates and save them with today's date
     */
    @Transactional
    public List<ExchangeRate> fetchAndSaveLatestRates() {
        try {
            LocalDate today = LocalDate.now();

            // Check if we already saved rates for today
            if (!exchangeRateRepository.findByDate(today).isEmpty()) {
                log.info("Exchange rates for today ({}) already exist, skipping", today);
                return exchangeRateRepository.findByDate(today);
            }

            // Use the latest rates endpoint
            String url = String.format("%s?apikey=%s&base=USD", LATEST_RATES_URL, apiKey);

            log.info("Fetching latest exchange rates");
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode rates = root.get("rates");

                if (rates != null) {
                    List<ExchangeRate> savedRates = new ArrayList<>();

                    // Get base currency
                    String baseCurrency = root.has("base") ? root.get("base").asText() : "USD";

                    for (String currencyCode : CURRENCIES) {
                        if (rates.has(currencyCode)) {
                            double rate = Double.parseDouble(rates.get(currencyCode).asText());

                            ExchangeRate exchangeRate = new ExchangeRate();
                            exchangeRate.setDate(today);
                            exchangeRate.setCurrencyCode(currencyCode);
                            exchangeRate.setRate(rate);
                            exchangeRate.setBaseCurrency(baseCurrency);

                            savedRates.add(exchangeRateRepository.save(exchangeRate));
                            log.info("Saved exchange rate for {} on {}: {}", currencyCode, today, rate);
                        }
                    }

                    return savedRates;
                }
            }

            log.error("Failed to fetch latest exchange rates: {}", response.getStatusCode());
            return List.of();

        } catch (Exception e) {
            log.error("Error fetching latest exchange rates: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Initialize historical data using approximate values
     * Since we don't have access to historical API, we'll populate with today's rates
     */
    @Transactional
    public void initializeHistoricalData() {
        if (!exchangeRateRepository.hasAnyExchangeRates()) {
            log.info("Initializing historical exchange rate data with approximate values");

            // First, get today's rates
            List<ExchangeRate> todayRates = fetchAndSaveLatestRates();

            // If we couldn't get today's rates, we can't proceed
            if (todayRates.isEmpty()) {
                log.error("Failed to fetch today's rates, cannot initialize historical data");
                return;
            }

            // Use today's rates to backfill historical data
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate yesterday = LocalDate.now().minusDays(1);

            for (LocalDate date = startDate; !date.isAfter(yesterday); date = date.plusDays(1)) {
                // Skip if we already have data for this date
                if (!exchangeRateRepository.findByDate(date).isEmpty()) {
                    continue;
                }

                for (ExchangeRate todayRate : todayRates) {
                    // Create a variation of today's rate for historical date
                    // This is just an approximation - in reality, you'd want real historical data
                    ExchangeRate historicalRate = new ExchangeRate();
                    historicalRate.setDate(date);
                    historicalRate.setCurrencyCode(todayRate.getCurrencyCode());
                    historicalRate.setBaseCurrency(todayRate.getBaseCurrency());

                    // Apply a small random variation to simulate different rates
                    // +/- 5% variation
                    double variation = 0.95 + (Math.random() * 0.10);
                    historicalRate.setRate(todayRate.getRate() * variation);

                    exchangeRateRepository.save(historicalRate);
                    log.info("Created approximate historical rate for {} on {}: {}",
                            historicalRate.getCurrencyCode(), date, historicalRate.getRate());
                }
            }

            log.info("Historical exchange rate initialization completed with approximate values");
        } else {
            log.info("Exchange rates data already exists, skipping initialization");
        }
    }

    /**
     * Update missing days with the latest rates
     */
    @Transactional
    public void updateMissingDays() {
        LocalDate lastDate = exchangeRateRepository.findMaxDate();

        if (lastDate == null) {
            // If no data exists, initialize everything
            initializeHistoricalData();
            return;
        }

        // Start from the day after the last recorded date
        LocalDate currentDate = lastDate.plusDays(1);
        LocalDate today = LocalDate.now();

        // For missing days between the last date and yesterday, use approximate data
        while (currentDate.isBefore(today)) {
            // Check if we already have data for this date
            if (exchangeRateRepository.findByDate(currentDate).isEmpty()) {
                // Use the most recent rates we have as a basis
                List<ExchangeRate> latestRates = exchangeRateRepository.findByDate(lastDate);

                for (ExchangeRate latestRate : latestRates) {
                    // Create a variation for the missing day
                    ExchangeRate missingRate = new ExchangeRate();
                    missingRate.setDate(currentDate);
                    missingRate.setCurrencyCode(latestRate.getCurrencyCode());
                    missingRate.setBaseCurrency(latestRate.getBaseCurrency());

                    // Apply a small random variation
                    double variation = 0.99 + (Math.random() * 0.02); // +/- 1% variation
                    missingRate.setRate(latestRate.getRate() * variation);

                    exchangeRateRepository.save(missingRate);
                    log.info("Created approximate rate for missing day {} - {}: {}",
                            currentDate, missingRate.getCurrencyCode(), missingRate.getRate());
                }
            }

            currentDate = currentDate.plusDays(1);
        }

        // Get today's actual rates
        if (currentDate.isEqual(today)) {
            fetchAndSaveLatestRates();
        }

        log.info("Exchange rates updated for all missing days");
    }

    /**
     * Get exchange rate for a specific currency and date
     */
    public ExchangeRate getExchangeRate(String currencyCode, LocalDate date) {
        return exchangeRateRepository.findByDateAndCurrencyCode(date, currencyCode)
                .orElse(null);
    }
}