package com.bsep.pki_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionDTO {
    
    private Long id;
    private String sessionId;
    private String ipAddress;
    private String deviceType;
    private String browserName;
    private LocalDateTime lastActivity;
    private LocalDateTime createdAt;
    private Boolean isActive;
    private LocalDateTime expiresAt;
    private Boolean isCurrentSession; // Indicates if this is the current user's session
}
