package com.pacioli.core.DTO.AI;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class BankStatementRequest {
    private Long dossierId;
    private MultipartFile file;
    private String fileId; // UUID

    public BankStatementRequest(Long dossierId, MultipartFile file) {
        this.dossierId = dossierId;
        this.file = file;
    }

    public BankStatementRequest(Long dossierId, MultipartFile file, String fileId) {
        this.dossierId = dossierId;
        this.file = file;
        this.fileId = fileId;
    }
}