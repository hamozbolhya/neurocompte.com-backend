package com.pacioli.core.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pacioli.core.models.AuditLog;
import com.pacioli.core.models.User;
import com.pacioli.core.repositories.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class AuditService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    private final ObjectMapper objectMapper;

    public AuditService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Enregistre une action dans les logs d'audit
     */
    private void logAction(User user, String action, String entityType,
                           Object entityId, String entityName,
                           Object oldValue, Object newValue,
                           String status, String errorMessage) {
        try {
            AuditLog auditLog = new AuditLog();

            // Informations utilisateur
            if (user != null) {
                auditLog.setUserId(user.getId());
                auditLog.setUsername(user.getUsername());

                if (user.getCabinet() != null) {
                    auditLog.setCabinetId(user.getCabinet().getId());
                    auditLog.setCabinetName(user.getCabinet().getName());
                }
            } else {
                auditLog.setUsername(entityName != null ? entityName : "anonymous");
            }

            // Action et entité
            auditLog.setAction(action);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId != null ? entityId.toString() : null);
            auditLog.setEntityName(entityName);

            // Valeurs
            if (oldValue != null) {
                auditLog.setOldValue(objectMapper.writeValueAsString(oldValue));
            }
            if (newValue != null) {
                auditLog.setNewValue(objectMapper.writeValueAsString(newValue));
            }

            // Informations HTTP
            HttpServletRequest request = getCurrentHttpRequest();
            if (request != null) {
                auditLog.setIpAddress(getClientIp(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
                auditLog.setRequestUrl(request.getRequestURI());
                auditLog.setHttpMethod(request.getMethod());
            }

            // Statut
            auditLog.setStatus(status);
            auditLog.setErrorMessage(errorMessage);
            auditLog.setActionDate(LocalDateTime.now());

            auditLogRepository.save(auditLog);

        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement du log d'audit", e);
        }
    }

    /**
     * Méthode simplifiée pour les actions réussies
     */
    public void logSuccess(User user, String action, String entityType,
                           Object entityId, String entityName,
                           Object oldValue, Object newValue) {
        logAction(user, action, entityType, entityId, entityName,
                oldValue, newValue, "SUCCESS", null);
    }

    /**
     * Méthode simplifiée pour les actions en échec
     */
    public void logFailure(User user, String action, String entityType,
                           Object entityId, String entityName,
                           String errorMessage) {
        logAction(user, action, entityType, entityId, entityName,
                null, null, "FAILURE", errorMessage);
    }

    /**
     * Récupère la requête HTTP courante
     */
    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}