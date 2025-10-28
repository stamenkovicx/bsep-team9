package com.bsep.pki_system.repository;

import com.bsep.pki_system.model.TokenSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TokenSessionRepository extends JpaRepository<TokenSession, Long> {

    Optional<TokenSession> findBySessionId(String sessionId);

    List<TokenSession> findByUserIdOrderByLastActivityDesc(Long userId);

    List<TokenSession> findByUserIdAndRevokedFalse(Long userId);

    @Query("SELECT ts FROM TokenSession ts WHERE ts.userId = :userId AND ts.revoked = false AND ts.expiresAt > :now ORDER BY ts.lastActivity DESC")
    List<TokenSession> findActiveSessionsByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    void deleteByExpiresAtBefore(LocalDateTime expiryDate);

    void deleteByUserId(Long userId);
}

