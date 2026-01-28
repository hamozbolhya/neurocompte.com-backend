package com.pacioli.core.services.serviceImp.pieces;

import com.pacioli.core.DTO.AI.BankStatementRequest;
import com.pacioli.core.DTO.AI.BankStatementResponse;
import com.pacioli.core.services.AI.services.BankApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class AIService {

    private final BankApiService bankApiService;

    @Value("${ai.service.url}")
    private String aiApiBaseUrl;

    @Value("${ai.service.api-key}")
    private String aiApiKey;

    public AIService(BankApiService bankApiService) {
        this.bankApiService = bankApiService;
    }

    public void processFileBasedOnType(MultipartFile file, String filename, Long dossierId,
                                       String country, String pieceType) {
        boolean isBankStatement = "Relevés bancaires".equalsIgnoreCase(pieceType);

        if (isBankStatement) {
            log.info("🏦 Sending to BANK AI Service");
            // ✅ FIX: Extract UUID without extension for bank statements
            String uuid = filename.contains(".") ?
                    filename.substring(0, filename.lastIndexOf('.')) : filename;
            processBankStatement(file, dossierId, uuid);
        } else {
            log.info("🤖 Sending to Normal AI Service");
            sendFileToAI(file, filename, dossierId, country);
        }
    }

    private void processBankStatement(MultipartFile file, Long dossierId, String uuid) {
        try {
            BankStatementRequest bankRequest = new BankStatementRequest(dossierId, file, uuid);
            BankStatementResponse bankResponse = bankApiService.uploadBankStatement(bankRequest);

            if (!bankResponse.isSuccess()) {
                log.warn("⚠️ Bank AI service returned error: {}", bankResponse.getMessage());
            }
        } catch (Exception e) {
            log.error("Error processing bank statement: {}", e.getMessage(), e);
        }
    }

    private void sendFileToAI(MultipartFile file, String filename, Long dossierID, String country) {
        try {
            log.info("============ START AI FILE UPLOAD TRACE ============");

            String uuid = filename.contains(".") ?
                    filename.substring(0, filename.lastIndexOf('.')) : filename;

            String baseUrl = aiApiBaseUrl.endsWith("/") ?
                    aiApiBaseUrl.substring(0, aiApiBaseUrl.length() - 1) : aiApiBaseUrl;

            String finalUrl = baseUrl + "/" + dossierID + "%2F" + filename;
            log.info("Complete API URL: {}", finalUrl);

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", file.getContentType());
            headers.put("x-api-key", aiApiKey);

            byte[] fileBytes = file.getBytes();

            URL url = new URL(finalUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);

            headers.forEach(connection::setRequestProperty);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(fileBytes);
            }

            int responseCode = connection.getResponseCode();
            StringBuilder response = new StringBuilder();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            if (responseCode == 200) {
                log.info("File successfully sent to AI: {}", filename);
            } else {
                log.warn("AI service responded with non-OK status: {} - {}", responseCode, response);
            }

            log.info("============ END AI FILE UPLOAD TRACE ============");

        } catch (Exception e) {
            log.error("Error in sendFileToAI: {}", e.getMessage(), e);
        }
    }
}
