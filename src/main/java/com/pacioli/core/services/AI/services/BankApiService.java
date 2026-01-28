package com.pacioli.core.services.AI.services;

import com.pacioli.core.DTO.AI.BankStatementGetResponse;
import com.pacioli.core.DTO.AI.BankStatementRequest;
import com.pacioli.core.DTO.AI.BankStatementResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BankApiService {
    BankStatementResponse uploadBankStatement(BankStatementRequest request);
    boolean isValidFileExtension(MultipartFile file);
    String generateFileUrl(Long dossierId, String fileId, String fileExtension);
    List<String> getAllowedExtensions();
    boolean isValidFileSize(MultipartFile file);
    BankStatementGetResponse getBankStatementResult(String fileId);
}
