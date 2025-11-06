package com.pacioli.core.services.serviceImp.pieces;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpeg", "jpg");

    @Value("${file.upload.dir:Files/}")
    private String uploadDir;

    public String validateAndSaveFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("Aucun fichier fourni.");
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);

        if (!isValidFileType(fileExtension)) {
            throw new IOException("Type de fichier non accepté. Seuls les fichiers PDF et JPEG sont autorisés.");
        }

        log.info("✅ File type validation OK: {}", fileExtension);

        String uuid = UUID.randomUUID().toString();
        String formattedFilename = uuid + "." + fileExtension;

        saveFileToDisk(file, formattedFilename);
        return formattedFilename;
    }

    private void saveFileToDisk(MultipartFile file, String formattedFilename) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path filePath = uploadPath.resolve(formattedFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean isValidFileType(String fileExtension) {
        return fileExtension != null && ALLOWED_EXTENSIONS.contains(fileExtension.toLowerCase());
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return null;
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
