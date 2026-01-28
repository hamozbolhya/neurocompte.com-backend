package com.pacioli.core.repositories;

import com.pacioli.core.models.Country;
import com.pacioli.core.models.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CountryRepository extends JpaRepository<Country, Long> {

    Optional<Country> findByCode(String code);

    List<Country> findByActive(boolean active);

    @Query("SELECT COUNT(c) > 0 FROM Country c")
    boolean hasAnyCountries();

    List<Country> findByCurrency(Currency currency);
}