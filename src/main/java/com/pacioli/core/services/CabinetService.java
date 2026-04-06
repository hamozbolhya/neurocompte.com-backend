package com.pacioli.core.services;

import com.pacioli.core.controllers.CabinetController.CabinetRequest;
import com.pacioli.core.DTO.CabinetDTO;
import com.pacioli.core.DTO.CabinetStatsDTO;
import com.pacioli.core.models.Cabinet;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public interface CabinetService {

    Cabinet addCabinet(@NonNull Cabinet cabinet, @NonNull CabinetRequest contractRequest);

    Cabinet updateCabinet(@NonNull Long id, @NonNull Cabinet cabinet);

    void deleteCabinet(@NonNull Long id);

    CabinetDTO fetchCabinetById(@NonNull Long id);

    void assignCabinetToUser(@NonNull Long cabinetId, @NonNull UUID userId);

    void unassignCabinetFromUser(@NonNull UUID userId);

    Optional<Cabinet> findByIce(String ice);

    CabinetStatsDTO getCabinetStatsForUser(@NonNull Long cabinetId, String userEmail);
}
