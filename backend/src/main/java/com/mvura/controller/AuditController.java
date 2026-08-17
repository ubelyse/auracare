package com.mvura.controller;

import com.mvura.model.AuditLog;
import com.mvura.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('DISTRICT_ADMIN', 'FACILITY_ADMIN')")
public class AuditController {

    private final AuditService auditService;

    // ==================== 1. GET AUDIT LOGS (WITH FILTERS) ====================

    @GetMapping("/logs")
    public ResponseEntity<?> getAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        // Set default date range
        start = start != null ? start : LocalDateTime.now().minusDays(7);
        end = end != null ? end : LocalDateTime.now();

        // Validate date range
        if (start.isAfter(end)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Start date must be before end date"
            ));
        }

        // Validate date range is not too large (max 90 days)
        if (start.plusDays(90).isBefore(end)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Date range cannot exceed 90 days"
            ));
        }

        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        try {
            // Get filtered logs
            Page<AuditLog> logs = auditService.getAuditLogs(
                    username, action, resourceType, resourceId, start, end, pageable
            );

            return ResponseEntity.ok(Map.of(
                    "logs", logs.getContent(),
                    "totalElements", logs.getTotalElements(),
                    "totalPages", logs.getTotalPages(),
                    "currentPage", logs.getNumber(),
                    "pageSize", logs.getSize(),
                    "filters", Map.of(
                            "username", username,
                            "action", action,
                            "resourceType", resourceType,
                            "resourceId", resourceId,
                            "start", start,
                            "end", end
                    )
            ));
        } catch (Exception e) {
            log.error("Error fetching audit logs: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to fetch audit logs: " + e.getMessage()
            ));
        }
    }

    // ==================== 2. GET SECURITY EVENTS ====================

    @GetMapping("/security")
    public ResponseEntity<?> getSecurityEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        since = since != null ? since : LocalDateTime.now().minusDays(1);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        try {
            Page<AuditLog> securityEvents = auditService.getSecurityEvents(
                    since, username, eventType, pageable
            );

            return ResponseEntity.ok(Map.of(
                    "events", securityEvents.getContent(),
                    "totalElements", securityEvents.getTotalElements(),
                    "totalPages", securityEvents.getTotalPages(),
                    "currentPage", securityEvents.getNumber(),
                    "pageSize", securityEvents.getSize(),
                    "since", since
            ));
        } catch (Exception e) {
            log.error("Error fetching security events: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to fetch security events: " + e.getMessage()
            ));
        }
    }

    // ==================== 3. GET USER ACTIVITY ====================

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('DISTRICT_ADMIN')")
    public ResponseEntity<?> getUserActivity(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        start = start != null ? start : LocalDateTime.now().minusDays(30);
        end = end != null ? end : LocalDateTime.now();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        try {
            Page<AuditLog> userLogs = auditService.getUserActivity(userId, start, end, pageable);

            return ResponseEntity.ok(Map.of(
                    "userId", userId,
                    "logs", userLogs.getContent(),
                    "totalElements", userLogs.getTotalElements(),
                    "totalPages", userLogs.getTotalPages(),
                    "currentPage", userLogs.getNumber(),
                    "pageSize", userLogs.getSize(),
                    "period", Map.of("start", start, "end", end)
            ));
        } catch (Exception e) {
            log.error("Error fetching user activity: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to fetch user activity: " + e.getMessage()
            ));
        }
    }

    // ==================== 4. GET AUDIT STATISTICS ====================

    @GetMapping("/stats")
    public ResponseEntity<?> getAuditStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        start = start != null ? start : LocalDateTime.now().minusDays(7);
        end = end != null ? end : LocalDateTime.now();

        try {
            Map<String, Object> stats = auditService.getAuditStatistics(start, end);

            return ResponseEntity.ok(Map.of(
                    "statistics", stats,
                    "period", Map.of("start", start, "end", end)
            ));
        } catch (Exception e) {
            log.error("Error fetching audit stats: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to fetch audit stats: " + e.getMessage()
            ));
        }
    }

    // ==================== 5. GET ACTION TYPES ====================

    @GetMapping("/actions")
    public ResponseEntity<?> getActionTypes() {
        try {
            List<String> actions = auditService.getDistinctActions();

            return ResponseEntity.ok(Map.of(
                    "actions", actions,
                    "count", actions.size()
            ));
        } catch (Exception e) {
            log.error("Error fetching action types: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to fetch action types: " + e.getMessage()
            ));
        }
    }

    // ==================== 6. GET RESOURCE TYPES ====================

    @GetMapping("/resource-types")
    public ResponseEntity<?> getResourceTypes() {
        try {
            List<String> resourceTypes = auditService.getDistinctResourceTypes();

            return ResponseEntity.ok(Map.of(
                    "resourceTypes", resourceTypes,
                    "count", resourceTypes.size()
            ));
        } catch (Exception e) {
            log.error("Error fetching resource types: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to fetch resource types: " + e.getMessage()
            ));
        }
    }

    // ==================== 7. VERIFY AUDIT CHAIN (BLOCKCHAIN) ====================

    @GetMapping("/verify-chain")
    @PreAuthorize("hasRole('DISTRICT_ADMIN')")
    public ResponseEntity<?> verifyAuditChain() {
        try {
            ChainVerificationResult result = auditService.verifyAuditChain();

            return ResponseEntity.ok(Map.of(
                    "verified", result.isVerified(),
                    "message", result.getMessage(),
                    "totalLogs", result.getTotalLogs(),
                    "brokenLinks", result.getBrokenLinks(),
                    "firstLogId", result.getFirstLogId(),
                    "lastLogId", result.getLastLogId()
            ));
        } catch (Exception e) {
            log.error("Error verifying audit chain: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to verify audit chain: " + e.getMessage()
            ));
        }
    }

    // ==================== 8. EXPORT AUDIT LOGS ====================

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportAuditLogsCsv(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        start = start != null ? start : LocalDateTime.now().minusDays(30);
        end = end != null ? end : LocalDateTime.now();

        try {
            byte[] csvData = auditService.exportAuditLogsCsv(username, action, start, end);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-logs.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csvData);
        } catch (Exception e) {
            log.error("Error exporting audit logs: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(("Error: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/export/json")
    public ResponseEntity<byte[]> exportAuditLogsJson(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        start = start != null ? start : LocalDateTime.now().minusDays(30);
        end = end != null ? end : LocalDateTime.now();

        try {
            byte[] jsonData = auditService.exportAuditLogsJson(username, action, start, end);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-logs.json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonData);
        } catch (Exception e) {
            log.error("Error exporting audit logs: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(("Error: " + e.getMessage()).getBytes());
        }
    }

    // ==================== 9. GET LOG BY ID ====================

    @GetMapping("/logs/{logId}")
    public ResponseEntity<?> getAuditLogById(@PathVariable UUID logId) {
        try {
            AuditLog log = auditService.getAuditLogById(logId);

            return ResponseEntity.ok(Map.of(
                    "log", log
            ));
        } catch (Exception e) {
            log.error("Error fetching audit log: {}", e.getMessage(), e);
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Audit log not found: " + e.getMessage()
            ));
        }
    }

    // ==================== 10. GET USER SUMMARY ====================

    @GetMapping("/user-summary")
    @PreAuthorize("hasRole('DISTRICT_ADMIN')")
    public ResponseEntity<?> getUserSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        start = start != null ? start : LocalDateTime.now().minusDays(30);
        end = end != null ? end : LocalDateTime.now();

        try {
            List<Map<String, Object>> userSummary = auditService.getUserActivitySummary(start, end);

            return ResponseEntity.ok(Map.of(
                    "userSummary", userSummary,
                    "period", Map.of("start", start, "end", end)
            ));
        } catch (Exception e) {
            log.error("Error fetching user summary: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to fetch user summary: " + e.getMessage()
            ));
        }
    }

    // ==================== 11. GET RESOURCE ACTIVITY ====================

    @GetMapping("/resource/{resourceType}/{resourceId}")
    public ResponseEntity<?> getResourceActivity(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        start = start != null ? start : LocalDateTime.now().minusDays(30);
        end = end != null ? end : LocalDateTime.now();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        try {
            Page<AuditLog> resourceLogs = auditService.getResourceActivity(
                    resourceType, resourceId, start, end, pageable
            );

            return ResponseEntity.ok(Map.of(
                    "resourceType", resourceType,
                    "resourceId", resourceId,
                    "logs", resourceLogs.getContent(),
                    "totalElements", resourceLogs.getTotalElements(),
                    "totalPages", resourceLogs.getTotalPages(),
                    "currentPage", resourceLogs.getNumber(),
                    "pageSize", resourceLogs.getSize(),
                    "period", Map.of("start", start, "end", end)
            ));
        } catch (Exception e) {
            log.error("Error fetching resource activity: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to fetch resource activity: " + e.getMessage()
            ));
        }
    }

    // ==================== 12. GET RECENT LOGS ====================

    @GetMapping("/recent")
    public ResponseEntity<?> getRecentLogs(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String resourceType) {

        if (limit < 1 || limit > 100) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Limit must be between 1 and 100"
            ));
        }

        try {
            List<AuditLog> recentLogs = auditService.getRecentLogs(limit, resourceType);

            return ResponseEntity.ok(Map.of(
                    "logs", recentLogs,
                    "count", recentLogs.size()
            ));
        } catch (Exception e) {
            log.error("Error fetching recent logs: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to fetch recent logs: " + e.getMessage()
            ));
        }
    }

    // ==================== INNER CLASSES ====================

    @lombok.Data
    @lombok.Builder
    public static class ChainVerificationResult {
        private boolean verified;
        private String message;
        private long totalLogs;
        private long brokenLinks;
        private UUID firstLogId;
        private UUID lastLogId;
    }
}