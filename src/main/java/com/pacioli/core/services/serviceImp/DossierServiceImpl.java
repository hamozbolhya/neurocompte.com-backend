package com.pacioli.core.services.serviceImp;

import com.pacioli.core.DTO.Company;
import com.pacioli.core.DTO.DossierDTO;
import com.pacioli.core.DTO.PaysDTO;
import com.pacioli.core.Exceptions.CompanyAiException;
import com.pacioli.core.Exceptions.ExerciseDateConflictException;
import com.pacioli.core.models.*;
import com.pacioli.core.repositories.*;
import com.pacioli.core.services.CompanyAiService;
import com.pacioli.core.services.DossierService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class DossierServiceImpl implements DossierService {

    private final DossierRepository dossierRepository;
    private final CompanyAiService companyAiService;

    @Autowired
    private CabinetRepository cabinetRepository;

    @Autowired
    private ExerciceRepository exerciseRepository;

    @Autowired
    private EcritureRepository ecritureRepository;

    @Autowired
    private JournalRepository journalRepository;

    @Autowired
    public DossierServiceImpl(DossierRepository dossierRepository, CompanyAiService companyAiService) {
        this.dossierRepository = dossierRepository;
        this.companyAiService = companyAiService;
    }

    @Override
    @Transactional
    public Dossier createDossier(Dossier dossier, List<Exercise> exercicesData) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Creating dossier: {}", requestId, dossier.getName());

        // Check if a dossier with the same name already exists
        Dossier existingDossier = dossierRepository.findByName(dossier.getName()).orElse(null);

        // Store whether this is a new dossier or an update
        boolean isNewDossier = existingDossier == null;

        if (existingDossier != null) {
            // If a Dossier with the same name exists, update its details (except the name)
            existingDossier.setICE(dossier.getICE());
            existingDossier.setAddress(dossier.getAddress());
            existingDossier.setCity(dossier.getCity());
            existingDossier.setPhone(dossier.getPhone());
            existingDossier.setEmail(dossier.getEmail());

            // Update country relationship
            existingDossier.setCountry(dossier.getCountry());

            // Update decimal precision
            if (dossier.getDecimalPrecision() != null) {
                existingDossier.setDecimalPrecision(dossier.getDecimalPrecision());
                log.info("[{}] Updated decimal precision to: {}", requestId, dossier.getDecimalPrecision());
            }

            dossier = existingDossier;
        } else {
            // If no Dossier with the same name exists, check the Cabinet
            Cabinet cabinet = cabinetRepository.findById(dossier.getCabinet().getId())
                    .orElseThrow(() -> new RuntimeException("Cabinet non trouvé"));
            dossier.setCabinet(cabinet);

            // Set default decimal precision if not provided for new dossiers
            if (dossier.getDecimalPrecision() == null) {
                dossier.setDecimalPrecision(2);
                log.info("[{}] Set default decimal precision to: 2", requestId);
            }
        }

        // Save or update the Dossier
        Dossier savedDossier = dossierRepository.save(dossier);
        log.info("[{}] Dossier saved with ID: {} and decimal precision: {}",
                requestId, savedDossier.getId(), savedDossier.getDecimalPrecision());

        // Create the list of default journals (only for new dossiers)
        if (isNewDossier) {
            createDefaultJournals(savedDossier);
        }

        // Handle Exercise data if provided
        if (exercicesData != null && !exercicesData.isEmpty()) {
            for (Exercise exercise : exercicesData) {
                // Check if an Exercise with the same dates already exists for the Dossier
                boolean exists = exerciseRepository.existsByDossierAndStartDateAndEndDate(
                        savedDossier, exercise.getStartDate(), exercise.getEndDate()
                );

                if (exists) {
                    throw new ExerciseDateConflictException("Le dossier a déjà des exercices à ces dates");
                }

                // Set the Dossier for each Exercise and save it
                exercise.setDossier(savedDossier);
                exerciseRepository.save(exercise);
            }
        }

        // Call the AI Company API - use POST for new, PUT for existing
        String countryCode = savedDossier.getCountry() != null ? savedDossier.getCountry().getCode() : "NOT_FOUND";

        Company company = new Company();
        company.setId(savedDossier.getId());
        company.setName(savedDossier.getName());
        company.setCountry(countryCode);
        company.setActivity(savedDossier.getActivity());

        try {
            if (isNewDossier) {
                // For new dossiers, use POST
                log.info("[{}] Calling Company AI API to create company for dossier ID: {}", requestId, savedDossier.getId());
                Company createdCompany = companyAiService.createCompany(company);
                log.info("[{}] Company created successfully in AI service with ID: {}", requestId, createdCompany.getId());
            } else {
                // For existing dossiers, use PUT
                log.info("[{}] Calling Company AI API to update company for dossier ID: {}", requestId, savedDossier.getId());
                Company updatedCompany = companyAiService.updateCompany(savedDossier.getId(), company);
                log.info("[{}] Company updated successfully in AI service with ID: {}", requestId, updatedCompany.getId());
            }
        } catch (Exception e) {
            // Log the error and trigger a transaction rollback
            log.error("[{}] Error creating/updating company in AI service for dossier ID {}: {}",
                    requestId, savedDossier.getId(), e.getMessage(), e);

            // The @Transactional annotation will ensure rollback on RuntimeException
            throw new CompanyAiException("Erreur lors de la " + (isNewDossier ? "création" : "mise à jour") +
                    " de la société dans le service AI: " + e.getMessage(), e);
        }

        return savedDossier;
    }

    private void createDefaultJournals(Dossier dossier) {
        List<Journal> defaultJournals = List.of(
                new Journal("HA", "Achats", dossier.getCabinet(), dossier),
                new Journal("VE", "Ventes", dossier.getCabinet(), dossier),
                new Journal("BQ", "Banque", dossier.getCabinet(), dossier),
                new Journal("CA", "Caisse", dossier.getCabinet(), dossier),
                new Journal("PA", "Paie", dossier.getCabinet(), dossier),
                new Journal("OD", "Opérations Diverses", dossier.getCabinet(), dossier)
        );

        // Filter out journals that already exist for this dossier
        List<Journal> journalsToCreate = defaultJournals.stream()
                .filter(journal -> !journalRepository.existsByNameAndDossierId(journal.getName(), dossier.getId()))
                .toList();

        // Save only new journals
        if (!journalsToCreate.isEmpty()) {
            journalRepository.saveAll(journalsToCreate);
        }
    }

    @Override
    @Transactional
    public Dossier updateExercises(Long dossierId, List<Exercise> updatedExercises) {
        // Fetch the dossier by ID
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new RuntimeException("Dossier non trouvé"));

        // Validate and update the exercises
        for (Exercise updatedExercise : updatedExercises) {
            // Fetch the existing exercise (if updating)
            Exercise existingExercise = exerciseRepository.findById(updatedExercise.getId()).orElse(null);

            // Validate the new date range
            validateExerciseDateRange(dossier, updatedExercise, existingExercise);

            if (existingExercise != null) {
                // Update existing exercise
                existingExercise.setStartDate(updatedExercise.getStartDate());
                existingExercise.setEndDate(updatedExercise.getEndDate());
                exerciseRepository.save(existingExercise);
            } else {
                // Create a new exercise
                updatedExercise.setDossier(dossier);
                exerciseRepository.save(updatedExercise);
            }
        }

        return dossier;
    }

    private void validateExerciseDateRange(Dossier dossier, Exercise updatedExercise, Exercise existingExercise) {
        // Check for overlap with existing exercises
        boolean overlapExists = exerciseRepository.existsByDossierAndStartDateAndEndDateOverlap(
                dossier, updatedExercise.getStartDate(), updatedExercise.getEndDate(), updatedExercise.getId());

        if (overlapExists) {
            throw new IllegalArgumentException("Les dates de l'exercice se chevauchent avec celles d'exercices existants");
        }

        // Fetch all "écritures" for the dossier and exercise
        List<Ecriture> ecritures = ecritureRepository.findByDossierAndExerciseId(dossier.getId(), updatedExercise.getId());

        // Check if any "écriture" falls outside the new date range
        for (Ecriture ecriture : ecritures) {
            if (ecriture.getEntryDate().isBefore(updatedExercise.getStartDate()) ||
                    ecriture.getEntryDate().isAfter(updatedExercise.getEndDate())) {
                throw new IllegalArgumentException("Impossible de modifier l'exercice car des écritures comptables existent en dehors des nouvelles dates proposées");
            }
        }
    }

    @Override
    public Dossier getDossierById(Long dossierId) {
        return dossierRepository.findById(dossierId)
                .orElseThrow(() -> new RuntimeException("Dossier non trouvé avec l'identifiant : " + dossierId));
    }

    @Override
    public DossierDTO getTheDossierById(Long dossierId) {
        return dossierRepository.findDossierById(dossierId)
                .orElseThrow(() -> new RuntimeException("Dossier non trouvé avec l'identifiant : " + dossierId));
    }

    @Override
    public Page<Dossier> getDossiers(Pageable pageable) {
        return dossierRepository.findAll(pageable);
    }

    @Override
    public Page<DossierDTO> getDossiersByCabinetId(Long cabinetId, Pageable pageable) {
        return dossierRepository.findDossierDTOsByCabinetId(cabinetId, pageable);
    }

    @Override
    @Transactional
    public void deleteExercises(Long dossierId, List<Long> exerciseIds) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Suppression des exercices pour le dossier ID: {}, Identifiants des exercices: {}",
                requestId, dossierId, exerciseIds);

        // Vérifier que le dossier existe
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new RuntimeException("Dossier non trouvé avec ID: " + dossierId));

        // Récupérer les exercices à supprimer
        List<Exercise> exercisesToDelete = exerciseRepository.findAllById(exerciseIds);
        if (exercisesToDelete.isEmpty()) {
            throw new IllegalArgumentException("Aucun exercice trouvé avec les identifiants fournis : " + exerciseIds);
        }

        // Valider que tous les exercices appartiennent au dossier
        for (Exercise exercise : exercisesToDelete) {
            if (!exercise.getDossier().getId().equals(dossierId)) {
                throw new IllegalArgumentException("L'exercice avec l'ID " + exercise.getId() + " n'appartient pas au dossier spécifié.");
            }

            // Récupérer les écritures pour l'exercice
            List<Ecriture> ecritures = ecritureRepository.findByDossierAndExerciseId(dossierId, exercise.getId());
            if (!ecritures.isEmpty()) {
                throw new IllegalArgumentException(
                        "Impossible de supprimer l'exercice avec l'ID " + exercise.getId() + " car des écritures comptables y sont associées.");
            }
        }

        // Supprimer les exercices
        log.info("[{}] Suppression des exercices: {}", requestId, exercisesToDelete);
        exerciseRepository.deleteAll(exercisesToDelete);
        log.info("[{}] Exercices supprimés avec succès.", requestId);
    }

    @Override
    @Transactional
    public DossierDTO updateDossier(Long id, Dossier dossierDetails) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Updating dossier with ID: {}", requestId, id);

        Dossier existingDossier = dossierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dossier not found for ID: " + id));

        // Store original values for comparison
        String originalName = existingDossier.getName();
        Country originalCountry = existingDossier.getCountry();

        // Update dossier fields
        if (dossierDetails.getName() != null) {
            existingDossier.setName(dossierDetails.getName());
        }
        if (dossierDetails.getICE() != null) {
            existingDossier.setICE(dossierDetails.getICE());
        }
        if (dossierDetails.getAddress() != null) {
            existingDossier.setAddress(dossierDetails.getAddress());
        }
        if (dossierDetails.getCity() != null) {
            existingDossier.setCity(dossierDetails.getCity());
        }
        if (dossierDetails.getPhone() != null) {
            existingDossier.setPhone(dossierDetails.getPhone());
        }
        if (dossierDetails.getEmail() != null) {
            existingDossier.setEmail(dossierDetails.getEmail());
        }
        if (dossierDetails.getCountry() != null) {
            existingDossier.setCountry(dossierDetails.getCountry());
        }
        // Update decimal precision if provided
        if (dossierDetails.getDecimalPrecision() != null) {
            existingDossier.setDecimalPrecision(dossierDetails.getDecimalPrecision());
            log.info("[{}] Updated decimal precision to: {}", requestId, dossierDetails.getDecimalPrecision());
        }

        Dossier savedDossier = dossierRepository.save(existingDossier);
        log.info("[{}] Dossier updated successfully with ID: {}", requestId, savedDossier.getId());

        // Call Company AI API only if the name or country changed
        boolean nameChanged = dossierDetails.getName() != null && !dossierDetails.getName().equals(originalName);
        boolean countryChanged = dossierDetails.getCountry() != null &&
                (originalCountry == null || !dossierDetails.getCountry().getCode().equals(originalCountry.getCode()));

        if (nameChanged || countryChanged) {
            try {
                log.info("[{}] Calling Company AI API to update company for dossier ID: {}", requestId, savedDossier.getId());

                String countryCode = savedDossier.getCountry() != null ? savedDossier.getCountry().getCode() : "MAR";

                Company company = new Company();
                company.setId(savedDossier.getId());
                company.setName(savedDossier.getName());
                company.setCountry(countryCode);

                // Use PUT method for updating existing companies
                Company updatedCompany = companyAiService.updateCompany(savedDossier.getId(), company);
                log.info("[{}] Company updated successfully in AI service with ID: {}", requestId, updatedCompany.getId());
            } catch (Exception e) {
                // Log the error but don't fail the transaction
                log.error("[{}] Error updating company in AI service for dossier ID {}: {}",
                        requestId, savedDossier.getId(), e.getMessage(), e);
            }
        } else {
            log.info("[{}] No need to update company in AI service (no name or country change)", requestId);
        }

        // Convert Dossier to DossierDTO
        DossierDTO dto = new DossierDTO();
        dto.setId(savedDossier.getId());
        dto.setName(savedDossier.getName());
        dto.setICE(savedDossier.getICE());
        dto.setAddress(savedDossier.getAddress());
        dto.setCity(savedDossier.getCity());
        dto.setPhone(savedDossier.getPhone());
        dto.setEmail(savedDossier.getEmail());
        dto.setDecimalPrecision(savedDossier.getDecimalPrecision());

        // Set pays object from country relationship including currency
        if (savedDossier.getCountry() != null) {
            PaysDTO paysDTO = new PaysDTO();
            paysDTO.setCountry(savedDossier.getCountry().getName());
            paysDTO.setCode(savedDossier.getCountry().getCode());

            // Add currency information if available
            if (savedDossier.getCountry().getCurrency() != null) {
                PaysDTO.CurrencyDTO currencyDTO = new PaysDTO.CurrencyDTO();
                currencyDTO.setCode(savedDossier.getCountry().getCurrency().getCode());
                currencyDTO.setName(savedDossier.getCountry().getCurrency().getName());
                paysDTO.setCurrency(currencyDTO);
            }

            dto.setPays(paysDTO);
        }

        // Set cabinet DTO
        DossierDTO.CabinetDTO cabinetDTO = new DossierDTO.CabinetDTO();
        cabinetDTO.setId(savedDossier.getCabinet().getId());
        dto.setCabinet(cabinetDTO);

        return dto;
    }

    @Override
    @Transactional
    public void deleteDossier(Long dossierId) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Deleting dossier with ID: {}", requestId, dossierId);

        // Verify that the dossier exists
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new RuntimeException("Dossier not found for ID: " + dossierId));

        // Additional business logic for dossier deletion could go here
        // For example, checking if it's safe to delete the dossier

        try {
            // Call Company AI API to delete the company
            log.info("[{}] Calling Company AI API to delete company for dossier ID: {}", requestId, dossierId);
            boolean deleted = companyAiService.deleteCompany(dossierId);

            if (deleted) {
                log.info("[{}] Company deleted successfully from AI service for dossier ID: {}", requestId, dossierId);
            } else {
                log.warn("[{}] Company deletion from AI service returned false for dossier ID: {}", requestId, dossierId);
            }
        } catch (Exception e) {
            // Log the error but continue with the deletion
            log.error("[{}] Error deleting company from AI service for dossier ID {}: {}",
                    requestId, dossierId, e.getMessage(), e);
        }

        // Now delete the dossier from our system
        dossierRepository.deleteById(dossierId);
        log.info("[{}] Dossier deleted successfully with ID: {}", requestId, dossierId);
    }

    // Add this method to DossierServiceImpl
    @Override
    @Transactional
    public DossierDTO updateActivity(Long dossierId, String activity) {
        String requestId = UUID.randomUUID().toString();
        log.info("[{}] Updating activity for dossier ID: {} to: {}", requestId, dossierId, activity);

        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new RuntimeException("Dossier not found for ID: " + dossierId));

        dossier.setActivity(activity);
        Dossier savedDossier = dossierRepository.save(dossier);

        log.info("[{}] Activity updated successfully for dossier ID: {}", requestId, dossierId);

        // Convert to DTO and return (reuse existing conversion logic)
        return getTheDossierById(dossierId);
    }
}