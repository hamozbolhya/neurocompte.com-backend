package com.pacioli.core.services;

import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for managing historical file uploads to the AI service
 */
public interface HistoireService {

    /**
     * Upload a historical file to the AI service
     *
     * @param fileName The name with which the file should be stored
     * @param file The file to upload
     * @return Success message if upload is successful
     * @throws IllegalArgumentException if file validation fails
     * @throws RuntimeException if upload fails
     */
    String uploadHistoriqueFile(String fileName, MultipartFile file);

    /**
     * Validate if the uploaded file meets the requirements
     *
     * @param file The file to validate
     * @throws IllegalArgumentException if validation fails
     */
    void validateFile(MultipartFile file);
}
