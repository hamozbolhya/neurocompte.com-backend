package com.pacioli.core.services;

import com.pacioli.core.DTO.CabinetDTO;
import com.pacioli.core.models.Cabinet;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public interface CabinetService {

    Cabinet addCabinet(Cabinet cabinet);
    Cabinet updateCabinet(Long id, Cabinet cabinet);
    void deleteCabinet(Long id);
    CabinetDTO fetchCabinetById(Long id);
    List<CabinetDTO> fetchAllCabinets();
    void assignCabinetToUser(Long cabinetId, UUID userId);

    void unassignCabinetFromUser(UUID userId);
    Optional<Cabinet> findByIce(String ice);

}
