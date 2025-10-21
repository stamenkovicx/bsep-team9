package com.bsep.pki_system.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Pronalaženje logova po korisniku
    List<AuditLog> findByUserIdOrderByTimestampDesc(Long userId);

    // Pronalaženje logova po tipu događaja
    List<AuditLog> findByEventTypeOrderByTimestampDesc(String eventType);

    // Pronalaženje logova po vremenskom periodu
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);

    // Pronalaženje logova po uspešnosti
    List<AuditLog> findBySuccessOrderByTimestampDesc(boolean success);

    // Kombinovana pretraga sa paginacijom
    @Query("SELECT al FROM AuditLog al WHERE " +
            "(:userId IS NULL OR al.userId = :userId) AND " +
            "(:eventType IS NULL OR al.eventType = :eventType) AND " +
            "(:success IS NULL OR al.success = :success) AND " +
            "(al.timestamp BETWEEN :startDate AND :endDate) " +
            "ORDER BY al.timestamp DESC")
    Page<AuditLog> findWithFilters(
            @Param("userId") Long userId,
            @Param("eventType") String eventType,
            @Param("success") Boolean success,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // Brojanje logova po danu za statistiku
    @Query("SELECT DATE(al.timestamp) as date, COUNT(al) as count " +
            "FROM AuditLog al " +
            "WHERE al.timestamp BETWEEN :start AND :end " +
            "GROUP BY DATE(al.timestamp) " +
            "ORDER BY date DESC")
    List<Object[]> countLogsByDate(@Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);
}