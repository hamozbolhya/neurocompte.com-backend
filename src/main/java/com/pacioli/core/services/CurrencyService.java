package com.pacioli.core.services;

import com.pacioli.core.models.Currency;
import java.util.List;
import java.util.Optional;

public interface CurrencyService {
    List<Currency> getAllCurrencies();
    List<Currency> getActiveCurrencies();
    Optional<Currency> getCurrencyByCode(String code);
    Currency saveCurrency(Currency currency);
    void initializeCurrencies();
}