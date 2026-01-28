package com.pacioli.core.DTO;

import lombok.Data;

import java.util.Map;

@Data
public class AIResponse {
    private String outputText;
    private String stopReason;
    private Map<String, Object> tokenUsage;
}
