package com.pacioli.core.services.serviceImp;

import com.pacioli.core.DTO.CabinetDTO;
import com.pacioli.core.DTO.CabinetStatsDTO;
import com.pacioli.core.DTO.RoleDTO;
import com.pacioli.core.DTO.UserDTO;
import com.pacioli.core.Exceptions.ResourceNotFoundException;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.models.User;
import com.pacioli.core.repositories.CabinetRepository;
import com.pacioli.core.repositories.DossierRepository;
import com.pacioli.core.repositories.PieceRepository;
import com.pacioli.core.repositories.UserRepository;
import com.pacioli.core.services.CabinetService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class CabinetServiceImpl implements CabinetService {


    @Autowired
    private CabinetRepository cabinetRepository;


    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DossierRepository dossierRepository;
    @Autowired
    private PieceRepository pieceRepository;
    @Override
    public Cabinet addCabinet(Cabinet cabinet) {
        return cabinetRepository.save(cabinet);
    }

    @Override
    public Cabinet updateCabinet(Long id, Cabinet cabinet) {
        return cabinetRepository.findById(id)
                .map(existingCabinet -> {
                    existingCabinet.setName(cabinet.getName());
                    existingCabinet.setAddress(cabinet.getAddress());
                    existingCabinet.setPhone(cabinet.getPhone());
                    return cabinetRepository.save(existingCabinet);
                })
                .orElseThrow(() -> new RuntimeException("Cabinet not found"));
    }

    @Override
    public void deleteCabinet(Long id) {
        cabinetRepository.deleteById(id);
    }

    @Override
    public CabinetDTO fetchCabinetById(Long id) {
        return cabinetRepository.findCabinetById(id)
                .orElseThrow(() -> new RuntimeException("Cabinet not found with id: " + id));
    }

    @Override
    public void assignCabinetToUser(Long cabinetId, UUID userId) {
        Optional<Cabinet> cabinetOptional = cabinetRepository.findById(cabinetId);
        if (!cabinetOptional.isPresent()) {
            throw new RuntimeException("Cabinet not found with id: " + cabinetId);
        }

        Optional<User> userOptional = userRepository.findById(userId);
        log.info("userOptional ********* {}", userOptional);
        log.info("userId ********* {}", userId);
        if (!userOptional.isPresent()) {
            throw new RuntimeException("User not found with id: " + userId);
        }

        Cabinet cabinet = cabinetOptional.get();
        User user = userOptional.get();

        // Assign the cabinet to the user
        user.setCabinet(cabinet);

        // Save the user with the updated cabinet
        userRepository.save(user);
    }

    @Override
    public void unassignCabinetFromUser(UUID userId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setCabinet(null);  // Unassign the Cabinet
            userRepository.save(user);  // Save the updated user
        } else {
            throw new RuntimeException("User not found");
        }
    }


    @Override
    public Optional<Cabinet> findByIce(String ice) {
        return cabinetRepository.findByIce(ice);
    }

    @Transactional
    public CabinetStatsDTO getCabinetStatsForUser(Long cabinetId, String userEmail) {
        // Find the cabinet
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new ResourceNotFoundException("Cabinet not found with id: " + cabinetId));

        // Get count of dossiers created by the user in this cabinet
        Long dossierCount = dossierRepository.countByCreatorAndCabinetId(cabinetId);

        // Get count of pieces uploaded by the user in this cabinet
        Long pieceCount = pieceRepository.countByUploaderAndCabinetId(cabinetId);

        // Build and return the DTO
        return CabinetStatsDTO.builder()
                .cabinetId(cabinetId)
                .cabinetName(cabinet.getName())
                .userEmail(userEmail)
                .dossierCount(dossierCount)
                .pieceCount(pieceCount)
                .build();
    }
}
