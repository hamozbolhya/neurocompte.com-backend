package com.pacioli.core.controllers;

import com.pacioli.core.models.Currency;
import com.pacioli.core.services.CurrencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/currencies")
public class CurrencyController {

    @Autowired
    private CurrencyService currencyService;

    @GetMapping
    public ResponseEntity<List<Currency>> getAllCurrencies(
            @RequestParam(required = false, defaultValue = "true") boolean activeOnly) {

        if (activeOnly) {
            return ResponseEntity.ok(currencyService.getActiveCurrencies());
        } else {
            return ResponseEntity.ok(currencyService.getAllCurrencies());
        }
    }

    @GetMapping("/{code}")
    public ResponseEntity<Currency> getCurrencyByCode(@PathVariable String code) {
        return currencyService.getCurrencyByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Currency> createCurrency(@RequestBody Currency currency) {
        return ResponseEntity.ok(currencyService.saveCurrency(currency));
    }

    @PutMapping("/{code}")
    public ResponseEntity<Currency> updateCurrency(
            @PathVariable String code,
            @RequestBody Currency currency) {

        return currencyService.getCurrencyByCode(code)
                .map(existingCurrency -> {
                    currency.setId(existingCurrency.getId());
                    return ResponseEntity.ok(currencyService.saveCurrency(currency));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/initialize")
    public ResponseEntity<String> initializeCurrencies() {
        currencyService.initializeCurrencies();
        return ResponseEntity.ok("Currency initialization triggered");
    }
}