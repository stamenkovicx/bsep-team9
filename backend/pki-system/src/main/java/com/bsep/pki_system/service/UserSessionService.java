package com.bsep.pki_system.service;

import com.bsep.pki_system.model.User;
import com.bsep.pki_system.model.UserSession;
import com.bsep.pki_system.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserSessionService {

    @Autowired
    private UserSessionRepository userSessionRepository;

    public UserSession createSession(User user, String sessionId, HttpServletRequest request) {
        UserSession session = new UserSession();
        session.setUser(user);
        session.setSessionId(sessionId);
        session.setIpAddress(getClientIpAddress(request));
        session.setUserAgent(request.getHeader("User-Agent"));
        session.setDeviceType(detectDeviceType(request.getHeader("User-Agent")));
        session.setBrowserName(detectBrowserName(request.getHeader("User-Agent")));
        session.setLastActivity(LocalDateTime.now());
        session.setCreatedAt(LocalDateTime.now());
        session.setIsActive(true);
        session.setExpiresAt(LocalDateTime.now().plusHours(24)); // 24 hours expiration
        
        return userSessionRepository.save(session);
    }

    public List<UserSession> getActiveSessionsForUser(User user) {
        return userSessionRepository.findByUserAndIsActiveTrue(user);
    }

    public Optional<UserSession> getSessionBySessionId(String sessionId) {
        return userSessionRepository.findBySessionId(sessionId);
    }

    @Transactional
    public void revokeSession(String sessionId) {
        userSessionRepository.deactivateSessionBySessionId(sessionId);
    }

    @Transactional
    public void revokeAllSessionsForUser(User user) {
        userSessionRepository.deactivateAllSessionsForUser(user);
    }

    @Transactional
    public void revokeAllOtherSessionsForUser(User user, String currentSessionId) {
        userSessionRepository.deactivateAllOtherSessionsForUser(user, currentSessionId);
    }

    public void updateLastActivity(String sessionId) {
        userSessionRepository.updateLastActivity(sessionId, LocalDateTime.now());
    }

    public boolean isSessionActive(String sessionId) {
        Optional<UserSession> sessionOpt = userSessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            return false;
        }
        
        UserSession session = sessionOpt.get();
        return session.getIsActive() && !session.isExpired();
    }

    // Clean up expired sessions every hour
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<UserSession> expiredSessions = userSessionRepository.findExpiredActiveSessions(now);
        
        for (UserSession session : expiredSessions) {
            session.markInactive();
            userSessionRepository.save(session);
        }
        
        // Delete old inactive sessions (older than 7 days)
        userSessionRepository.deleteExpiredSessions(now.minusDays(7));
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    private String detectDeviceType(String userAgent) {
        if (userAgent == null) return "Unknown";
        
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "Mobile";
        } else if (ua.contains("tablet") || ua.contains("ipad")) {
            return "Tablet";
        } else {
            return "Desktop";
        }
    }

    private String detectBrowserName(String userAgent) {
        if (userAgent == null) return "Unknown";
        
        String ua = userAgent.toLowerCase();
        if (ua.contains("chrome") && !ua.contains("edg")) {
            return "Chrome";
        } else if (ua.contains("firefox")) {
            return "Firefox";
        } else if (ua.contains("safari") && !ua.contains("chrome")) {
            return "Safari";
        } else if (ua.contains("edg")) {
            return "Edge";
        } else if (ua.contains("opera")) {
            return "Opera";
        } else {
            return "Other";
        }
    }
}
