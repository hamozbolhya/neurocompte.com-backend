package com.pacioli.core.repositories;

import com.pacioli.core.models.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findById(@Param("id") UUID id);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    @Override
    List<User> findAll(Sort sort);

    List<User> findByCabinetId(Long cabinetId);
}