package com.bsep.pki_system.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private String eventType; // LOGIN, LOGOUT, CERTIFICATE_ISSUED, CERTIFICATE_REVOKED, etc.

    @Column(nullable = false)
    private String description;

    private Long userId;

    private String userEmail;

    private String userRole;

    @Column(length = 1000)
    private String additionalData; // JSON string sa dodatnim podacima

    private String ipAddress;

    private String userAgent;

    private boolean success;

    @Column(length = 500)
    private String errorMessage;

    // Konstruktor za uspješne događaje
    public AuditLog(String eventType, String description, Long userId, String userEmail,
                    String userRole, String additionalData, String ipAddress, String userAgent) {
        this.timestamp = LocalDateTime.now();
        this.eventType = eventType;
        this.description = description;
        this.userId = userId;
        this.userEmail = userEmail;
        this.userRole = userRole;
        this.additionalData = additionalData;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.success = true;
    }

    // Konstruktor za neuspješne događaje
    public AuditLog(String eventType, String description, Long userId, String userEmail,
                    String userRole, String additionalData, String ipAddress, String userAgent,
                    String errorMessage) {
        this.timestamp = LocalDateTime.now();
        this.eventType = eventType;
        this.description = description;
        this.userId = userId;
        this.userEmail = userEmail;
        this.userRole = userRole;
        this.additionalData = additionalData;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.success = false;
        this.errorMessage = errorMessage;
    }
}