package com.pacioli.core.controllers;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Piece;
import com.pacioli.core.services.DossierService;
import com.pacioli.core.services.PieceService;
//import org.springframework.core.io.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/pieces")
public class PieceController {

    @Value("${file.upload.dir:Files/}")
    private String uploadDir;
    private final PieceService pieceService;

    private final DossierService dossierService;

    @Autowired
    private ObjectMapper objectMapper;

    public PieceController(PieceService pieceService, DossierService dossierService) {
        this.pieceService = pieceService;
        this.dossierService = dossierService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Piece> getPieceById(@PathVariable Long id) {
        Piece piece = pieceService.getPieceById(id);
        return ResponseEntity.ok(piece);
    }

    @PostMapping
    public ResponseEntity<Piece> savePiece(
            @RequestPart("piece") String pieceData,
            @RequestPart("file") MultipartFile file,
            @RequestParam("dossier_id") Long dossierId) throws IOException {

        // Step 1: Fetch Dossier
        Dossier dossier = dossierService.getDossierById(dossierId);
        if (dossier == null) {
            throw new IllegalArgumentException("Dossier not found for ID: " + dossierId);
        }

        // Step 2: Deserialize piece
        Piece piece;
        try {
            InjectableValues.Std injectableValues = new InjectableValues.Std();
            injectableValues.addValue("dossierId", dossierId);
            objectMapper.setInjectableValues(injectableValues);
            piece = objectMapper.readValue(pieceData, Piece.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse 'piece' JSON data: " + e.getMessage());
        }

        // Step 3: Link Dossier to Piece
        piece.setDossier(dossier);

        // Step 4: Save file to disk
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFilename = file.getOriginalFilename();
        String uniqueFilename = generateUniqueFilename(originalFilename, uploadPath);
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath);
        piece.setFilename(uniqueFilename);

        // Step 5: Save Piece and return Response
        Piece savedPiece = pieceService.savePiece(piece);
        return ResponseEntity.ok(savedPiece);
    }


    // Utility method to ensure unique filenames
    private String generateUniqueFilename(String filename, Path uploadPath) {
        String baseName = FilenameUtils.getBaseName(filename);
        String extension = FilenameUtils.getExtension(filename);
        String uniqueFilename = filename;

        int count = 1;
        while (Files.exists(uploadPath.resolve(uniqueFilename))) {
            uniqueFilename = baseName + "_" + count + "." + extension;
            count++;
        }
        return uniqueFilename;
    }

    @GetMapping
    public ResponseEntity<List<Piece>> getPieces(@RequestParam(required = false) Long dossierId) {
        if(dossierId != null) {
            List<Piece> pieces =
                pieceService.getPiecesByDossier(dossierId);
        return ResponseEntity.ok(pieces);
        }
        return ResponseEntity.ok(new ArrayList<>());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePiece(@PathVariable Long id) {
        pieceService.deletePiece(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFileById(@PathVariable("id") Long pieceId) {
        try {
            // Fetch the Piece
            Piece piece = pieceService.getPieceById(pieceId);
            String filename = piece.getFilename();

            // Locate the file
            Path filePath = Paths.get(uploadDir).resolve(filename).normalize();

            // Check if the file exists and is readable
            if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
                throw new FileNotFoundException("File not found: " + filename);
            }

            // Create a resource
            org.springframework.core.io.Resource resource = new UrlResource(filePath.toUri());

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
    public ResponseEntity<Resource> getFile(@PathVariable Long pieceId) throws IOException {
        // Fetch the piece by ID
        Piece piece = pieceService.getPieceById(pieceId);

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

}
