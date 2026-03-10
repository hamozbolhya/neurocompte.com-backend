package com.pacioli.core.aspects;

import com.pacioli.core.annotations.Auditable;
import com.pacioli.core.models.User;
import com.pacioli.core.services.AuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;  // ← AJOUTER CET IMPORT
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class AuditAspect {

    @Autowired
    @Lazy  // ← AJOUTER CETTE ANNOTATION
    private AuditService auditService;

    @Around("@annotation(com.pacioli.core.annotations.Auditable)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Auditable auditable = method.getAnnotation(Auditable.class);

        User currentUser = getCurrentUser();
        String action = auditable.action();
        String entityType = auditable.entityType();

        Object result = null;
        Throwable error = null;

        long startTime = System.currentTimeMillis();

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            // Log l'action
            if (error != null) {
                auditService.logFailure(
                        currentUser,
                        action,
                        entityType,
                        extractEntityId(joinPoint.getArgs()),
                        extractEntityName(joinPoint.getArgs()),
                        error.getMessage()
                );
            } else {
                auditService.logSuccess(
                        currentUser,
                        action,
                        entityType,
                        extractEntityId(joinPoint.getArgs()),
                        extractEntityName(joinPoint.getArgs()),
                        null,
                        result
                );
            }
        }
    }

    private User getCurrentUser() {
        // Récupérer l'utilisateur courant depuis le contexte de sécurité
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User) {
            return (User) principal;
        }
        return null;
    }

    private Object extractEntityId(Object[] args) {
        // Logique pour extraire l'ID de l'entité des arguments
        if (args != null && args.length > 0) {
            // Implémentez selon votre logique métier
            return args[0];
        }
        return null;
    }

    private String extractEntityName(Object[] args) {
        // Logique pour extraire le nom de l'entité
        return null;
    }
}