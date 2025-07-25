package com.pacioli.core.repositories;

import com.pacioli.core.models.Account;
import com.pacioli.core.models.Line;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

public interface LineRepository extends JpaRepository<Line, Long> {

    @Modifying
    @Query("UPDATE Line e SET e.account = :account WHERE e.id IN :ids")
    void updateCompteByIds(@Param("account") Account account, @Param("ids") List<Long> ids);
    @Query("SELECT COUNT(l) FROM Line l WHERE l.manuallyUpdated = true")
    Long countManuallyUpdatedLines();

    @Query("SELECT COUNT(l) FROM Line l WHERE l.manuallyUpdated = true AND l.manualUpdateDate BETWEEN :startDate AND :endDate")
    Long countManuallyUpdatedLinesInPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Count manually updated lines by cabinet
    @Query("SELECT l.ecriture.piece.dossier.cabinet.id, COUNT(l) FROM Line l WHERE l.manuallyUpdated = true GROUP BY l.ecriture.piece.dossier.cabinet.id")
    List<Object[]> countManuallyUpdatedLinesByCabinet();

    @Query("SELECT l.ecriture.piece.dossier.cabinet.id, COUNT(l) FROM Line l WHERE l.manuallyUpdated = true AND l.manualUpdateDate BETWEEN :startDate AND :endDate GROUP BY l.ecriture.piece.dossier.cabinet.id")
    List<Object[]> countManuallyUpdatedLinesByCabinetInPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(l) FROM Line l WHERE l.ecriture.piece.dossier.cabinet.id = :cabinetId AND l.manuallyUpdated = true AND l.manualUpdateDate BETWEEN :startDate AND :endDate")
    Long countManuallyUpdatedLinesByCabinetAndPeriod(@Param("cabinetId") Long cabinetId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
