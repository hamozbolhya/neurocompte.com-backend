package com.pacioli.core.repositories;

import com.pacioli.core.models.Account;
import com.pacioli.core.models.Line;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LineRepository extends JpaRepository<Line, Long> {

    @Modifying
    @Query("UPDATE Line e SET e.account = :account WHERE e.ecriture.id IN :ids")
    void updateCompteByIds(@Param("account") Account account, @Param("ids") List<Long> ids);

}
