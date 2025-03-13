package com.pacioli.core.repositories;

import com.pacioli.core.models.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, Long> {

    Optional<Currency> findByCode(String code);

    List<Currency> findByActive(boolean active);

    @Query("SELECT COUNT(c) > 0 FROM Currency c")
    boolean hasAnyCurrencies();
}