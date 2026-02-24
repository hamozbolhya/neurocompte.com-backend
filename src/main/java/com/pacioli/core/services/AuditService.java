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
    private void logAction(User user, String action, String entityType, Object entityId, String entityName, Object oldValue, Object newValue, String status, String errorMessage) {
        try {
            AuditLog auditLog = new AuditLog();

            // IMPORTANT: Gérer le cas où l'utilisateur est null
            if (user != null) {
                auditLog.setUserId(user.getId());
                auditLog.setUsername(user.getUsername());

                if (user.getCabinet() != null) {
                    auditLog.setCabinetId(user.getCabinet().getId());
                    auditLog.setCabinetName(user.getCabinet().getName());
                }
            } else {
                // Utilisateur anonyme ou non authentifié
                // Utiliser un ID par défaut ou rendre le champ nullable
                // Option 1: Utiliser un UUID spécial pour les utilisateurs anonymes
                auditLog.setUserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
                auditLog.setUsername("anonymous");
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

            // Calculer les changements si les deux valeurs sont présentes
            if (oldValue != null && newValue != null) {
                auditLog.setChanges(computeChanges(oldValue, newValue));
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
    public void logSuccess(User user, String action, String entityType, Object entityId, String entityName, Object oldValue, Object newValue) {
        logAction(user, action, entityType, entityId, entityName, oldValue, newValue, "SUCCESS", null);
    }

    /**
     * Méthode simplifiée pour les actions en échec
     */
    public void logFailure(User user, String action, String entityType, Object entityId, String entityName, String errorMessage) {
        logAction(user, action, entityType, entityId, entityName, null, null, "FAILURE", errorMessage);
    }

    /**
     * Méthode pour les actions de consultation
     */
    public void logView(User user, String entityType, Object entityId, String entityName) {
        logAction(user, "VIEW", entityType, entityId, entityName, null, null, "SUCCESS", null);
    }

    /**
     * Calcule les différences entre deux objets
     */
    private String computeChanges(Object oldValue, Object newValue) {
        // Protection contre les valeurs null
        if (oldValue == null || newValue == null) {
            log.debug("Cannot compute changes: oldValue or newValue is null");
            return null;
        }

        try {
            // Convertir en Map de façon sécurisée
            Map<String, Object> oldMap = convertToMapSafely(oldValue);
            Map<String, Object> newMap = convertToMapSafely(newValue);

            if (oldMap == null || newMap == null) {
                return null;
            }

            Map<String, Object> changes = new HashMap<>();

            for (Map.Entry<String, Object> entry : newMap.entrySet()) {
                String key = entry.getKey();

                // Ignorer certains champs sensibles ou non pertinents
                if (shouldIgnoreField(key)) {
                    continue;
                }

                Object newVal = entry.getValue();
                Object oldVal = oldMap.get(key);

                // Comparaison avec gestion des nulls
                if (hasChanged(oldVal, newVal)) {
                    Map<String, Object> changeDetail = new HashMap<>();
                    changeDetail.put("old", oldVal != null ? oldVal.toString() : "null");
                    changeDetail.put("new", newVal != null ? newVal.toString() : "null");
                    changes.put(key, changeDetail);
                }
            }

            return changes.isEmpty() ? null : objectMapper.writeValueAsString(changes);

        } catch (Exception e) {
            log.error("Erreur lors du calcul des changements: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Convertit un objet en Map de façon sécurisée
     */
    private Map<String, Object> convertToMapSafely(Object obj) {
        try {
            if (obj == null) {
                return null;
            }
            return objectMapper.convertValue(obj, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("Could not convert object to Map: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Vérifie si un champ doit être ignoré dans l'audit
     */
    private boolean shouldIgnoreField(String fieldName) {
        if (fieldName == null) return true;

        String lowerField = fieldName.toLowerCase();
        return lowerField.contains("password") || lowerField.contains("pwd") || lowerField.contains("secret") || lowerField.contains("token") || lowerField.contains("creditcard") || lowerField.contains("cvv") || lowerField.contains("pin") || lowerField.contains("authorities") || lowerField.contains("credentials");
    }

    /**
     * Vérifie si une valeur a changé (avec gestion des nulls)
     */
    private boolean hasChanged(Object oldVal, Object newVal) {
        if (oldVal == null && newVal == null) {
            return false; // Les deux sont null, pas de changement
        }
        if (oldVal == null || newVal == null) {
            return true; // L'un est null, l'autre non
        }
        return !oldVal.equals(newVal); // Comparaison des valeurs non-null
    }

    /**
     * Récupère la requête HTTP courante
     */
    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * Récupère l'adresse IP du client
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}