package com.pacioli.core.controllers;

import com.pacioli.core.DTO.*;
import com.pacioli.core.models.Piece;
import com.pacioli.core.repositories.UserRepository;
import com.pacioli.core.services.DossierService;
import com.pacioli.core.services.PieceService;
import com.pacioli.core.utils.SecurityHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/pieces")
public class PieceController {

    @Value("${file.upload.dir:Files/}")
    private String uploadDir;

    @Autowired
    private PieceService pieceService;

    @Autowired
    private DossierService dossierService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SecurityHelper securityHelper;

    @GetMapping("/dossier/{dossierId}")
    public List<Piece> getPiecesByDossierId(
            @PathVariable Long dossierId,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} fetching pieces for dossier: {}", principal.getUsername(), dossierId);

        UUID userId = extractUserIdFromPrincipal(principal);

        // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this dossier
        boolean hasAccess = securityHelper.isPacioli(principal)
                || dossierService.userHasAccessToDossier(userId, dossierId);

        if (!hasAccess) {
            log.error("User {} attempted to access pieces from unauthorized dossier {}",
                    principal.getUsername(), dossierId);
            throw new SecurityException("User cannot access this dossier");
        }

        return pieceService.getPiecesByDossierIdSortedByDate(dossierId);
    }

    @PostMapping
    public ResponseEntity<Piece> savePiece(
            @RequestPart("piece") String pieceData,
            @RequestPart("file") MultipartFile file,
            @RequestParam("dossier_id") Long dossierId,
            @RequestParam("country") String country,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) throws IOException {

        log.info("User {} uploading piece for dossier: {}", principal.getUsername(), dossierId);

        UUID userId = extractUserIdFromPrincipal(principal);

        // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this dossier
        boolean hasAccess = securityHelper.isPacioli(principal)
                || dossierService.userHasAccessToDossier(userId, dossierId);

        if (!hasAccess) {
            log.error("User {} attempted to upload to unauthorized dossier {}",
                    principal.getUsername(), dossierId);
            throw new SecurityException("User cannot access this dossier");
        }

        Piece savedPiece = pieceService.savePiece(pieceData, file, dossierId, country);
        return ResponseEntity.ok(savedPiece);
    }

    @PostMapping("/save-ecritures-and-facture")
    public ResponseEntity<Piece> saveEcrituresAndFacture(
            @RequestBody String pieceData,
            @RequestParam(name = "piece_id", required = true) Long pieceId,
            @RequestParam(name = "dossier_id", required = true) Long dossierId,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        UUID userId = extractUserIdFromPrincipal(principal);

        // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this dossier
        boolean hasAccess = securityHelper.isPacioli(principal)
                || dossierService.userHasAccessToDossier(userId, dossierId);

        if (!hasAccess) {
            log.error("User {} attempted to save ecritures in unauthorized dossier {}",
                    principal.getUsername(), dossierId);
            throw new SecurityException("User cannot access this dossier");
        }

        // ✅ ADDITIONAL SECURITY CHECK: Verify the piece belongs to the dossier
        Piece piece = pieceService.getPieceById(pieceId);
        if (!piece.getDossier().getId().equals(dossierId)) {
            log.error("User {} attempted to access piece {} not belonging to dossier {}",
                    principal.getUsername(), pieceId, dossierId);
            throw new SecurityException("Piece does not belong to the specified dossier");
        }

        Piece savedPiece = pieceService.saveEcrituresAndFacture(pieceId, dossierId, pieceData);
        return ResponseEntity.ok(savedPiece);
    }

    @GetMapping
    public ResponseEntity<Page<PieceDTO>> getPieces(
            @RequestParam(required = false) Long dossierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        UUID userId = extractUserIdFromPrincipal(principal);

        if (dossierId != null) {
            log.info("User {} fetching pieces for dossier: {}", principal.getUsername(), dossierId);

            // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this dossier
            boolean hasAccess = securityHelper.isPacioli(principal)
                    || dossierService.userHasAccessToDossier(userId, dossierId);

            if (!hasAccess) {
                log.error("User {} attempted to access pieces from unauthorized dossier {}",
                        principal.getUsername(), dossierId);
                throw new SecurityException("User cannot access this dossier");
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<PieceDTO> pieceDTOs = pieceService.getPiecesByDossier(dossierId, pageable);
            return ResponseEntity.ok(pieceDTOs);
        } else {
            // Get all pieces across user's accessible dossiers
            log.info("User {} fetching all accessible pieces", principal.getUsername());

            // Sinon, retourner seulement les pièces accessibles par l'utilisateur
            Pageable pageable = PageRequest.of(page, size);
            Page<PieceDTO> pieceDTOs = pieceService.getPiecesForUser(userId, pageable);
            return ResponseEntity.ok(pieceDTOs);
        }
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<PieceDTO> getPieceDetails(@PathVariable Long id,
                                                    @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} fetching details for piece: {}", principal.getUsername(), id);

        UUID userId = extractUserIdFromPrincipal(principal);

        // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this piece's dossier
        Piece piece = pieceService.getPieceById(id);

        boolean hasAccess = securityHelper.isPacioli(principal)
                || dossierService.userHasAccessToDossier(userId, piece.getDossier().getId());

        if (!hasAccess) {
            log.error("User {} attempted to access piece from unauthorized dossier {}",
                    principal.getUsername(), piece.getDossier().getId());
            throw new SecurityException("User cannot access this piece");
        }

        PieceDTO pieceDetails = pieceService.getPieceDetails(id);
        return ResponseEntity.ok(pieceDetails);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePiece(@PathVariable Long id,
                                            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} deleting piece: {}", principal.getUsername(), id);

        UUID userId = extractUserIdFromPrincipal(principal);

        // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this piece's dossier
        Piece piece = pieceService.getPieceById(id);

        boolean hasAccess = securityHelper.isPacioli(principal)
                || dossierService.userHasAccessToDossier(userId, piece.getDossier().getId());

        if (!hasAccess) {
            log.error("User {} attempted to delete piece from unauthorized dossier {}",
                    principal.getUsername(), piece.getDossier().getId());
            throw new SecurityException("User cannot access this piece");
        }

        pieceService.deletePiece(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFileById(@PathVariable("id") Long pieceId,
                                                                                 @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} downloading file for piece: {}", principal.getUsername(), pieceId);

        try {
            UUID userId = extractUserIdFromPrincipal(principal);

            // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this piece's dossier
            Piece piece = pieceService.getPieceById(pieceId);

            boolean hasAccess = securityHelper.isPacioli(principal)
                    || dossierService.userHasAccessToDossier(userId, piece.getDossier().getId());

            if (!hasAccess) {
                log.error("User {} attempted to download file from unauthorized piece {}",
                        principal.getUsername(), pieceId);
                throw new SecurityException("User cannot access this piece");
            }

            // Fetch the Piece
            String filename = piece.getFilename();

            // Locate the file
            Path filePath = Paths.get(uploadDir).resolve(filename).normalize();

            // Check if the file exists and is readable
            if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
                throw new FileNotFoundException("File not found: " + filename);
            }

            // Create a resource
            Resource resource = new UrlResource(filePath.toUri());

            // Return the file as a response
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (FileNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{pieceId}/file")
    public ResponseEntity<Resource> getFile(@PathVariable Long pieceId,
                                            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) throws IOException {

        log.info("User {} accessing file for piece: {}", principal.getUsername(), pieceId);

        UUID userId = extractUserIdFromPrincipal(principal);

        // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this piece's dossier
        Piece piece = pieceService.getPieceById(pieceId);

        boolean hasAccess = securityHelper.isPacioli(principal)
                || dossierService.userHasAccessToDossier(userId, piece.getDossier().getId());

        if (!hasAccess) {
            log.error("User {} attempted to access file from unauthorized piece {}",
                    principal.getUsername(), pieceId);
            throw new SecurityException("User cannot access this piece");
        }

        // Construct the file path
        Path filePath = Paths.get(uploadDir).resolve(piece.getFilename());
        File file = filePath.toFile();

        // Check if the file exists
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        // Serve the file as a Resource
        Resource resource = new FileSystemResource(file);
        String contentType = Files.probeContentType(file.toPath());
        contentType = contentType == null ? "application/octet-stream" : contentType;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(resource);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updatePieceStatus(
            @PathVariable Long id,
            @RequestBody UpdatePieceStatusRequest requestBody,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} updating status for piece {} to {}",
                principal.getUsername(), id, requestBody.getStatus());

        UUID userId = extractUserIdFromPrincipal(principal);

        // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this piece's dossier
        Piece piece = pieceService.getPieceById(id);

        boolean hasAccess = securityHelper.isPacioli(principal)
                || dossierService.userHasAccessToDossier(userId, piece.getDossier().getId());

        if (!hasAccess) {
            log.error("User {} attempted to update status of unauthorized piece {}",
                    principal.getUsername(), id);
            throw new SecurityException("User cannot access this piece");
        }

        Piece updatedPiece = pieceService.updatePieceStatus(id, requestBody.getStatus());

        if (updatedPiece != null) {
            return ResponseEntity.ok(updatedPiece);
        } else {
            return ResponseEntity.badRequest().body("Piece not found or update failed");
        }
    }

    @GetMapping("/stats/dossier/{dossierId}")
    public ResponseEntity<PieceStatsDTO> getPieceStatsByDossier(@PathVariable Long dossierId,
                                                                @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} fetching stats for dossier: {}", principal.getUsername(), dossierId);

        UUID userId = extractUserIdFromPrincipal(principal);

        // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this dossier
        boolean hasAccess = securityHelper.isPacioli(principal)
                || dossierService.userHasAccessToDossier(userId, dossierId);

        if (!hasAccess) {
            log.error("User {} attempted to access stats from unauthorized dossier {}",
                    principal.getUsername(), dossierId);
            throw new SecurityException("User cannot access this dossier");
        }

        PieceStatsDTO stats = pieceService.getPieceStatsByDossier(dossierId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/cabinet/{cabinetId}")
    public ResponseEntity<List<PieceStatsDTO>> getPieceStatsByCabinet(@PathVariable Long cabinetId,
                                                                      @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} fetching stats for cabinet: {}", principal.getUsername(), cabinetId);

        UUID userId = extractUserIdFromPrincipal(principal);

        // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this cabinet
        boolean hasAccess = securityHelper.isPacioli(principal)
                || dossierService.userHasAccessToCabinet(userId, cabinetId);

        if (!hasAccess) {
            log.error("User {} attempted to access stats from unauthorized cabinet {}",
                    principal.getUsername(), cabinetId);
            throw new SecurityException("User cannot access this cabinet");
        }

        List<PieceStatsDTO> stats = pieceService.getPieceStatsByCabinet(cabinetId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{pieceId}/files")
    public ResponseEntity<byte[]> getPieceFiles(@PathVariable Long pieceId,
                                                @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} downloading files for piece: {}", principal.getUsername(), pieceId);

        try {
            UUID userId = extractUserIdFromPrincipal(principal);

            // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this piece's dossier
            Piece piece = pieceService.getPieceById(pieceId);

            boolean hasAccess = securityHelper.isPacioli(principal)
                    || dossierService.userHasAccessToDossier(userId, piece.getDossier().getId());

            if (!hasAccess) {
                log.error("User {} attempted to download files from unauthorized piece {}",
                        principal.getUsername(), pieceId);
                throw new SecurityException("User cannot access this piece");
            }

            byte[] zipContent = pieceService.getPieceFilesAsZip(pieceId);

            if (zipContent == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"piece_" + pieceId + "_files.zip\"")
                    .body(zipContent);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PatchMapping("/{id}/force-not-duplicate")
    public ResponseEntity<Piece> forceNotDuplicate(@PathVariable Long id,
                                                   @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        log.info("User {} forcing piece {} as not duplicate", principal.getUsername(), id);

        UUID userId = extractUserIdFromPrincipal(principal);

        // ✅ SECURITY CHECK: Verify PACIOLI or user has access to this piece's dossier
        Piece piece = pieceService.getPieceById(id);

        boolean hasAccess = securityHelper.isPacioli(principal)
                || dossierService.userHasAccessToDossier(userId, piece.getDossier().getId());

        if (!hasAccess) {
            log.error("User {} attempted to force piece {} as not duplicate without access",
                    principal.getUsername(), id);
            throw new SecurityException("User cannot access this piece");
        }

        Piece updated = pieceService.forcePieceNotDuplicate(id);
        return ResponseEntity.ok(updated);
    }

    private UUID extractUserIdFromPrincipal(org.springframework.security.core.userdetails.User principal) {
        if (principal == null) {
            log.error("Principal is null - user not authenticated");
            throw new SecurityException("User not authenticated");
        }

        String username = principal.getUsername();
        log.debug("Extracting user ID for username: {}", username);

        try {
            // Look up the user by username to get the UUID
            com.pacioli.core.models.User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        log.error("User not found for username: {}", username);
                        return new SecurityException("User not found");
                    });

            if (user.getId() == null) {
                log.error("User ID is null for user: {}", username);
                throw new SecurityException("User ID not found");
            }

            log.debug("Successfully extracted user ID: {} for user: {}", user.getId(), username);
            return user.getId();

        } catch (SecurityException e) {
            // Re-throw security exceptions
            throw e;
        } catch (Exception e) {
            log.error("Error extracting user ID for username {}: {}", username, e.getMessage(), e);
            throw new SecurityException("Error extracting user information: " + e.getMessage());
        }
    }
}