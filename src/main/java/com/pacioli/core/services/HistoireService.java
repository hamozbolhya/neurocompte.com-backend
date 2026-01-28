package com.pacioli.core.services;

import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for managing historical file uploads to the AI service
 */
public interface HistoireService {

    /**
     * Upload a historical file to the AI service
     *
     * @param dossierId The dossier ID
     * @param file The file to upload
     * @param fileType The type of file ("balance" or "fec")
     * @return Success message if upload is successful
     * @throws IllegalArgumentException if file validation fails
     * @throws RuntimeException if upload fails
     */
    String uploadHistoriqueFile(String dossierId, MultipartFile file, String fileType);
    /**
     * Validate if the uploaded file meets the requirements
     *
     * @param file The file to validate
     * @throws IllegalArgumentException if validation fails
     */
    void validateFile(MultipartFile file);
}
