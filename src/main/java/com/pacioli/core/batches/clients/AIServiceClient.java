package com.pacioli.core.batches.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@Slf4j
public class AIServiceClient {

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    @Value("${ai.service.api-key}")
    private String apiKey;

    @Value("${file.upload.dir:Files/}")
    private String uploadDir;

    @Autowired private RestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;

    public JsonNode callAIService(String filename) throws IOException {
        Path filePath = Paths.get(uploadDir, filename);
        validateFileExists(filePath);

        String jsonFilename = convertToJsonFilename(filename);
        String apiUrl = aiServiceUrl + jsonFilename;

        log.info("🔗 Calling AI service: {}", apiUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);

        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("AI service failed with status: " + response.getStatusCode());
        }

        log.info("✅ AI service call successful");
        return objectMapper.readTree(response.getBody());
    }

    private void validateFileExists(Path filePath) throws FileNotFoundException {
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
    }

    private String convertToJsonFilename(String filename) {
        return filename.substring(0, filename.lastIndexOf(".")) + ".json";
    }
}