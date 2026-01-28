package com.pacioli.core.DTO.AI;

import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
public class BankStatementResponse {
    private boolean success;
    private String message;
    private String fileUrl;
    private String fileId;
    private Long dossierId;
    private HttpStatus httpStatus;
    private String externalApiResponse;
    private long fileSize;
    private String fileName;

    public static BankStatementResponse success(String fileUrl, String fileId, Long dossierId,
                                                String fileName, long fileSize, String externalResponse) {
        BankStatementResponse response = new BankStatementResponse();
        response.setSuccess(true);
        response.setMessage("Bank statement uploaded successfully");
        response.setFileUrl(fileUrl);
        response.setFileId(fileId);
        response.setDossierId(dossierId);
        response.setHttpStatus(HttpStatus.OK);
        response.setExternalApiResponse(externalResponse);
        response.setFileSize(fileSize);
        response.setFileName(fileName);
        return response;
    }

    public static BankStatementResponse error(String message, HttpStatus httpStatus, String externalResponse) {
        BankStatementResponse response = new BankStatementResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setHttpStatus(httpStatus);
        response.setExternalApiResponse(externalResponse);
        return response;
    }
}