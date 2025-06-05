package com.pacioli.core.controllers;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.pacioli.core.DTO.*;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    @GetMapping("/{Id}")
    public List<Piece> getPiecesByDossierId(@PathVariable Long dossierId) {
        return pieceService.getPiecesByDossierIdSortedByDate(dossierId);
    }

    @PostMapping
    public ResponseEntity<Piece> savePiece(
            @RequestPart("piece") String pieceData,
            @RequestPart("file") MultipartFile file,
            @RequestParam("dossier_id") Long dossierId,
            @RequestParam("country") String country
            ) throws IOException {
        log.info("peiceData: {}, dossier_id:{}" , pieceData, dossierId);
        Piece savedPiece = pieceService.savePiece(pieceData, file, dossierId, country);
        return ResponseEntity.ok(savedPiece);
    }

    /**
     * Endpoint to save Ecritures and Facture Data for a given Piece.
     */
    @PostMapping("/save-ecritures-and-facture")
    public ResponseEntity<Piece> saveEcrituresAndFacture(
            @RequestBody String pieceData,
            @RequestParam(name = "piece_id", required = true) Long pieceId,
            @RequestParam(name = "dossier_id", required = true) Long dossierId) {
        Piece savedPiece = pieceService.saveEcrituresAndFacture(pieceId, dossierId, pieceData);
        return ResponseEntity.ok(savedPiece);
    }

    @GetMapping
    public ResponseEntity<List<PieceDTO>> getPieces(@RequestParam(required = false) Long dossierId) {
        if(dossierId != null) {
            List<Piece> pieces = pieceService.getPiecesByDossier(dossierId);

            // Convert to DTOs with original piece information
            List<PieceDTO> pieceDTOs = pieces.stream()
                    .map(piece -> {
                        PieceDTO dto = new PieceDTO();
                        dto.setId(piece.getId());
                        dto.setFilename(piece.getFilename());
                        dto.setOriginalFileName(piece.getOriginalFileName()); // Original filename
                        dto.setType(piece.getType());
                        dto.setStatus(piece.getStatus());
                        dto.setUploadDate(piece.getUploadDate());
                        dto.setAmount(piece.getAmount());
                        dto.setDossierId(piece.getDossier().getId());
                        dto.setDossierName(piece.getDossier().getName());

                        // Add duplicate information
                        dto.setIsDuplicate(piece.getIsDuplicate());
                        if (piece.getOriginalPiece() != null) {
                            dto.setOriginalPieceId(piece.getOriginalPiece().getId());
                            dto.setOriginalPieceName(piece.getOriginalPiece().getOriginalFileName());
                        }

                        // Add AI currency and amount info
                        dto.setAiCurrency(piece.getAiCurrency());
                        dto.setAiAmount(piece.getAiAmount());

                        // Add exchange rate info
                        dto.setExchangeRate(piece.getExchangeRate());
                        dto.setConvertedCurrency(piece.getConvertedCurrency());
                        dto.setExchangeRateDate(piece.getExchangeRateDate());
                        dto.setExchangeRateUpdated(piece.getExchangeRateUpdated());

                        // Add FactureData if exists
                        if (piece.getFactureData() != null) {
                            FactureDataDTO factureDataDTO = new FactureDataDTO();
                            factureDataDTO.setInvoiceNumber(piece.getFactureData().getInvoiceNumber());
                            factureDataDTO.setTotalTVA(piece.getFactureData().getTotalTVA());
                            factureDataDTO.setTaxRate(piece.getFactureData().getTaxRate());
                            factureDataDTO.setInvoiceDate(piece.getFactureData().getInvoiceDate());

                            // Add currency information
                            factureDataDTO.setDevise(piece.getFactureData().getDevise());
                            factureDataDTO.setOriginalCurrency(piece.getFactureData().getOriginalCurrency());
                            factureDataDTO.setConvertedCurrency(piece.getFactureData().getConvertedCurrency());
                            factureDataDTO.setExchangeRate(piece.getFactureData().getExchangeRate());

                            dto.setFactureData(factureDataDTO);
                        }

                        // Add Ecritures if exists - UPDATED WITH LINES MAPPING
                        if (piece.getEcritures() != null && !piece.getEcritures().isEmpty()) {
                            List<EcrituresDTO2> ecrituresDTOs = piece.getEcritures().stream()
                                    .map(ecriture -> {
                                        EcrituresDTO2 dto2 = new EcrituresDTO2();
                                        dto2.setUniqueEntryNumber(ecriture.getUniqueEntryNumber());
                                        dto2.setEntryDate(ecriture.getEntryDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

                                        // Add journal information
                                        if (ecriture.getJournal() != null) {
                                            JournalDTO journalDTO = new JournalDTO();
                                            journalDTO.setName(ecriture.getJournal().getName());
                                            journalDTO.setType(ecriture.getJournal().getType());
                                            dto2.setJournal(journalDTO);
                                        }

                                        // ← ADD THIS: Map lines with account information
                                        if (ecriture.getLines() != null && !ecriture.getLines().isEmpty()) {
                                            List<LineDTO> linesDTOs = ecriture.getLines().stream()
                                                    .map(line -> {
                                                        LineDTO lineDTO = new LineDTO();
                                                        lineDTO.setId(line.getId());
                                                        lineDTO.setLabel(line.getLabel());
                                                        lineDTO.setDebit(line.getDebit());
                                                        lineDTO.setCredit(line.getCredit());

                                                        // Add account information
                                                        if (line.getAccount() != null) {
                                                            AccountDTO accountDTO = new AccountDTO();
                                                            accountDTO.setId(line.getAccount().getId());
                                                            accountDTO.setAccount(line.getAccount().getAccount());
                                                            accountDTO.setLabel(line.getAccount().getLabel());
                                                            // Add type if Account entity has getType() method
                                                            // accountDTO.setType(line.getAccount().getType());
                                                            lineDTO.setAccount(accountDTO);
                                                        }

                                                        // Add currency information if available
                                                        lineDTO.setOriginalDebit(line.getOriginalDebit());
                                                        lineDTO.setOriginalCredit(line.getOriginalCredit());
                                                        lineDTO.setOriginalCurrency(line.getOriginalCurrency());
                                                        lineDTO.setConvertedDebit(line.getConvertedDebit());
                                                        lineDTO.setConvertedCredit(line.getConvertedCredit());
                                                        lineDTO.setConvertedCurrency(line.getConvertedCurrency());
                                                        lineDTO.setExchangeRate(line.getExchangeRate());
                                                        lineDTO.setExchangeRateDate(line.getExchangeRateDate());
                                                        lineDTO.setUsdDebit(line.getUsdDebit());
                                                        lineDTO.setUsdCredit(line.getUsdCredit());

                                                        return lineDTO;
                                                    })
                                                    .collect(Collectors.toList());
                                            dto2.setLines(linesDTOs);
                                        }

                                        return dto2;
                                    })
                                    .collect(Collectors.toList());
                            dto.setEcritures(ecrituresDTOs);
                        }

                        return dto;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(pieceDTOs);
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


    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updatePieceStatus(
            @PathVariable Long id,
            @RequestBody UpdatePieceStatusRequest requestBody) {

        log.info("Updating status for piece with id {} to {}", id, requestBody.getStatus());

        Piece updatedPiece = pieceService.updatePieceStatus(id, requestBody.getStatus());

        if (updatedPiece != null) {
            return ResponseEntity.ok(updatedPiece);
        } else {
            return ResponseEntity.badRequest().body("Piece not found or update failed");
        }
    }


    @GetMapping("/stats/dossier/{dossierId}")
    public ResponseEntity<PieceStatsDTO> getPieceStatsByDossier(@PathVariable Long dossierId) {
        PieceStatsDTO stats = pieceService.getPieceStatsByDossier(dossierId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/cabinet/{cabinetId}")
    public ResponseEntity<List<PieceStatsDTO>> getPieceStatsByCabinet(@PathVariable Long cabinetId) {
        List<PieceStatsDTO> stats = pieceService.getPieceStatsByCabinet(cabinetId);
        return ResponseEntity.ok(stats);
    }
}
