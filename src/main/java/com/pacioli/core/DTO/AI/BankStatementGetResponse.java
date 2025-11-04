package com.pacioli.core.DTO.AI;

import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
public class BankStatementGetResponse {
    private boolean success;
    private String message;
    private String fileId;
    private String jsonResponse; // The actual JSON content from the API
    private HttpStatus httpStatus;
    private String rawResponse;

    public static BankStatementGetResponse success(String fileId, String jsonResponse, String rawResponse) {
        BankStatementGetResponse response = new BankStatementGetResponse();
        response.setSuccess(true);
        response.setMessage("Bank statement result retrieved successfully");
        response.setFileId(fileId);
        response.setJsonResponse(jsonResponse);
        response.setRawResponse(rawResponse);
        response.setHttpStatus(HttpStatus.OK);
        return response;
    }

    public static BankStatementGetResponse error(String message, HttpStatus httpStatus, String rawResponse) {
        BankStatementGetResponse response = new BankStatementGetResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setHttpStatus(httpStatus);
        response.setRawResponse(rawResponse);
        return response;
    }
}