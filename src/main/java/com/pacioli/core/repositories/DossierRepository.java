package com.pacioli.core.repositories;

import com.pacioli.core.DTO.DossierDTO;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.models.Dossier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DossierRepository extends JpaRepository<Dossier, Long> {
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
            "d.decimalPrecision, " +
            "d.activity) " +
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
            "d.decimalPrecision, " +
            "d.activity) " +
            "FROM Dossier d WHERE d.cabinet.id = :cabinetId")
    Page<DossierDTO> findDossierDTOsByCabinetId(@Param("cabinetId") Long cabinetId, Pageable pageable);

    @Query("SELECT COUNT(d) FROM Dossier d WHERE d.cabinet.id = :cabinetId")
    Long countByCreatorAndCabinetId(@Param("cabinetId") Long cabinetId);
    Long countByCabinetId(Long cabinetId);
    Optional<Dossier> findByNameAndCabinetId(String name, Long cabinetId);
    Page<Dossier> findByCabinetUsersId(UUID userId, Pageable pageable);
    boolean existsByIdAndCabinetUsersId(Long dossierId, UUID userId);
    Optional<Dossier> findByIdAndCabinetUsersId(Long id, UUID userId);
}