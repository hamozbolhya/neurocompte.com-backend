package com.pacioli.core.controllers;

import com.pacioli.core.models.ExchangeRate;
import com.pacioli.core.repositories.ExchangeRateRepository;
import com.pacioli.core.services.ExchangeRateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/exchange-rates")
public class ExchangeRateController {

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @GetMapping
    public ResponseEntity<List<ExchangeRate>> getExchangeRates(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (date != null) {
            return ResponseEntity.ok(exchangeRateRepository.findByDate(date));
        } else {
            return ResponseEntity.ok(exchangeRateRepository.findAll());
        }
    }

    @PostMapping("/initialize")
    public ResponseEntity<String> initializeHistoricalData() {
        exchangeRateService.initializeHistoricalData();
        return ResponseEntity.ok("Historical data initialization triggered");
    }

    @PostMapping("/fetch-today")
    public ResponseEntity<List<ExchangeRate>> fetchTodayRates() {
        List<ExchangeRate> rates = exchangeRateService.fetchAndSaveLatestRates();
        return ResponseEntity.ok(rates);
    }

    @PostMapping("/update-missing")
    public ResponseEntity<String> updateMissingDays() {
        exchangeRateService.updateMissingDays();
        return ResponseEntity.ok("Missing days update triggered");
    }

    @PostMapping("/fetch-date")
    public ResponseEntity<List<ExchangeRate>> fetchSpecificDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        // Check if we already have data for this date
        List<ExchangeRate> existingRates = exchangeRateRepository.findByDate(date);
        if (!existingRates.isEmpty()) {
            return ResponseEntity.ok(existingRates);
        }

        // If date is today, fetch actual rates
        if (date.isEqual(LocalDate.now())) {
            return ResponseEntity.ok(exchangeRateService.fetchAndSaveLatestRates());
        }

        // For historical dates, we can't fetch real data with the free plan
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(List.of());
    }
}