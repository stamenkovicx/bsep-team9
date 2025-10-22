package com.bsep.pki_system.repository;

import com.bsep.pki_system.model.User;
import com.bsep.pki_system.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findBySessionId(String sessionId);

    List<UserSession> findByUserAndIsActiveTrue(User user);

    List<UserSession> findByUser(User user);

    @Modifying
    @Transactional
    @Query("UPDATE UserSession s SET s.isActive = false WHERE s.sessionId = :sessionId")
    void deactivateSessionBySessionId(@Param("sessionId") String sessionId);

    @Modifying
    @Transactional
    @Query("UPDATE UserSession s SET s.isActive = false WHERE s.user = :user")
    void deactivateAllSessionsForUser(@Param("user") User user);

    @Modifying
    @Transactional
    @Query("UPDATE UserSession s SET s.isActive = false WHERE s.user = :user AND s.sessionId != :currentSessionId")
    void deactivateAllOtherSessionsForUser(@Param("user") User user, @Param("currentSessionId") String currentSessionId);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
    void deleteExpiredSessions(@Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE UserSession s SET s.lastActivity = :lastActivity WHERE s.sessionId = :sessionId")
    void updateLastActivity(@Param("sessionId") String sessionId, @Param("lastActivity") LocalDateTime lastActivity);

    @Query("SELECT s FROM UserSession s WHERE s.isActive = true AND s.expiresAt < :now")
    List<UserSession> findExpiredActiveSessions(@Param("now") LocalDateTime now);
}
