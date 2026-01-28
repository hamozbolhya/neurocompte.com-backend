package com.pacioli.core.services;

import com.pacioli.core.models.Country;
import com.pacioli.core.models.Currency;
import java.util.List;
import java.util.Optional;

public interface CountryService {
    List<Country> getAllCountries();
    List<Country> getActiveCountries();
    Optional<Country> getCountryByCode(String code);
    Country saveCountry(Country country);
    void initializeCountries();
    List<Country> getCountriesByCurrency(Currency currency);
    Country updateCountryCurrency(String countryCode, String currencyCode);
}