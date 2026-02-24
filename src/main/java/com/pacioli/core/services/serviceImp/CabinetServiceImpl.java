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
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.CabinetService;
import com.pacioli.core.services.UserService;
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

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserService userService;

    @Override
    @Transactional
    public Cabinet addCabinet(Cabinet cabinet) {
        User currentUser = userService.getCurrentUser();

        Cabinet savedCabinet = cabinetRepository.save(cabinet);

        // Audit: Création de cabinet
        auditService.logSuccess(currentUser, "CREATE", "Cabinet", savedCabinet.getId(), savedCabinet.getName(), null, savedCabinet);

        return savedCabinet;
    }

    @Override
    @Transactional
    public Cabinet updateCabinet(Long id, Cabinet cabinet) {
        User currentUser = userService.getCurrentUser();

        return cabinetRepository.findById(id).map(existingCabinet -> {
            // Sauvegarder l'ancien état pour l'audit
            Cabinet oldCabinet = new Cabinet();
            oldCabinet.setId(existingCabinet.getId());
            oldCabinet.setName(existingCabinet.getName());
            oldCabinet.setAddress(existingCabinet.getAddress());
            oldCabinet.setPhone(existingCabinet.getPhone());
            oldCabinet.setIce(existingCabinet.getIce());
            oldCabinet.setVille(existingCabinet.getVille());

            // Mise à jour
            existingCabinet.setName(cabinet.getName());
            existingCabinet.setAddress(cabinet.getAddress());
            existingCabinet.setPhone(cabinet.getPhone());
            existingCabinet.setIce(cabinet.getIce());
            existingCabinet.setVille(cabinet.getVille());

            Cabinet updatedCabinet = cabinetRepository.save(existingCabinet);

            // Audit: Mise à jour de cabinet
            auditService.logSuccess(currentUser, "UPDATE", "Cabinet", updatedCabinet.getId(), updatedCabinet.getName(), oldCabinet, updatedCabinet);

            return updatedCabinet;
        }).orElseThrow(() -> {
            // Audit: Échec mise à jour - cabinet non trouvé
            auditService.logFailure(currentUser, "UPDATE", "Cabinet", id, "Cabinet-" + id, "Cabinet not found with id: " + id);
            return new RuntimeException("Cabinet not found");
        });
    }

    @Override
    @Transactional
    public void deleteCabinet(Long id) {
        User currentUser = userService.getCurrentUser();

        try {
            Optional<Cabinet> cabinetOptional = cabinetRepository.findById(id);

            if (cabinetOptional.isPresent()) {
                Cabinet cabinet = cabinetOptional.get();
                String cabinetName = cabinet.getName();

                // Audit: Suppression de cabinet (avant suppression pour avoir les infos)
                auditService.logSuccess(currentUser, "DELETE", "Cabinet", id, cabinetName, cabinet, null);

                cabinetRepository.deleteById(id);

                log.info("Cabinet with id {} deleted successfully", id);
            } else {
                // Audit: Échec suppression - cabinet non trouvé
                auditService.logFailure(currentUser, "DELETE", "Cabinet", id, "Cabinet-" + id, "Cabinet not found with id: " + id);
                throw new RuntimeException("Cabinet not found with id: " + id);
            }
        } catch (Exception e) {
            // Audit: Échec suppression - erreur
            auditService.logFailure(currentUser, "DELETE", "Cabinet", id, "Cabinet-" + id, e.getMessage());
            throw e;
        }
    }

    @Override
    public CabinetDTO fetchCabinetById(Long id) {
        CabinetDTO cabinetDTO = cabinetRepository.findCabinetById(id).orElseThrow(() -> {
            return new RuntimeException("Cabinet not found with id: " + id);
        });
        return cabinetDTO;
    }

    @Override
    @Transactional
    public void assignCabinetToUser(Long cabinetId, UUID userId) {
        User currentUser = userService.getCurrentUser();

        try {
            Optional<Cabinet> cabinetOptional = cabinetRepository.findById(cabinetId);
            if (!cabinetOptional.isPresent()) {
                // Audit: Échec assignation - cabinet non trouvé
                auditService.logFailure(currentUser, "ASSIGN", "Cabinet", cabinetId, "Cabinet-" + cabinetId, "Cabinet not found with id: " + cabinetId);
                throw new RuntimeException("Cabinet not found with id: " + cabinetId);
            }

            Optional<User> userOptional = userRepository.findById(userId);
            log.info("userOptional ********* {}", userOptional);
            log.info("userId ********* {}", userId);

            if (!userOptional.isPresent()) {
                // Audit: Échec assignation - utilisateur non trouvé
                auditService.logFailure(currentUser, "ASSIGN", "User", userId, "User-" + userId, "User not found with id: " + userId);
                throw new RuntimeException("User not found with id: " + userId);
            }

            Cabinet cabinet = cabinetOptional.get();
            User user = userOptional.get();

            // Sauvegarder l'ancien cabinet pour l'audit
            Long oldCabinetId = user.getCabinet() != null ? user.getCabinet().getId() : null;
            String oldCabinetName = user.getCabinet() != null ? user.getCabinet().getName() : null;

            // Assign the cabinet to the user
            user.setCabinet(cabinet);

            // Save the user with the updated cabinet
            userRepository.save(user);

            // Audit: Assignation réussie
            Map<String, Object> changes = new HashMap<>();
            changes.put("oldCabinetId", oldCabinetId);
            changes.put("oldCabinetName", oldCabinetName);
            changes.put("newCabinetId", cabinetId);
            changes.put("newCabinetName", cabinet.getName());

            auditService.logSuccess(currentUser, "ASSIGN", "User", userId, user.getUsername(), changes, null);

            log.info("Cabinet {} assigned to user {} successfully", cabinetId, userId);

        } catch (Exception e) {
            // Audit: Échec assignation - erreur
            auditService.logFailure(currentUser, "ASSIGN", "Cabinet", cabinetId, "Cabinet-" + cabinetId, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional
    public void unassignCabinetFromUser(UUID userId) {
        User currentUser = userService.getCurrentUser();

        try {
            Optional<User> optionalUser = userRepository.findById(userId);
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();

                // Sauvegarder l'ancien cabinet pour l'audit
                Long oldCabinetId = user.getCabinet() != null ? user.getCabinet().getId() : null;
                String oldCabinetName = user.getCabinet() != null ? user.getCabinet().getName() : null;

                user.setCabinet(null);  // Unassign the Cabinet
                userRepository.save(user);  // Save the updated user

                // Audit: Désassignation réussie
                Map<String, Object> changes = new HashMap<>();
                changes.put("oldCabinetId", oldCabinetId);
                changes.put("oldCabinetName", oldCabinetName);
                changes.put("newCabinetId", null);
                changes.put("newCabinetName", null);

                auditService.logSuccess(currentUser, "UNASSIGN", "User", userId, user.getUsername(), changes, null);

                log.info("Cabinet unassigned from user {} successfully", userId);
            } else {
                // Audit: Échec désassignation - utilisateur non trouvé
                auditService.logFailure(currentUser, "UNASSIGN", "User", userId, "User-" + userId, "User not found");
                throw new RuntimeException("User not found");
            }
        } catch (Exception e) {
            // Audit: Échec désassignation - erreur
            auditService.logFailure(currentUser, "UNASSIGN", "User", userId, "User-" + userId, e.getMessage());
            throw e;
        }
    }

    @Override
    public Optional<Cabinet> findByIce(String ice) {
        Optional<Cabinet> cabinet = cabinetRepository.findByIce(ice);

        return cabinet;
    }

    @Override
    @Transactional
    public CabinetStatsDTO getCabinetStatsForUser(Long cabinetId, String userEmail) {
        User currentUser = userService.getCurrentUser();

        try {
            // Find the cabinet
            Cabinet cabinet = cabinetRepository.findById(cabinetId).orElseThrow(() -> {
                // Audit: Échec stats - cabinet non trouvé
                return new ResourceNotFoundException("Cabinet not found with id: " + cabinetId);
            });

            // Get count of dossiers created by the user in this cabinet
            Long dossierCount = dossierRepository.countByCreatorAndCabinetId(cabinetId);

            // Get count of pieces uploaded by the user in this cabinet
            Long pieceCount = pieceRepository.countByUploaderAndCabinetId(cabinetId);

            // Build and return the DTO
            CabinetStatsDTO stats = CabinetStatsDTO.builder().cabinetId(cabinetId).cabinetName(cabinet.getName()).userEmail(userEmail).dossierCount(dossierCount).pieceCount(pieceCount).build();

            return stats;

        } catch (Exception e) {
            throw e;
        }
    }
}