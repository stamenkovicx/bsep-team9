package com.bsep.pki_system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "token_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sessionId; // JWT jti claim

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String ipAddress;

    private String userAgent;

    private String deviceType; // e.g., "Desktop", "Mobile", "Tablet"

    private String browser; // e.g., "Chrome", "Firefox", "Safari"

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastActivity;

    private boolean revoked = false;

    private LocalDateTime revokedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}

