package com.pacioli.core.services.serviceImp.pieces;

import org.springframework.web.multipart.MultipartFile;

public class FileProcessingResult {
    private final String filename;
    private final MultipartFile fileToProcess;

    public FileProcessingResult(String filename, MultipartFile fileToProcess) {
        this.filename = filename;
        this.fileToProcess = fileToProcess;
    }

    public String getFilename() {
        return filename;
    }

    public MultipartFile getFileToProcess() {
        return fileToProcess;
    }
}
