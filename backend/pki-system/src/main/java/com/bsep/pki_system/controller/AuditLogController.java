package com.bsep.pki_system.controller;

import com.bsep.pki_system.audit.AuditLog;
import com.bsep.pki_system.audit.AuditLogService;
import com.bsep.pki_system.jwt.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        Sort sort = sortDirection.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<AuditLog> auditLogs = auditLogService.getAuditLogsWithFilters(
                userId, eventType, success, startDate, endDate, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("logs", auditLogs.getContent());
        response.put("currentPage", auditLogs.getNumber());
        response.put("totalItems", auditLogs.getTotalElements());
        response.put("totalPages", auditLogs.getTotalPages());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-logs")
    public ResponseEntity<Map<String, Object>> getMyAuditLogs(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

        Page<AuditLog> auditLogs = auditLogService.getAuditLogsWithFilters(
                userPrincipal.getId(), null, null, null, null, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("logs", auditLogs.getContent());
        response.put("currentPage", auditLogs.getNumber());
        response.put("totalItems", auditLogs.getTotalElements());
        response.put("totalPages", auditLogs.getTotalPages());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/event-types")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> getEventTypes() {
        Map<String, String> eventTypes = new HashMap<>();
        eventTypes.put("LOGIN", "User login");
        eventTypes.put("LOGOUT", "User logout");
        eventTypes.put("LOGIN_FAILED", "Failed login attempt");
        eventTypes.put("REGISTRATION", "User registration");
        eventTypes.put("CERTIFICATE_ISSUED", "Certificate issued");
        eventTypes.put("CERTIFICATE_REVOKED", "Certificate revoked");
        eventTypes.put("CERTIFICATE_VIEWED", "Certificate viewed");
        eventTypes.put("PASSWORD_CHANGE", "Password changed");
        eventTypes.put("2FA_ENABLED", "2FA enabled");
        eventTypes.put("2FA_DISABLED", "2FA disabled");
        eventTypes.put("USER_CREATED", "User created by admin");
        eventTypes.put("ACCESS_DENIED", "Access denied");

        return ResponseEntity.ok(eventTypes);
    }
}