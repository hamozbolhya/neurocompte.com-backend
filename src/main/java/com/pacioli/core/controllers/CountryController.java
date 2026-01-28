package com.pacioli.core.controllers;

import com.pacioli.core.models.Country;
import com.pacioli.core.services.CountryService;
import com.pacioli.core.services.CurrencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/countries")
public class CountryController {

    @Autowired
    private CountryService countryService;

    @Autowired
    private CurrencyService currencyService;

    @GetMapping
    public ResponseEntity<List<Country>> getAllCountries(
            @RequestParam(required = false, defaultValue = "true") boolean activeOnly) {

        if (activeOnly) {
            return ResponseEntity.ok(countryService.getActiveCountries());
        } else {
            return ResponseEntity.ok(countryService.getAllCountries());
        }
    }

    @GetMapping("/{code}")
    public ResponseEntity<Country> getCountryByCode(@PathVariable String code) {
        return countryService.getCountryByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Country> createCountry(@RequestBody Country country) {
        return ResponseEntity.ok(countryService.saveCountry(country));
    }

    @PutMapping("/{code}")
    public ResponseEntity<Country> updateCountry(
            @PathVariable String code,
            @RequestBody Country country) {

        return countryService.getCountryByCode(code)
                .map(existingCountry -> {
                    country.setId(existingCountry.getId());
                    return ResponseEntity.ok(countryService.saveCountry(country));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/currency/{currencyCode}")
    public ResponseEntity<List<Country>> getCountriesByCurrency(@PathVariable String currencyCode) {
        return currencyService.getCurrencyByCode(currencyCode)
                .map(currency -> ResponseEntity.ok(countryService.getCountriesByCurrency(currency)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{code}/currency/{currencyCode}")
    public ResponseEntity<Country> updateCountryCurrency(
            @PathVariable String code,
            @PathVariable String currencyCode) {

        try {
            Country updatedCountry = countryService.updateCountryCurrency(code, currencyCode);
            return ResponseEntity.ok(updatedCountry);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/initialize")
    public ResponseEntity<String> initializeCountries() {
        countryService.initializeCountries();
        return ResponseEntity.ok("Country initialization triggered");
    }
}