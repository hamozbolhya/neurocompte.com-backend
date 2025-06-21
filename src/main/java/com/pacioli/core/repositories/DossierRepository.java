package com.pacioli.core.repositories;

import com.pacioli.core.DTO.DossierDTO;
import com.pacioli.core.models.Dossier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DossierRepository extends JpaRepository<Dossier, Long> {
    Optional<Dossier> findByName(String name);

    @Query("SELECT NEW com.pacioli.core.DTO.DossierDTO(" +
            "d.id, " +
            "d.name, " +
            "d.ICE, " +
            "d.address, " +
            "d.city, " +
            "d.phone, " +
            "d.email, " +
            "d.country.name, " +
            "d.country.code, " +
            "d.country.currency.code, " +
            "d.country.currency.name, " +
            "d.decimalPrecision) " +
            "FROM Dossier d WHERE d.id = :id")
    Optional<DossierDTO> findDossierById(@Param("id") Long id);

    @Query("SELECT NEW com.pacioli.core.DTO.DossierDTO(" +
            "d.id, " +
            "d.name, " +
            "d.ICE, " +
            "d.address, " +
            "d.city, " +
            "d.phone, " +
            "d.email, " +
            "d.country.name, " +
            "d.country.code, " +
            "d.country.currency.code, " +
            "d.country.currency.name, " +
            "d.decimalPrecision) " +  // ADD THIS LINE
            "FROM Dossier d WHERE d.cabinet.id = :cabinetId")
    Page<DossierDTO> findDossierDTOsByCabinetId(@Param("cabinetId") Long cabinetId, Pageable pageable);

    @Query("SELECT COUNT(d) FROM Dossier d WHERE d.cabinet.id = :cabinetId")
    Long countByCreatorAndCabinetId(@Param("cabinetId") Long cabinetId);
}