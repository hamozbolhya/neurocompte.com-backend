package com.pacioli.core.batches.processors.converters;

import com.pacioli.core.models.ExchangeRate;
import com.pacioli.core.models.Piece;
import com.pacioli.core.services.ExchangeRateService;
import com.pacioli.core.utils.NormalizeCurrencyCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
public class CurrencyDataExtractionService {

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Autowired
    private NormalizeCurrencyCode normalizeCurrencyCode;

    public void calculateAndApplyExchangeRate(Piece piece, String sourceCurrency, String targetCurrency, LocalDate transactionDate) {
        if (sourceCurrency == null || targetCurrency == null) {
            applyDefaultCurrency(piece, targetCurrency);
            return;
        }

        String normalizedSource = normalizeCurrencyCode.normalizeCurrencyCode(sourceCurrency);
        String normalizedTarget = normalizeCurrencyCode.normalizeCurrencyCode(targetCurrency);

        if (normalizedSource.equals(normalizedTarget)) {
            piece.setExchangeRate(1.0);
            piece.setConvertedCurrency(normalizedTarget);
            log.info("💱 Same currency: {} = {}, no conversion needed", normalizedSource, normalizedTarget);
            return;
        }

        LocalDate effectiveDate = determineEffectiveDate(transactionDate);
        double exchangeRate = calculateExchangeRate(effectiveDate, normalizedSource, normalizedTarget);

        piece.setExchangeRate(exchangeRate);
        piece.setConvertedCurrency(normalizedTarget);
        piece.setExchangeRateDate(effectiveDate);

        log.info("💱 Applied currency conversion: {} → {}, rate: {}, date: {}",
                normalizedSource, normalizedTarget, exchangeRate, effectiveDate);
    }

    public void applyDefaultCurrency(Piece piece, String defaultCurrency) {
        String normalizedCurrency = normalizeCurrencyCode.normalizeCurrencyCode(defaultCurrency);
        piece.setConvertedCurrency(normalizedCurrency);
        piece.setExchangeRate(1.0);
        log.info("💱 Applied default currency: {}", normalizedCurrency);
    }

    private double calculateExchangeRate(LocalDate effectiveDate, String sourceCurrency, String targetCurrency) {
        try {
            ExchangeRate sourceRate = getExchangeRateWithFallback(sourceCurrency, effectiveDate);
            ExchangeRate targetRate = getExchangeRateWithFallback(targetCurrency, effectiveDate);

            return calculateConversionRate(sourceCurrency, targetCurrency, sourceRate, targetRate);
        } catch (Exception e) {
            log.error("💥 Error calculating exchange rate, using emergency fallback: {}", e.getMessage());
            return getEmergencyFallbackRate(sourceCurrency, targetCurrency);
        }
    }

    private ExchangeRate getExchangeRateWithFallback(String currencyCode, LocalDate date) {
        try {
            ExchangeRate rate = exchangeRateService.getExchangeRate(currencyCode, date);
            if (rate != null) {
                log.debug("💱 Got exchange rate for {}: {}", currencyCode, rate.getRate());
                return rate;
            }
        } catch (Exception e) {
            log.warn("⚠️ Using fallback exchange rate for {}: {}", currencyCode, e.getMessage());
        }

        return createFallbackExchangeRate(currencyCode, date);
    }

    private ExchangeRate createFallbackExchangeRate(String currencyCode, LocalDate date) {
        ExchangeRate rate = new ExchangeRate();
        rate.setCurrencyCode(currencyCode);
        rate.setDate(date);
        rate.setBaseCurrency("USD");

        Map<String, Double> fallbackRates = Map.of(
                "USD", 1.0,
                "EUR", 1.1,
                "MAD", 10.0,
                "GBP", 1.3,
                "TND", 3.1
        );

        rate.setRate(fallbackRates.getOrDefault(currencyCode, 1.0));
        log.info("💱 Using fallback rate for {}: {}", currencyCode, rate.getRate());
        return rate;
    }

    private double calculateConversionRate(String sourceCurrency, String targetCurrency,
                                           ExchangeRate sourceRate, ExchangeRate targetRate) {
        if ("USD".equals(sourceCurrency)) {
            return targetRate.getRate();
        }
        if ("USD".equals(targetCurrency)) {
            return 1.0 / sourceRate.getRate();
        }
        return targetRate.getRate() / sourceRate.getRate();
    }

    private double getEmergencyFallbackRate(String sourceCurrency, String targetCurrency) {
        Map<String, Double> emergencyRates = Map.of(
                "MAD_USD", 0.1,    "USD_MAD", 10.0,
                "TND_USD", 0.32,   "USD_TND", 3.1,
                "EUR_USD", 1.1,    "USD_EUR", 0.91
        );

        String key = sourceCurrency + "_" + targetCurrency;
        Double rate = emergencyRates.get(key);
        if (rate != null) {
            log.warn("🚨 Using emergency fallback rate for {}→{}: {}", sourceCurrency, targetCurrency, rate);
            return rate;
        }

        log.warn("🚨 Using default emergency rate 1.0 for {}→{}", sourceCurrency, targetCurrency);
        return 1.0;
    }

    private LocalDate determineEffectiveDate(LocalDate transactionDate) {
        LocalDate today = LocalDate.now();
        LocalDate jan1st2024 = LocalDate.of(2024, 1, 1);

        if (transactionDate.isBefore(jan1st2024)) {
            return jan1st2024;
        }
        if (transactionDate.isEqual(today) || transactionDate.isAfter(today)) {
            return today.minusDays(1);
        }
        return transactionDate;
    }
}