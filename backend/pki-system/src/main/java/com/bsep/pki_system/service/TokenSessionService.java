package com.bsep.pki_system.service;

import com.bsep.pki_system.model.TokenSession;
import com.bsep.pki_system.repository.TokenSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TokenSessionService {

    private final TokenSessionRepository tokenSessionRepository;

    public TokenSessionService(TokenSessionRepository tokenSessionRepository) {
        this.tokenSessionRepository = tokenSessionRepository;
    }

    public TokenSession createSession(Long userId, String sessionId, HttpServletRequest request, LocalDateTime expiresAt) {
        TokenSession session = new TokenSession();
        session.setUserId(userId);
        session.setSessionId(sessionId);
        session.setIpAddress(getClientIp(request));
        session.setUserAgent(getUserAgent(request));
        session.setBrowser(parseBrowser(getUserAgent(request)));
        session.setDeviceType(parseDeviceType(getUserAgent(request)));
        session.setCreatedAt(LocalDateTime.now());
        session.setLastActivity(LocalDateTime.now());
        session.setExpiresAt(expiresAt);
        session.setRevoked(false);

        return tokenSessionRepository.save(session);
    }

    @Transactional
    public void updateLastActivity(String sessionId) {
        Optional<TokenSession> sessionOpt = tokenSessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            TokenSession session = sessionOpt.get();
            session.setLastActivity(LocalDateTime.now());
            tokenSessionRepository.save(session);
        }
    }

    @Transactional
    public boolean revokeSession(String sessionId, Long userId) {
        Optional<TokenSession> sessionOpt = tokenSessionRepository.findBySessionId(sessionId);
        
        if (sessionOpt.isPresent() && sessionOpt.get().getUserId().equals(userId)) {
            TokenSession session = sessionOpt.get();
            session.setRevoked(true);
            session.setRevokedAt(LocalDateTime.now());
            tokenSessionRepository.save(session);
            return true;
        }
        return false;
    }

    public boolean isSessionRevoked(String sessionId) {
        Optional<TokenSession> sessionOpt = tokenSessionRepository.findBySessionId(sessionId);
        return sessionOpt.map(TokenSession::isRevoked).orElse(false);
    }

    public List<TokenSession> getActiveSessions(Long userId) {
        return tokenSessionRepository.findActiveSessionsByUserId(userId, LocalDateTime.now());
    }

    public List<TokenSession> getAllSessions(Long userId) {
        return tokenSessionRepository.findByUserIdOrderByLastActivityDesc(userId);
    }

    @Transactional
    public void cleanupExpiredSessions() {
        tokenSessionRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    public String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "Unknown";
    }

    private String parseBrowser(String userAgent) {
        if (userAgent == null) return "Unknown";

        if (userAgent.contains("Chrome") && !userAgent.contains("Edg")) {
            return "Chrome";
        } else if (userAgent.contains("Firefox")) {
            return "Firefox";
        } else if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) {
            return "Safari";
        } else if (userAgent.contains("Edg")) {
            return "Edge";
        } else if (userAgent.contains("Opera") || userAgent.contains("OPR")) {
            return "Opera";
        }
        return "Unknown";
    }

    private String parseDeviceType(String userAgent) {
        if (userAgent == null) return "Unknown";

        Pattern mobilePattern = Pattern.compile("Mobile|Android|iPhone|iPad");
        Matcher mobileMatcher = mobilePattern.matcher(userAgent);
        
        if (mobileMatcher.find()) {
            if (userAgent.contains("iPad")) {
                return "Tablet";
            }
            return "Mobile";
        }

        Pattern tabletPattern = Pattern.compile("Tablet|iPad");
        Matcher tabletMatcher = tabletPattern.matcher(userAgent);
        if (tabletMatcher.find()) {
            return "Tablet";
        }

        return "Desktop";
    }
}

