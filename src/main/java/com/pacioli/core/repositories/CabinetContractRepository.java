package com.pacioli.core.repositories;

import com.pacioli.core.models.CabinetContract;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CabinetContractRepository extends JpaRepository<CabinetContract, Long> {
    List<CabinetContract> findByCabinetIdOrderByStartDateDesc(Long cabinetId);
    Page<CabinetContract> findByCabinetIdOrderByStartDateDesc(Long cabinetId, Pageable pageable);
    Optional<CabinetContract> findFirstByCabinetIdAndActiveTrueOrderByStartDateDesc(Long cabinetId);

    @Query("SELECT c FROM CabinetContract c WHERE c.cabinet.id = :cabinetId AND :onDate >= c.startDate AND :onDate <= c.endDate ORDER BY c.startDate DESC")
    List<CabinetContract> findByCabinetIdAndDateInContract(@Param("cabinetId") Long cabinetId, @Param("onDate") LocalDate onDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = "UPDATE cabinet_contract SET normal_pieces_consumed = COALESCE(normal_pieces_consumed, 0) + :delta WHERE id = :id", nativeQuery = true)
    int incrementNormalPiecesConsumed(@Param("id") Long id, @Param("delta") long delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = "UPDATE cabinet_contract SET bank_pages_consumed = COALESCE(bank_pages_consumed, 0) + :delta WHERE id = :id", nativeQuery = true)
    int incrementBankPagesConsumed(@Param("id") Long id, @Param("delta") long delta);
}
