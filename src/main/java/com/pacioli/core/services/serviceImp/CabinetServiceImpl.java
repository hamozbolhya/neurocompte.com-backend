package com.pacioli.core.services.serviceImp;

import com.pacioli.core.DTO.CabinetDTO;
import com.pacioli.core.DTO.RoleDTO;
import com.pacioli.core.DTO.UserDTO;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.models.User;
import com.pacioli.core.repositories.CabinetRepository;
import com.pacioli.core.repositories.UserRepository;
import com.pacioli.core.services.CabinetService;
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
    public List<CabinetDTO> fetchAllCabinets() {
        // 1️⃣ Execute the flat query
        List<Object[]> results = cabinetRepository.findCabinetWithUsersAndRoles();

        // 2️⃣ Map to hold Cabinets, Users, and Roles
        Map<Long, CabinetDTO> cabinetMap = new HashMap<>();
        Map<UUID, UserDTO> userMap = new HashMap<>();

        for (Object[] result : results) {
            Long cabinetId = (Long) result[0];
            String cabinetName = (String) result[1];
            String cabinetAddress = (String) result[2];
            String cabinetPhone = (String) result[3];
            String cabinetICE = (String) result[4];
            String cabinetVille = (String) result[5];
            UUID userId = (UUID) result[6];
            String username = (String) result[7];
            String userEmail = (String) result[8];
            String roleId = (String) result[9];
            String roleName = (String) result[10];

            // 3️⃣ Create CabinetDTO
            CabinetDTO cabinet = cabinetMap.computeIfAbsent(cabinetId, id ->
                    new CabinetDTO(id, cabinetName, cabinetAddress, cabinetPhone, cabinetICE, cabinetVille, new ArrayList<>())
            );

            // 4️⃣ Create UserDTO
            if (userId != null) {
                UserDTO user = userMap.computeIfAbsent(userId, id ->
                        new UserDTO(id, username, userEmail, new ArrayList<>())
                );

                // 5️⃣ Add Role to User
                if (roleId != null && roleName != null) {
                    RoleDTO role = new RoleDTO(roleId, roleName);
                    if (!user.getRoles().contains(role)) {
                        user.getRoles().add(role);
                    }
                }

                // 6️⃣ Add User to Cabinet
                if (!cabinet.getUsers().contains(user)) {
                    cabinet.getUsers().add(user);
                }
            }
        }

        return new ArrayList<>(cabinetMap.values());
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

}
