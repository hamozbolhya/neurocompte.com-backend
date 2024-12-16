package com.pacioli.core.repositories;

import com.pacioli.core.DTO.CabinetDTO;
import com.pacioli.core.models.Cabinet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CabinetRepository extends JpaRepository<Cabinet, Long> {
    Optional<Cabinet> findByIce(String ice);
    @Query("SELECT new com.pacioli.core.DTO.CabinetDTO(c.id, c.name, c.address, c.phone, c.ice, c.ville) FROM Cabinet c WHERE c.id = :id")
    Optional<CabinetDTO> findCabinetById(@Param("id") Long id);

    @Query("""
    SELECT 
        c.id AS cabinetId, 
        c.name AS cabinetName, 
        c.address AS cabinetAddress, 
        c.phone AS cabinetPhone, 
        c.ice AS cabinetICE, 
        c.ville AS cabinetVille,
        u.id AS userId, 
        u.username AS username, 
        u.email AS userEmail, 
        r.id AS roleId, 
        r.name AS roleName
    FROM Cabinet c 
    LEFT JOIN c.users u 
    LEFT JOIN u.roles r
""")
    List<Object[]> findCabinetWithUsersAndRoles();
}
