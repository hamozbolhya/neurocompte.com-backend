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
    boolean existsByName(String name);
    Optional<Dossier> findByName(String name);

    // For single Dossier fetch
    @Query("SELECT NEW com.pacioli.core.DTO.DossierDTO(" +
            "d.id, " +
            "d.name, " +
            "d.ICE, " +
            "d.address, " +
            "d.city, " +
            "d.phone, " +
            "d.email) " +
            "FROM Dossier d WHERE d.id = :id")
    Optional<DossierDTO> findDossierById(@Param("id") Long id);

    Page<Dossier> findByCabinetId(Long cabinetId, Pageable pageable);

    // For paginated results
    @Query("SELECT NEW com.pacioli.core.DTO.DossierDTO(" +
            "d.id, " +
            "d.name, " +
            "d.ICE, " +
            "d.address, " +
            "d.city, " +
            "d.phone, " +
            "d.email) " +
            "FROM Dossier d WHERE d.cabinet.id = :cabinetId")
    Page<DossierDTO> findDossierDTOsByCabinetId(@Param("cabinetId") Long cabinetId, Pageable pageable);
}