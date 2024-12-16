package com.pacioli.core.repositories;

import com.pacioli.core.models.FactureData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FactureDataRepository extends JpaRepository<FactureData, Long> {
}

