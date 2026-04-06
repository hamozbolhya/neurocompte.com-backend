package com.pacioli.core.repositories;

import com.pacioli.core.models.CabinetContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CabinetContractRepository extends JpaRepository<CabinetContract, Long> {
    List<CabinetContract> findByCabinetIdOrderByStartDateDesc(Long cabinetId);
    Optional<CabinetContract> findFirstByCabinetIdAndActiveTrueOrderByStartDateDesc(Long cabinetId);
}
