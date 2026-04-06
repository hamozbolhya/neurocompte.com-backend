package com.pacioli.core.services.serviceImp;

import com.pacioli.core.DTO.CabinetDTO;
import com.pacioli.core.DTO.CabinetStatsDTO;
import com.pacioli.core.Exceptions.ResourceNotFoundException;
import com.pacioli.core.controllers.CabinetController.CabinetRequest;
import com.pacioli.core.models.Cabinet;
import com.pacioli.core.models.CabinetContract;
import com.pacioli.core.models.User;
import com.pacioli.core.repositories.CabinetContractRepository;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class CabinetServiceImpl implements CabinetService {

    @Autowired
    private CabinetRepository cabinetRepository;

    @Autowired
    private CabinetContractRepository contractRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private PieceRepository pieceRepository;

    @Autowired
    @Lazy
    private AuditService auditService;

    @Autowired
    private UserService userService;

    // ✅ Méthode utilitaire pour récupérer le cabinet cible
    private Long getTargetCabinetId(Cabinet cabinet) {
        return cabinet != null ? cabinet.getId() : null;
    }

    private String getTargetCabinetName(Cabinet cabinet) {
        return cabinet != null ? cabinet.getName() : null;
    }

    /**
     * Default one-year window aligned on the subscription date: end = start + 1 year − 1 day
     * (e.g. 15 May → 14 May next year).
     */
    static LocalDate defaultContractEndDate(@NonNull LocalDate contractStartDate) {
        return contractStartDate.plusYears(1).minusDays(1);
    }

    private CabinetContract createInitialContract(@NonNull Cabinet cabinet, @NonNull CabinetRequest incoming) {
        LocalDate start = incoming.getContractStartDate();
        if (start == null) {
            throw new IllegalArgumentException("La date de début de contrat est obligatoire.");
        }
        LocalDate end = incoming.getContractEndDate();
        if (end == null) {
            end = defaultContractEndDate(start);
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("La date de fin de contrat doit être postérieure à la date de début.");
        }

        Integer pieces = incoming.getNormalStatementPieceQuota();
        Integer pages = incoming.getBankStatementPageQuota();
        if (pieces == null || pages == null) {
            throw new IllegalArgumentException("Les quotas de pièces et de pages sont obligatoires.");
        }
        if (pieces < 0 || pages < 0) {
            throw new IllegalArgumentException("Les quotas doivent être des entiers positifs ou nuls.");
        }

        CabinetContract contract = new CabinetContract();
        contract.setCabinet(cabinet);
        contract.setStartDate(start);
        contract.setEndDate(end);
        contract.setNormalStatementPieceQuota(pieces);
        contract.setBankStatementPageQuota(pages);
        contract.setActive(true);
        return contract;
    }

    @Override
    @Transactional
    public Cabinet addCabinet(@NonNull Cabinet cabinet, @NonNull CabinetRequest contractRequest) {
        User currentUser = userService.getCurrentUser();

        // Temporary storage of contract fields from the incoming object
        // since we removed them from the Cabinet entity
        CabinetContract initialContract = createInitialContract(cabinet, contractRequest);

        Cabinet savedCabinet = cabinetRepository.save(cabinet);
        initialContract.setCabinet(savedCabinet);
        contractRepository.save(initialContract);

        // ✅ Récupérer le cabinet cible (c'est le cabinet lui-même)
        Long targetCabinetId = getTargetCabinetId(savedCabinet);
        String targetCabinetName = getTargetCabinetName(savedCabinet);

        // Audit: Création de cabinet avec cabinet cible
        auditService.logSuccessWithTargetCabinet(
                currentUser,
                "CREATE",
                "Cabinet",
                savedCabinet.getId(),
                savedCabinet.getName(),
                null,
                savedCabinet,
                targetCabinetId,
                targetCabinetName
        );

        return savedCabinet;
    }

    @Override
    @Transactional
    public Cabinet updateCabinet(@NonNull Long id, @NonNull Cabinet cabinet) {
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

            // ✅ Récupérer le cabinet cible
            Long targetCabinetId = getTargetCabinetId(updatedCabinet);
            String targetCabinetName = getTargetCabinetName(updatedCabinet);

            // Audit: Mise à jour de cabinet avec cabinet cible
            auditService.logSuccessWithTargetCabinet(
                    currentUser,
                    "UPDATE",
                    "Cabinet",
                    updatedCabinet.getId(),
                    updatedCabinet.getName(),
                    oldCabinet,
                    updatedCabinet,
                    targetCabinetId,
                    targetCabinetName
            );

            return updatedCabinet;
        }).orElseThrow(() -> {
            // Audit: Échec mise à jour - cabinet non trouvé
            auditService.logFailure(
                    currentUser,
                    "UPDATE",
                    "Cabinet",
                    id,
                    "Cabinet-" + id,
                    "Cabinet not found with id: " + id
            );
            return new RuntimeException("Cabinet not found");
        });
    }

    @Override
    @Transactional
    public void deleteCabinet(@NonNull Long id) {
        User currentUser = userService.getCurrentUser();

        try {
            Optional<Cabinet> cabinetOptional = cabinetRepository.findById(id);

            if (cabinetOptional.isPresent()) {
                Cabinet cabinet = cabinetOptional.get();
                String cabinetName = cabinet.getName();

                // ✅ Récupérer le cabinet cible
                Long targetCabinetId = getTargetCabinetId(cabinet);
                String targetCabinetName = getTargetCabinetName(cabinet);

                // Audit: Suppression de cabinet avec cabinet cible (avant suppression)
                auditService.logSuccessWithTargetCabinet(
                        currentUser,
                        "DELETE",
                        "Cabinet",
                        id,
                        cabinetName,
                        cabinet,
                        null,
                        targetCabinetId,
                        targetCabinetName
                );

                cabinetRepository.deleteById(id);

                log.info("Cabinet with id {} deleted successfully", id);
            } else {
                // Audit: Échec suppression - cabinet non trouvé
                auditService.logFailure(
                        currentUser,
                        "DELETE",
                        "Cabinet",
                        id,
                        "Cabinet-" + id,
                        "Cabinet not found with id: " + id
                );
                throw new RuntimeException("Cabinet not found with id: " + id);
            }
        } catch (Exception e) {
            // Audit: Échec suppression - erreur
            auditService.logFailure(
                    currentUser,
                    "DELETE",
                    "Cabinet",
                    id,
                    "Cabinet-" + id,
                    e.getMessage()
            );
            throw e;
        }
    }

    @Override
    public CabinetDTO fetchCabinetById(@NonNull Long id) {
        Cabinet cabinet = cabinetRepository.findById(id).orElseThrow(() -> {
            return new RuntimeException("Cabinet not found with id: " + id);
        });

        CabinetDTO dto = new CabinetDTO(
                cabinet.getId(),
                cabinet.getName(),
                cabinet.getAddress(),
                cabinet.getPhone(),
                cabinet.getIce(),
                cabinet.getVille()
        );

        if (cabinet.getContracts() != null) {
            dto.setContracts(cabinet.getContracts().stream()
                    .map(c -> {
                        LocalDateTime start = c.getStartDate().atStartOfDay();
                        LocalDateTime end = c.getEndDate().atTime(23, 59, 59);
                        
                        long normalConsumption = pieceRepository.countNormalPiecesForCabinetInPeriod(cabinet.getId(), start, end);
                        Long bankConsumption = pieceRepository.sumBankPagesForCabinetInPeriod(cabinet.getId(), start, end);
                        
                        return new CabinetDTO.ContractDTO(
                            c.getId(),
                            c.getStartDate(),
                            c.getEndDate(),
                            c.getNormalStatementPieceQuota(),
                            c.getBankStatementPageQuota(),
                            normalConsumption,
                            bankConsumption != null ? bankConsumption : 0L,
                            c.isActive()
                        );
                    })
                    .collect(java.util.stream.Collectors.toList()));
        }

        return dto;
    }

    @Override
    public Page<CabinetContract> fetchContractsByCabinetId(@NonNull Long cabinetId, int page, int size) {
        return contractRepository.findByCabinetIdOrderByStartDateDesc(cabinetId, PageRequest.of(page, size));
    }

    @Override
    @Transactional
    public void renewContract(@NonNull Long cabinetId, @NonNull CabinetRequest renewalRequest) {
        User currentUser = userService.getCurrentUser();
        Cabinet cabinet = cabinetRepository.findById(cabinetId).orElseThrow(() -> 
            new ResourceNotFoundException("Cabinet not found with id: " + cabinetId));

        // 1. Deactivate all existing contracts for this cabinet
        List<CabinetContract> existingContracts = contractRepository.findByCabinetIdOrderByStartDateDesc(cabinetId);
        for (CabinetContract contract : existingContracts) {
            contract.setActive(false);
        }
        contractRepository.saveAll(existingContracts);

        // 2. Create and save the new contract
        CabinetContract newContract = createInitialContract(cabinet, renewalRequest);
        contractRepository.save(newContract);

        // 3. Audit the renewal
        auditService.logSuccessWithTargetCabinet(
                currentUser,
                "RENEW_CONTRACT",
                "Cabinet",
                cabinetId,
                cabinet.getName(),
                null,
                newContract,
                cabinetId,
                cabinet.getName()
        );

        log.info("Contract renewed for cabinet {} (ID: {})", cabinet.getName(), cabinetId);
    }

    @Override
    @Transactional
    public void assignCabinetToUser(@NonNull Long cabinetId, @NonNull UUID userId) {
        User currentUser = userService.getCurrentUser();

        try {
            Optional<Cabinet> cabinetOptional = cabinetRepository.findById(cabinetId);
            if (!cabinetOptional.isPresent()) {
                // Audit: Échec assignation - cabinet non trouvé
                auditService.logFailure(
                        currentUser,
                        "ASSIGN",
                        "Cabinet",
                        cabinetId,
                        "Cabinet-" + cabinetId,
                        "Cabinet not found with id: " + cabinetId
                );
                throw new RuntimeException("Cabinet not found with id: " + cabinetId);
            }

            Optional<User> userOptional = userRepository.findById(userId);
            log.info("userOptional ********* {}", userOptional);
            log.info("userId ********* {}", userId);

            if (!userOptional.isPresent()) {
                // Audit: Échec assignation - utilisateur non trouvé
                auditService.logFailure(
                        currentUser,
                        "ASSIGN",
                        "User",
                        userId,
                        "User-" + userId,
                        "User not found with id: " + userId
                );
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

            // ✅ Récupérer le cabinet cible (celui qu'on assigne)
            Long targetCabinetId = cabinet.getId();
            String targetCabinetName = cabinet.getName();

            // Audit: Assignation réussie avec cabinet cible
            Map<String, Object> changes = new HashMap<>();
            changes.put("oldCabinetId", oldCabinetId);
            changes.put("oldCabinetName", oldCabinetName);
            changes.put("newCabinetId", cabinetId);
            changes.put("newCabinetName", cabinet.getName());

            auditService.logSuccessWithTargetCabinet(
                    currentUser,
                    "ASSIGN",
                    "User",
                    userId,
                    user.getUsername(),
                    changes,
                    null,
                    targetCabinetId,
                    targetCabinetName
            );

            log.info("Cabinet {} assigned to user {} successfully", cabinetId, userId);

        } catch (Exception e) {
            // Audit: Échec assignation - erreur
            auditService.logFailure(
                    currentUser,
                    "ASSIGN",
                    "Cabinet",
                    cabinetId,
                    "Cabinet-" + cabinetId,
                    e.getMessage()
            );
            throw e;
        }
    }

    @Override
    @Transactional
    public void unassignCabinetFromUser(@NonNull UUID userId) {
        User currentUser = userService.getCurrentUser();

        try {
            Optional<User> optionalUser = userRepository.findById(userId);
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();

                // Sauvegarder l'ancien cabinet pour l'audit
                Long oldCabinetId = user.getCabinet() != null ? user.getCabinet().getId() : null;
                String oldCabinetName = user.getCabinet() != null ? user.getCabinet().getName() : null;

                // ✅ Récupérer le cabinet cible (celui qu'on désassigne)
                Long targetCabinetId = oldCabinetId;
                String targetCabinetName = oldCabinetName;

                user.setCabinet(null);  // Unassign the Cabinet
                userRepository.save(user);  // Save the updated user

                // Audit: Désassignation réussie avec cabinet cible
                Map<String, Object> changes = new HashMap<>();
                changes.put("oldCabinetId", oldCabinetId);
                changes.put("oldCabinetName", oldCabinetName);
                changes.put("newCabinetId", null);
                changes.put("newCabinetName", null);

                auditService.logSuccessWithTargetCabinet(
                        currentUser,
                        "UNASSIGN",
                        "User",
                        userId,
                        user.getUsername(),
                        changes,
                        null,
                        targetCabinetId,
                        targetCabinetName
                );

                log.info("Cabinet unassigned from user {} successfully", userId);
            } else {
                // Audit: Échec désassignation - utilisateur non trouvé
                auditService.logFailure(
                        currentUser,
                        "UNASSIGN",
                        "User",
                        userId,
                        "User-" + userId,
                        "User not found"
                );
                throw new RuntimeException("User not found");
            }
        } catch (Exception e) {
            // Audit: Échec désassignation - erreur
            auditService.logFailure(
                    currentUser,
                    "UNASSIGN",
                    "User",
                    userId,
                    "User-" + userId,
                    e.getMessage()
            );
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
    public CabinetStatsDTO getCabinetStatsForUser(@NonNull Long cabinetId, String userEmail) {
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
            CabinetStatsDTO stats = CabinetStatsDTO.builder()
                    .cabinetId(cabinetId)
                    .cabinetName(cabinet.getName())
                    .userEmail(userEmail)
                    .dossierCount(dossierCount)
                    .pieceCount(pieceCount)
                    .build();

            return stats;

        } catch (Exception e) {
            throw e;
        }
    }
}