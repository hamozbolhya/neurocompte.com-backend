package com.pacioli.core.services.serviceImp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacioli.core.DTO.PieceDTO;
import com.pacioli.core.DTO.PieceStatsDTO;
import com.pacioli.core.enums.PieceStatus;
import com.pacioli.core.models.*;
import com.pacioli.core.repositories.*;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.PieceService;
import com.pacioli.core.services.UserService;
import com.pacioli.core.services.serviceImp.mappers.PieceDTOMapper;
import com.pacioli.core.services.serviceImp.pieces.AIService;
import com.pacioli.core.services.serviceImp.pieces.FileProcessingResult;
import com.pacioli.core.services.serviceImp.pieces.FileService;
import com.pacioli.core.services.serviceImp.pieces.PieceProcessingService;
import com.pacioli.core.utils.FileContentHashing;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PieceServiceImpl implements PieceService {

    private final PieceRepository pieceRepository;
    private final PieceDTOMapper pieceDTOMapper;
    private final DossierRepository dossierRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FileService fileService;
    private final AIService aiService;
    private final PieceProcessingService pieceProcessingService;
    private final ObjectMapper objectMapper;
    private final DuplicateDetectionService duplicateDetectionService;
    private final AuditService auditService;
    private final UserService userService;


    public PieceServiceImpl(PieceRepository pieceRepository, PieceDTOMapper pieceDTOMapper, DossierRepository dossierRepository, SimpMessagingTemplate messagingTemplate, FileService fileService, AIService aiService, PieceProcessingService pieceProcessingService, ObjectMapper objectMapper, EcritureRepository ecritureRepository, LineRepository lineRepository, DuplicateDetectionService duplicateDetectionService, AuditService auditService, UserService userService) {
        this.pieceRepository = pieceRepository;
        this.pieceDTOMapper = pieceDTOMapper;
        this.dossierRepository = dossierRepository;
        this.messagingTemplate = messagingTemplate;
        this.fileService = fileService;
        this.aiService = aiService;
        this.pieceProcessingService = pieceProcessingService;
        this.objectMapper = objectMapper;
        this.duplicateDetectionService = duplicateDetectionService;
        this.auditService = auditService;
        this.userService = userService;
    }

    @Override
    @Transactional
    public Piece savePiece(String pieceData, MultipartFile file, @NonNull Long dossierId, String country) {
        User currentUser = userService.getCurrentUser();

        try {
            log.info("saving piece {}", pieceData);
            Piece piece = deserializePiece(pieceData, dossierId);

            // Original name + MD5 from the actual upload (JSON body often omits or misstates originalFileName)
            try {
                piece.setFileHash(FileContentHashing.md5Hex(file.getBytes()));
            } catch (IOException ioe) {
                log.warn("Could not compute MD5 for upload: {}", ioe.getMessage());
            }
            String multipartOriginalName = file.getOriginalFilename();
            if (multipartOriginalName != null && !multipartOriginalName.isBlank()) {
                piece.setOriginalFileName(multipartOriginalName.trim());
            }

            // Validate and save file - returns both filename and the file to process
            FileProcessingResult fileResult = fileService.validateAndSaveFile(file, piece.getType());
            String formattedFilename = fileResult.getFilename();
            MultipartFile fileToProcess = fileResult.getFileToProcess();

            Dossier dossier = dossierRepository.findById(dossierId).orElseThrow(() -> new IllegalArgumentException("Dossier introuvable pour l'ID: " + dossierId));

            // Récupérer le cabinet cible (celui du dossier)
            Long targetCabinetId = dossier.getCabinet() != null ? dossier.getCabinet().getId() : null;
            String targetCabinetName = dossier.getCabinet() != null ? dossier.getCabinet().getName() : null;

            // Initialize piece with the actual saved filename
            initializePiece(piece, dossier, formattedFilename);

            // Process with AI - send the converted PNG file if PDF was converted
            aiService.processFileBasedOnType(fileToProcess, formattedFilename, dossierId, country, piece.getType());

            // Save and return
            Piece savedPiece = pieceRepository.save(piece);

            // Audit: Création de pièce avec cabinet cible
            auditService.logSuccessWithTargetCabinet(currentUser, "CREATE", "Piece", savedPiece.getId(), savedPiece.getOriginalFileName() != null ? savedPiece.getOriginalFileName() : savedPiece.getFilename(), null, savedPiece, targetCabinetId, targetCabinetName);

            log.info("✅ Piece saved with ID: {}", savedPiece.getId());

            return savedPiece;

        } catch (IOException e) {
            // Audit: Échec création - erreur IO
            auditService.logFailure(currentUser, "CREATE", "Piece", null, file != null ? file.getOriginalFilename() : "unknown", "IO Error: " + e.getMessage());
            log.error("Validation/processing error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            // Audit: Échec création - erreur interne
            auditService.logFailure(currentUser, "CREATE", "Piece", null, file != null ? file.getOriginalFilename() : "unknown", "Internal Error: " + e.getMessage());
            log.error("Unexpected internal error:", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne lors de l'enregistrement de la pièce.", e);
        }
    }


    @Override
    @Transactional
    public Piece saveEcrituresAndFacture(@NonNull Long pieceId, @NonNull Long dossierId, String pieceData,
            @Nullable JsonNode originalAiResponse) {
        User currentUser = userService.getCurrentUser();

        Dossier dossier = dossierRepository.findById(dossierId).orElseThrow(() -> new IllegalArgumentException("Dossier not found for ID: " + dossierId));
        Piece piece = pieceRepository.findById(pieceId).orElseThrow(() -> new IllegalArgumentException("Piece not found for ID: " + pieceId));

        // Récupérer le cabinet cible (celui du dossier)
        Long targetCabinetId = dossier.getCabinet() != null ? dossier.getCabinet().getId() : null;
        String targetCabinetName = dossier.getCabinet() != null ? dossier.getCabinet().getName() : null;

        try {
            // ** Step 1: Save FactureData first **
            saveFactureDataForPiece(piece, pieceData, originalAiResponse);

            // ** Step 2: Save Ecritures temporarily **
            saveEcrituresForPiece(piece, dossierId, pieceData, originalAiResponse);

            // ** Step 3: ENSURE AMOUNT IS SET - CRITICAL FIX **
            ensurePieceAmountIsSet(piece, pieceData, dossier, originalAiResponse);

            // ** Step 4: Perform comprehensive duplicate check **
            if (duplicateDetectionService != null) {
                Optional<Piece> comprehensiveDuplicate = duplicateDetectionService.performComprehensiveDuplicateCheck(piece);

                if (comprehensiveDuplicate.isPresent()) {
                    log.warn("🚫 Comprehensive duplicate detected, marking piece {} as duplicate of piece {}", piece.getId(), comprehensiveDuplicate.get().getId());

                    if (piece.getEcritures() != null) {
                        piece.getEcritures().clear();
                        pieceRepository.save(piece);
                    }

                    // ✅ CRITICAL FIX: Set status to DUPLICATE before marking
                    piece.setStatus(PieceStatus.DUPLICATE);
                    piece.setIsDuplicate(true);
                    piece.setOriginalPiece(comprehensiveDuplicate.get());

                    // Save the piece with duplicate status
                    piece = pieceRepository.save(piece);

                    duplicateDetectionService.markAsDuplicate(piece, comprehensiveDuplicate.get());

                    Piece orig = comprehensiveDuplicate.get();
                    String auditOriginalName = orig.getOriginalFileName();
                    if (auditOriginalName == null || auditOriginalName.isBlank()) {
                        auditOriginalName = orig.getFilename();
                    }
                    // Audit: Détection de doublon avec cabinet cible
                    auditService.logSuccessWithTargetCabinet(currentUser, "DUPLICATE_DETECTED", "Piece", piece.getId(), piece.getOriginalFileName(), null, Map.of("originalPieceId", orig.getId(), "originalPieceName", auditOriginalName != null ? auditOriginalName : ""), targetCabinetId, targetCabinetName);
                    log.info("⏭️ Marked piece {} as DUPLICATE of piece {} (original label: {})",
                            piece.getId(), orig.getId(),
                            orig.getOriginalFileName() != null && !orig.getOriginalFileName().isBlank()
                                    ? orig.getOriginalFileName() : orig.getFilename());
                    notifyPiecesUpdate(dossierId);
                    return piece;
                }
            }

            // ** Step 5: Update the status of the Piece **
            piece.setStatus(PieceStatus.PROCESSED);
            piece.setIsDuplicate(false); // Ensure it's not marked as duplicate
            piece = pieceRepository.save(piece);

            // Audit: Traitement réussi avec cabinet cible
            auditService.logSuccessWithTargetCabinet(currentUser, "PROCESS", "Piece", piece.getId(), piece.getOriginalFileName(), null, Map.of("status", piece.getStatus(), "amount", piece.getAmount()), targetCabinetId, targetCabinetName);

            log.info("✅ Piece {} successfully processed with amount: {}", piece.getId(), piece.getAmount());

        } catch (Exception e) {
            log.error("💥 Error in saveEcrituresAndFacture for piece {}: {}", piece.getId(), e.getMessage(), e);
            piece.setStatus(PieceStatus.REJECTED);
            pieceRepository.save(piece);

            // Audit: Échec traitement avec cabinet cible
            auditService.logFailureWithTargetCabinet(currentUser, "PROCESS", "Piece", piece.getId(), piece.getOriginalFileName(), "Processing Error: " + e.getMessage(), targetCabinetId, targetCabinetName);
        } finally {
            notifyPiecesUpdate(dossierId);
        }

        return piece;
    }

    private void ensurePieceAmountIsSet(Piece piece, String pieceData, Dossier dossier, JsonNode originalAiResponse) {
        // If amount is already set, keep it
        if (piece.getAmount() != null) {
            log.info("💰 Piece {} already has amount: {}", piece.getId(), piece.getAmount());
            return;
        }

        Double calculatedAmount = null;

        // Priority 1: Try to get amount from original AI response first (most accurate)
        try {
            JsonNode originalEcritures = parseOriginalAiResponse(originalAiResponse);
            if (originalEcritures != null && originalEcritures.isArray()) {
                BigDecimal maxAmount = BigDecimal.ZERO;
                for (JsonNode entry : originalEcritures) {
                    String debitStr = entry.has("DebitAmt") ? entry.get("DebitAmt").asText("0") : "0";
                    String creditStr = entry.has("CreditAmt") ? entry.get("CreditAmt").asText("0") : "0";

                    BigDecimal debit = new BigDecimal(debitStr);
                    BigDecimal credit = new BigDecimal(creditStr);

                    maxAmount = maxAmount.max(debit.max(credit));
                }
                calculatedAmount = maxAmount.doubleValue();
                log.info("💰 Calculated amount from original AI: {}", calculatedAmount);
            }
        } catch (Exception e) {
            log.warn("Error getting amount from original AI response: {}", e.getMessage());
        }

        // Priority 2: Use AI amount from piece
        if (calculatedAmount == null && piece.getAiAmount() != null) {
            calculatedAmount = piece.getAiAmount();
            log.info("💰 Using AI amount from piece: {}", calculatedAmount);
        }

        // Priority 3: Fallback to calculating from ecritures
        if (calculatedAmount == null) {
            calculatedAmount = calculateAmountFromEcritures(pieceData, dossier, originalAiResponse);
            log.info("💰 Calculated amount from ecritures: {}", calculatedAmount);
        }

        // Apply conversion if needed
        if (calculatedAmount != null) {
            if (piece.getExchangeRate() != null && piece.getExchangeRate() > 0) {
                // Use converted amount - convert AI amount if available, otherwise use calculated amount
                Double amountToConvert = piece.getAiAmount() != null ? piece.getAiAmount() : calculatedAmount;
                Double convertedAmount = amountToConvert * piece.getExchangeRate();
                piece.setAmount(convertedAmount);
                log.info("💰 Set converted amount: {} (Original: {} × Rate: {})", convertedAmount, amountToConvert, piece.getExchangeRate());
            } else {
                // No conversion, use calculated amount directly
                piece.setAmount(calculatedAmount);
                log.info("💰 Set direct amount: {}", calculatedAmount);
            }

            // Save the amount immediately
            pieceRepository.save(piece);
        } else {
            log.warn("⚠️ Could not determine amount for piece {}", piece.getId());
        }
    }

    /**
     * ✅ Deserialize ecritures for amount calculation
     */
    private List<Ecriture> deserializeEcritures(String pieceData, Dossier dossier) {
        try {
            JsonNode rootNode = objectMapper.readTree(pieceData);
            JsonNode ecrituresNode = rootNode.get("ecritures");

            if (ecrituresNode == null || ecrituresNode.isNull()) {
                log.warn("'ecritures' field is missing or null in the JSON");
                return Collections.emptyList();
            }

            List<Ecriture> ecritures = new ArrayList<>();
            for (JsonNode ecritureNode : ecrituresNode) {
                Ecriture ecriture = objectMapper.treeToValue(ecritureNode, Ecriture.class);
                ecritures.add(ecriture);
            }
            return ecritures;
        } catch (IOException e) {
            log.error("Failed to parse 'ecritures' JSON: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Invalid JSON format for 'ecritures': " + e.getMessage(), e);
        }
    }

    /**
     * ✅ Parse original AI response for amount extraction
     */
    private JsonNode parseOriginalAiResponse(JsonNode originalAiResponse) {
        if (originalAiResponse == null) {
            log.debug("Original AI response is null");
            return null;
        }

        try {
            String responseText = originalAiResponse.asText();
            JsonNode parsedOriginal = objectMapper.readTree(responseText);
            JsonNode originalEcritures = parsedOriginal.get("ecritures");
            return originalEcritures != null ? originalEcritures : parsedOriginal.get("Ecritures");
        } catch (Exception e) {
            log.error("Error parsing original AI response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * ✅ Calculate amount from ecritures (fallback method)
     */
    private Double calculateAmountFromEcritures(String pieceData, Dossier dossier, JsonNode originalAiResponse) {
        try {
            List<Ecriture> ecritures = deserializeEcritures(pieceData, dossier);
            return ecritures.stream().flatMap(e -> e.getLines().stream()).mapToDouble(line -> Math.max(line.getDebit() != null ? line.getDebit() : 0.0, line.getCredit() != null ? line.getCredit() : 0.0)).max().orElse(0.0);
        } catch (Exception e) {
            log.warn("Error calculating amount from ecritures: {}", e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional
    public Page<PieceDTO> getPiecesForUser(@NonNull UUID userId, @NonNull Pageable pageable) {
        Page<Piece> piecesPage = pieceRepository.findByDossierCabinetUsersId(userId, pageable);
        List<PieceDTO> pieceDTOs = piecesPage.getContent().stream().map(pieceDTOMapper::toBasicDTO).collect(Collectors.toList());
        return new PageImpl<>(new ArrayList<>(pieceDTOs), pageable, piecesPage.getTotalElements());
    }


    // Core CRUD operations
    @Override
    @Transactional
    public Page<PieceDTO> getPiecesByDossier(@NonNull Long dossierId, @NonNull Pageable pageable) {
        Page<Piece> piecesPage = pieceRepository.findByDossierId(dossierId, pageable);
        List<PieceDTO> pieceDTOs = piecesPage.getContent().stream().map(pieceDTOMapper::toBasicDTO).collect(Collectors.toList());
        return new PageImpl<>(new ArrayList<>(pieceDTOs), pageable, piecesPage.getTotalElements());
    }

    @Override
    @Transactional
    public List<Piece> getPiecesByDossierIdSortedByDate(@NonNull Long dossierId) {
        List<Piece> pieces = pieceRepository.findByDossierIdWithDetailsOrderByUploadDateDesc(dossierId);
        return pieces;
    }


    @Override
    public Piece getPieceById(@NonNull Long id) {
        return pieceRepository.findById(id).orElseThrow(() -> {
            return new IllegalArgumentException("Piece with id " + id + " not found");
        });
    }

    @Override
    @Transactional
    public PieceDTO getPieceDetails(@NonNull Long pieceId) {
        Piece piece = getPieceById(pieceId);
        PieceDTO dto = pieceDTOMapper.toBasicDTO(piece);
        pieceDTOMapper.addFactureDataIfExists(piece, dto);
        pieceDTOMapper.addEcrituresIfExists(piece, dto);
        return dto;
    }

    @Override
    @Transactional
    public void deletePiece(@NonNull Long id) {
        User currentUser = userService.getCurrentUser();

        try {
            Piece piece = getPieceById(id);
            String pieceName = piece.getOriginalFileName() != null ? piece.getOriginalFileName() : piece.getFilename();

            // Récupérer le cabinet cible (celui du dossier de la pièce)
            Long targetCabinetId = piece.getDossier() != null && piece.getDossier().getCabinet() != null ? piece.getDossier().getCabinet().getId() : null;
            String targetCabinetName = piece.getDossier() != null && piece.getDossier().getCabinet() != null ? piece.getDossier().getCabinet().getName() : null;

            // Audit: Suppression de pièce avec cabinet cible (avant suppression)
            auditService.logSuccessWithTargetCabinet(currentUser, "DELETE", "Piece", id, pieceName, piece, null, targetCabinetId, targetCabinetName);

            pieceRepository.deleteById(id);

            log.info("Piece with id {} deleted successfully", id);

        } catch (Exception e) {
            // Audit: Échec suppression
            auditService.logFailure(currentUser, "DELETE", "Piece", id, "Piece-" + id, e.getMessage());
            throw e;
        }
    }

    // Status operations
    @Override
    @Transactional
    public Piece updatePieceStatus(@NonNull Long pieceId, String newStatus) {
        Piece piece = getPieceById(pieceId);
        PieceStatus status = PieceStatus.valueOf(newStatus.toUpperCase());

        piece.setStatus(status);
        Piece updatedPiece = pieceRepository.save(piece);
        return updatedPiece;
    }

    @Override
    @Transactional
    public Piece forcePieceNotDuplicate(@NonNull Long pieceId) {
        User currentUser = userService.getCurrentUser();

        Piece piece = getPieceById(pieceId);

        // Récupérer le cabinet cible (celui du dossier de la pièce)
        Long targetCabinetId = piece.getDossier() != null && piece.getDossier().getCabinet() != null ? piece.getDossier().getCabinet().getId() : null;
        String targetCabinetName = piece.getDossier() != null && piece.getDossier().getCabinet() != null ? piece.getDossier().getCabinet().getName() : null;

        // ✅ FIXED CONDITION: Allow if isDuplicate = true OR status = DUPLICATE
        if (!Boolean.TRUE.equals(piece.getIsDuplicate()) && piece.getStatus() != PieceStatus.DUPLICATE) {
            String errorMessage = "Seules les pièces dupliquées peuvent être forcées à être considérées comme non dupliquées.";

            // Audit: Échec force avec cabinet cible
            auditService.logFailureWithTargetCabinet(currentUser, "FORCE_NOT_DUPLICATE", "Piece", pieceId, piece.getOriginalFileName(), errorMessage, targetCabinetId, targetCabinetName);

            throw new IllegalStateException(errorMessage);
        }

        Piece forcedPiece = pieceProcessingService.forcePieceAsNotDuplicate(piece);

        // Audit: Force pièce non dupliquée avec cabinet cible
        auditService.logSuccessWithTargetCabinet(currentUser, "FORCE_NOT_DUPLICATE", "Piece", pieceId, piece.getOriginalFileName(), Map.of("wasDuplicate", true, "wasStatus", piece.getStatus()), Map.of("isDuplicate", false, "newStatus", forcedPiece.getStatus()), targetCabinetId, targetCabinetName);

        return forcedPiece;
    }


    @Override
    @Transactional
    public PieceStatsDTO getPieceStatsByDossier(@NonNull Long dossierId) {
        PieceStatsDTO stats = pieceRepository.getPieceStatsByDossierId(dossierId);
        PieceStatsDTO result = (stats != null) ? stats : createEmptyStats(dossierId);
        return result;
    }

    @Override
    @Transactional
    public List<PieceStatsDTO> getPieceStatsByCabinet(@NonNull Long cabinetId) {
        List<PieceStatsDTO> stats = pieceRepository.getPieceStatsByCabinetId(cabinetId);
        return stats;
    }

    // File operations
    @Override
    public byte[] getPieceFilesAsZip(@NonNull Long pieceId) {
        return pieceProcessingService.createPieceFilesZip(pieceId);
    }

    // Notification operations
    @Override
    @Transactional
    public void notifyPiecesUpdate(@NonNull Long dossierId) {
        // NE PAS AUDITER - c'est une notification technique

        if (messagingTemplate == null) {
            log.error("❌ messagingTemplate is null! Cannot notify WebSocket for dossier {}", dossierId);
            return;
        }

        try {
            // Full dossier list — never paginate here (a fixed page size would truncate WebSocket payloads).
            List<Piece> pieces = pieceRepository.findByDossierIdOrderByUploadDateDesc(dossierId);
            List<PieceDTO> basicDTOs = pieces.stream().map(pieceDTOMapper::toBasicDTO).collect(Collectors.toList());

            messagingTemplate.convertAndSend("/topic/dossier-pieces/" + dossierId, new ArrayList<>(basicDTOs));

            log.debug("Notified WebSocket /topic/dossier-pieces/{} — {} pieces", dossierId, basicDTOs.size());

        } catch (Exception e) {
            log.error("💥 Failed to notify WebSocket for dossier {}: {}", dossierId, e.getMessage(), e);
            sendWebSocketError(dossierId, e);
        }
    }

    // Private helper methods
    private Piece deserializePiece(String pieceData, Long dossierId) {
        try {
            Piece piece = objectMapper.readValue(pieceData, Piece.class);
            Dossier dossier = new Dossier();
            dossier.setId(dossierId);
            piece.setDossier(dossier);
            return piece;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse 'piece' JSON data: " + e.getMessage());
        }
    }

    private void initializePiece(Piece piece, Dossier dossier, String filename) {
        piece.setDossier(dossier);
        piece.setUploadDate(new Date());
        piece.setIsDuplicate(false);
        piece.setFilename(filename);
        piece.setStatus(PieceStatus.UPLOADED);
    }

    private PieceStatsDTO createEmptyStats(Long dossierId) {
        return dossierRepository.findById(Objects.requireNonNull(dossierId, "dossierId")).map(dossier -> {
            PieceStatsDTO stats = new PieceStatsDTO();
            stats.setDossierId(dossier.getId());
            stats.setDossierName(dossier.getName());
            stats.setTotalCount(0L);
            stats.setUploadedCount(0L);
            stats.setProcessedCount(0L);
            stats.setRejectedCount(0L);
            stats.setProcessingCount(0L);

            if (dossier.getCountry() != null) {
                stats.setCountryCode(dossier.getCountry().getCode());
                if (dossier.getCountry().getCurrency() != null) {
                    stats.setDossierCurrency(dossier.getCountry().getCurrency().getCode());
                }
            }
            return stats;
        }).orElse(new PieceStatsDTO());
    }

    private void sendWebSocketError(Long dossierId, Exception e) {
        try {
            Map<String, Object> errorMessage = new HashMap<>();
            errorMessage.put("error", true);
            errorMessage.put("message", "Failed to load pieces: " + e.getMessage());
            errorMessage.put("dossierId", dossierId);
            errorMessage.put("timestamp", new Date());

            messagingTemplate.convertAndSend("/topic/dossier-pieces/" + dossierId, errorMessage);
        } catch (Exception notifyError) {
            log.error("Failed to send error notification to WebSocket: {}", notifyError.getMessage());
        }
    }


    private void saveFactureDataForPiece(Piece piece, String pieceData, JsonNode originalAiResponse) {
        pieceProcessingService.saveFactureDataForPiece(piece, pieceData, originalAiResponse);
    }

    private void saveEcrituresForPiece(Piece piece, Long dossierId, String pieceData, JsonNode originalAiResponse) {
        pieceProcessingService.saveEcrituresForPiece(piece, Objects.requireNonNull(dossierId, "dossierId"), pieceData,
                originalAiResponse);
    }
}