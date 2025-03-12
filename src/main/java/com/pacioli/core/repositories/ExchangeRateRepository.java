package com.pacioli.core.repositories;

import com.pacioli.core.models.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByDateAndCurrencyCode(LocalDate date, String currencyCode);

    List<ExchangeRate> findByDate(LocalDate date);

    @Query("SELECT COUNT(er) > 0 FROM ExchangeRate er")
    boolean hasAnyExchangeRates();

    @Query("SELECT MAX(er.date) FROM ExchangeRate er")
    LocalDate findMaxDate();
}