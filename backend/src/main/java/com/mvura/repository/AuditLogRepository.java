package com.mvura.repository;

import com.mvura.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // ===== EXISTING METHODS =====

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<AuditLog> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT a FROM AuditLog a WHERE a.username = :username AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<AuditLog> findByUsernameAndDateRange(@Param("username") String username,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    // ===== PAGEABLE METHODS FOR CONTROLLER =====

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    Page<AuditLog> findByDateRange(@Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end,
                                   Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.username = :username AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    Page<AuditLog> findByUsernameAndDateRange(@Param("username") String username,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.action = :action AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    Page<AuditLog> findByActionAndDateRange(@Param("action") String action,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.resourceType = :resourceType AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    Page<AuditLog> findByResourceTypeAndDateRange(@Param("resourceType") String resourceType,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end,
                                                  Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.resourceType = :resourceType AND a.resourceId = :resourceId AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    Page<AuditLog> findByResourceTypeAndResourceIdAndDateRange(@Param("resourceType") String resourceType,
                                                               @Param("resourceId") String resourceId,
                                                               @Param("start") LocalDateTime start,
                                                               @Param("end") LocalDateTime end,
                                                               Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    Page<AuditLog> findByUserIdAndDateRange(@Param("userId") UUID userId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.resourceType = 'SECURITY' AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    Page<AuditLog> findSecurityEvents(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.resourceType = 'SECURITY' AND a.username = :username AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    Page<AuditLog> findSecurityEventsByUser(@Param("since") LocalDateTime since,
                                            @Param("username") String username,
                                            Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.resourceType = 'SECURITY' AND a.action = :eventType AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    Page<AuditLog> findSecurityEventsByType(@Param("since") LocalDateTime since,
                                            @Param("eventType") String eventType,
                                            Pageable pageable);

    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> findDistinctActions();

    @Query("SELECT DISTINCT a.resourceType FROM AuditLog a WHERE a.resourceType IS NOT NULL ORDER BY a.resourceType")
    List<String> findDistinctResourceTypes();

    @Query("SELECT a FROM AuditLog a ORDER BY a.createdAt ASC")
    List<AuditLog> findAllByOrderByCreatedAtAsc();

    @Query("SELECT a FROM AuditLog a ORDER BY a.createdAt DESC LIMIT :limit")
    List<AuditLog> findRecentLogs(Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.resourceType = :resourceType ORDER BY a.createdAt DESC LIMIT :limit")
    List<AuditLog> findRecentByResourceType(@Param("resourceType") String resourceType, Pageable pageable);

    // ===== METHODS FOR AUDIT SERVICE getAuditLogs =====

    @Query("SELECT a FROM AuditLog a WHERE a.action = :action AND a.resourceType = :entityType AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<AuditLog> findByActionAndEntityTypeAndCreatedAtBetween(@Param("action") String action,
                                                                @Param("entityType") String entityType,
                                                                @Param("start") LocalDateTime start,
                                                                @Param("end") LocalDateTime end);

    @Query("SELECT a FROM AuditLog a WHERE a.action = :action AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<AuditLog> findByActionAndCreatedAtBetween(@Param("action") String action,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    @Query("SELECT a FROM AuditLog a WHERE a.resourceType = :entityType AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<AuditLog> findByEntityTypeAndCreatedAtBetween(@Param("entityType") String entityType,
                                                       @Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end);

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<AuditLog> findByCreatedAtBetween(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);
}