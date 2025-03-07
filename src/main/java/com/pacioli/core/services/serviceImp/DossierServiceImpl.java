package com.pacioli.core.services.serviceImp;

import com.pacioli.core.DTO.DossierDTO;
import com.pacioli.core.Exceptions.ExerciseDateConflictException;
import com.pacioli.core.models.*;
import com.pacioli.core.repositories.*;
import com.pacioli.core.services.DossierService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DossierServiceImpl implements DossierService {

    private final DossierRepository dossierRepository;
    @Autowired
    private CabinetRepository cabinetRepository;

    @Autowired
    private ExerciceRepository exerciseRepository;
    @Autowired
    private EcritureRepository ecritureRepository;
    @Autowired
    private JournalRepository journalRepository;


    @Autowired
    public DossierServiceImpl(DossierRepository dossierRepository) {
        this.dossierRepository = dossierRepository;
    }


    @Override
    @Transactional
    public Dossier createDossier(Dossier dossier, List<Exercise> exercicesData) {
        // Check if a dossier with the same name already exists
        Dossier existingDossier = dossierRepository.findByName(dossier.getName()).orElse(null);

        if (existingDossier != null) {
            // If a Dossier with the same name exists, update its details (except the name)
            existingDossier.setICE(dossier.getICE());
            existingDossier.setAddress(dossier.getAddress());
            existingDossier.setCity(dossier.getCity());
            existingDossier.setPhone(dossier.getPhone());
            existingDossier.setEmail(dossier.getEmail());
            // Update country and code values
            existingDossier.setCountry(dossier.getCountry());
            existingDossier.setCode(dossier.getCode());
            dossier = existingDossier;
        } else {
            // If no Dossier with the same name exists, check the Cabinet
            Cabinet cabinet = cabinetRepository.findById(dossier.getCabinet().getId())
                    .orElseThrow(() -> new RuntimeException("Cabinet non trouvé"));
            dossier.setCabinet(cabinet);
        }

        // Save or update the Dossier
        Dossier savedDossier = dossierRepository.save(dossier);

        // **Create the list of default journals**
        createDefaultJournals(savedDossier);

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

        // **Filter out journals that already exist for this dossier**
        List<Journal> journalsToCreate = defaultJournals.stream()
                .filter(journal -> !journalRepository.existsByNameAndDossierId(journal.getName(), dossier.getId()))
                .toList();

        // **Save only new journals**
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

        // Fetch all "écritures" for the dossier
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
        log.info("Suppression des exercices pour le dossier ID: {}, Identifiants des exercices: {}", dossierId, exerciseIds);

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
        log.info("Suppression des exercices: {}", exercisesToDelete);
        exerciseRepository.deleteAll(exercisesToDelete);
        log.info("Exercices supprimés avec succès.");
    }


    @Override
    @Transactional
    public DossierDTO updateDossier(Long id, Dossier dossierDetails) {
        Dossier existingDossier = dossierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dossier not found for ID: " + id));

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

        Dossier savedDossier = dossierRepository.save(existingDossier);

        // Convert Dossier to DossierDTO
        DossierDTO dto = new DossierDTO();
        dto.setId(savedDossier.getId());
        dto.setName(savedDossier.getName());
        dto.setICE(savedDossier.getICE());
        dto.setAddress(savedDossier.getAddress());
        dto.setCity(savedDossier.getCity());
        dto.setPhone(savedDossier.getPhone());
        dto.setEmail(savedDossier.getEmail());
        return dto;
    }
}
