package com.bsep.pki_system.audit;

import com.bsep.pki_system.jwt.UserPrincipal;
import com.bsep.pki_system.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuditLogService {

    private static final Logger auditLogger = LoggerFactory.getLogger("com.bsep.pki_system.audit");
    private static final Logger securityLogger = LoggerFactory.getLogger("com.bsep.pki_system.security");

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Event types
    public static final String EVENT_LOGIN = "LOGIN";
    public static final String EVENT_LOGOUT = "LOGOUT";
    public static final String EVENT_LOGIN_FAILED = "LOGIN_FAILED";
    public static final String EVENT_REGISTRATION = "REGISTRATION";
    public static final String EVENT_CERTIFICATE_ISSUED = "CERTIFICATE_ISSUED";
    public static final String EVENT_CERTIFICATE_REVOKED = "CERTIFICATE_REVOKED";
    public static final String EVENT_CERTIFICATE_VIEWED = "CERTIFICATE_VIEWED";
    public static final String EVENT_PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String EVENT_2FA_ENABLED = "2FA_ENABLED";
    public static final String EVENT_2FA_DISABLED = "2FA_DISABLED";
    public static final String EVENT_USER_CREATED = "USER_CREATED";
    public static final String EVENT_ACCESS_DENIED = "ACCESS_DENIED";
    public static final String EVENT_TEMPLATE_CREATED = "TEMPLATE_CREATED";
    public static final String EVENT_TEMPLATE_UPDATED = "TEMPLATE_UPDATED";
    public static final String EVENT_TEMPLATE_DELETED = "TEMPLATE_DELETED";
    public static final String EVENT_TEMPLATE_USED = "TEMPLATE_USED";
    public static final String EVENT_TEMPLATE_ACCESSED = "TEMPLATE_ACCESSED";

    public void logSecurityEvent(String eventType, String description, boolean success,
                                 String additionalData, HttpServletRequest request) {
        UserPrincipal user = getCurrentUser();

        String logMessage = buildLogMessage(eventType, description, user, additionalData, success, null);

        if (success) {
            securityLogger.info(logMessage);
        } else {
            securityLogger.warn(logMessage);
        }

        // Čuvanje u bazi
        saveToDatabase(eventType, description, user, additionalData, request, success, null);
    }

    public void logSecurityEvent(String eventType, String description, boolean success,
                                 String additionalData, HttpServletRequest request, User user) {

        String logMessage = buildLogMessage(eventType, description, user != null ?
                        new UserPrincipal(user.getId(), user.getEmail(), user.getRole()) : null,
                additionalData, success, null);

        if (success) {
            securityLogger.info(logMessage);
        } else {
            securityLogger.warn(logMessage);
        }

        // Čuvanje u bazi - KORISTIMO User OBJEKAT
        saveToDatabaseWithUser(eventType, description, user, additionalData, request, success, null);
    }

    private void saveToDatabaseWithUser(String eventType, String description, User user,
                                        String additionalData, HttpServletRequest request,
                                        boolean success, String errorMessage) {
        try {
            AuditLog auditLog;

            if (user != null) {
                if (success) {
                    auditLog = new AuditLog(eventType, description, user.getId(), user.getEmail(),
                            user.getRole().name(), additionalData,
                            getClientIp(request), getUserAgent(request));
                } else {
                    auditLog = new AuditLog(eventType, description, user.getId(), user.getEmail(),
                            user.getRole().name(), additionalData,
                            getClientIp(request), getUserAgent(request), errorMessage);
                }
            } else {
                // Za anonimne događaje
                Map<String, Object> anonymousData = new HashMap<>();
                if (additionalData != null) {
                    anonymousData.put("attemptedData", additionalData);
                }

                String anonymousAdditionalData = objectMapper.writeValueAsString(anonymousData);

                if (success) {
                    auditLog = new AuditLog(eventType, description, null, "ANONYMOUS",
                            "ANONYMOUS", anonymousAdditionalData,
                            getClientIp(request), getUserAgent(request));
                } else {
                    auditLog = new AuditLog(eventType, description, null, "ANONYMOUS",
                            "ANONYMOUS", anonymousAdditionalData,
                            getClientIp(request), getUserAgent(request), errorMessage);
                }
            }

            auditLogRepository.save(auditLog);

        } catch (Exception e) {
            securityLogger.error("Failed to save audit log to database: {}", e.getMessage());
        }
    }

    private String buildLogMessage(String eventType, String description, UserPrincipal user,
                                   String additionalData, boolean success, String errorMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("Event: ").append(eventType);
        sb.append(" | User: ").append(user != null ? user.getEmail() : "ANONYMOUS");
        sb.append(" | Role: ").append(user != null ? user.getRole() : "NONE");
        sb.append(" | Description: ").append(description);
        sb.append(" | Success: ").append(success);

        if (additionalData != null) {
            sb.append(" | Data: ").append(additionalData);
        }

        if (errorMessage != null) {
            sb.append(" | Error: ").append(errorMessage);
        }

        return sb.toString();
    }

    private void saveToDatabase(String eventType, String description, UserPrincipal user,
                                String additionalData, HttpServletRequest request,
                                boolean success, String errorMessage) {
        try {
            AuditLog auditLog;

            if (user != null) {
                if (success) {
                    auditLog = new AuditLog(eventType, description, user.getId(), user.getEmail(),
                            user.getRole().name(), additionalData,
                            getClientIp(request), getUserAgent(request));
                } else {
                    auditLog = new AuditLog(eventType, description, user.getId(), user.getEmail(),
                            user.getRole().name(), additionalData,
                            getClientIp(request), getUserAgent(request), errorMessage);
                }
            } else {
                // Za anonimne događaje (npr. neuspješna prijava)
                Map<String, Object> anonymousData = new HashMap<>();
                if (additionalData != null) {
                    anonymousData.put("attemptedData", additionalData);
                }

                String anonymousAdditionalData = objectMapper.writeValueAsString(anonymousData);

                if (success) {
                    auditLog = new AuditLog(eventType, description, null, "ANONYMOUS",
                            "ANONYMOUS", anonymousAdditionalData,
                            getClientIp(request), getUserAgent(request));
                } else {
                    auditLog = new AuditLog(eventType, description, null, "ANONYMOUS",
                            "ANONYMOUS", anonymousAdditionalData,
                            getClientIp(request), getUserAgent(request), errorMessage);
                }
            }

            auditLogRepository.save(auditLog);

        } catch (Exception e) {
            securityLogger.error("Failed to save audit log to database: {}", e.getMessage());
        }
    }

    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null) {
            return xfHeader.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    private String getUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    // Metode za pretragu logova
    public Page<AuditLog> getAuditLogsWithFilters(Long userId, String eventType, Boolean success,
                                                  LocalDateTime startDate, LocalDateTime endDate,
                                                  Pageable pageable) {
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(30); // Podrazumijevano posljednjih 30 dana
        }

        return auditLogRepository.findWithFilters(userId, eventType, success, startDate, endDate, pageable);
    }
}