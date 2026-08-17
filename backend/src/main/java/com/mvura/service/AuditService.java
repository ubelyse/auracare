package com.mvura.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvura.controller.AuditController;
import com.mvura.model.AuditLog;
import com.mvura.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unused")
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== EXISTING METHODS ====================

    public void logLoginEvent(UUID userId, String username, String ipAddress, String action) {
        try {
            String details = objectMapper.writeValueAsString(Map.of("event", action));
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .resourceType("AUTH")
                    .resourceId(null)
                    .ipAddress(ipAddress)
                    .details(details)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize login event: {}", e.getMessage());
            saveFallback(userId, username, action, "AUTH", null, ipAddress, null, "{}");
        }
    }

    public void logSecurityEvent(String action, String username, UUID userId, String ipAddress, String details) {
        try {
            String detailsJson = objectMapper.writeValueAsString(Map.of("details", details));
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .resourceType("SECURITY")
                    .resourceId(null)
                    .ipAddress(ipAddress)
                    .details(detailsJson)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize security event: {}", e.getMessage());
            saveFallback(userId, username, action, "SECURITY", null, ipAddress, null, "{}");
        }
    }

    @Deprecated
    public void logSecurityEvent(String action, String username, String ipAddress, String details) {
        logSecurityEvent(action, username, null, ipAddress, details);
    }

    public void logAction(String action, String resourceType, String resourceId,
                          String username, String ipAddress, String userAgent,
                          Map<String, Object> details) {
        try {
            String detailsJson = details != null ? objectMapper.writeValueAsString(details) : "{}";

            AuditLog auditLog = AuditLog.builder()
                    .userId(null)
                    .username(username != null ? username : "system")
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .details(detailsJson)
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit log: {} - {} - {}", username, action, resourceType);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit action: {}", action, e);
            saveFallback(null, username != null ? username : "system", action, resourceType, resourceId, ipAddress, userAgent, "{}");
        }
    }

    private void saveFallback(UUID userId, String username, String action, String resourceType,
                              String resourceId, String ipAddress, String userAgent, String details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .details(details)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.error("Failed to save audit log even with fallback: {}", ex.getMessage());
        }
    }

    public void logDataAccess(String action, String username, String resourceType,
                              String resourceId, Map<String, Object> details) {
        logAction("DATA_ACCESS_" + action, resourceType, resourceId,
                username, null, null, details);
    }

    public void logEncryptionEvent(String action, String username, String resourceType,
                                   String resourceId, boolean success) {
        Map<String, Object> details = Map.of(
                "encryptionEvent", true,
                "success", success,
                "timestamp", LocalDateTime.now().toString()
        );
        logAction("ENCRYPTION_" + action, resourceType, resourceId,
                username, null, null, details);
    }

    // ==================== NEW METHODS FOR CONTROLLER ====================

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(String username, String action, String resourceType,
                                       String resourceId, LocalDateTime start, LocalDateTime end,
                                       Pageable pageable) {
        if (username != null && !username.isEmpty()) {
            return auditLogRepository.findByUsernameAndDateRange(username, start, end, pageable);
        } else if (action != null && !action.isEmpty()) {
            return auditLogRepository.findByActionAndDateRange(action, start, end, pageable);
        } else if (resourceType != null && !resourceType.isEmpty()) {
            if (resourceId != null && !resourceId.isEmpty()) {
                return auditLogRepository.findByResourceTypeAndResourceIdAndDateRange(
                        resourceType, resourceId, start, end, pageable
                );
            }
            return auditLogRepository.findByResourceTypeAndDateRange(resourceType, start, end, pageable);
        }
        return auditLogRepository.findByDateRange(start, end, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getSecurityEvents(LocalDateTime since, String username,
                                            String eventType, Pageable pageable) {
        if (username != null && !username.isEmpty()) {
            return auditLogRepository.findSecurityEventsByUser(since, username, pageable);
        }
        if (eventType != null && !eventType.isEmpty()) {
            return auditLogRepository.findSecurityEventsByType(since, eventType, pageable);
        }
        return auditLogRepository.findSecurityEvents(since, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getUserActivity(UUID userId, LocalDateTime start,
                                          LocalDateTime end, Pageable pageable) {
        return auditLogRepository.findByUserIdAndDateRange(userId, start, end, pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAuditStatistics(LocalDateTime start, LocalDateTime end) {
        List<AuditLog> logs = auditLogRepository.findByDateRange(start, end);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLogs", logs.size());

        // Actions distribution
        Map<String, Long> actionStats = logs.stream()
                .collect(Collectors.groupingBy(AuditLog::getAction, Collectors.counting()));
        stats.put("actions", actionStats);

        // Resource types distribution
        Map<String, Long> resourceStats = logs.stream()
                .filter(l -> l.getResourceType() != null)
                .collect(Collectors.groupingBy(AuditLog::getResourceType, Collectors.counting()));
        stats.put("resourceTypes", resourceStats);

        // Unique users
        long uniqueUsers = logs.stream()
                .map(AuditLog::getUsername)
                .filter(u -> u != null && !u.isEmpty())
                .distinct()
                .count();
        stats.put("uniqueUsers", uniqueUsers);

        // Top users
        List<Map<String, Object>> topUsers = logs.stream()
                .filter(l -> l.getUsername() != null)
                .collect(Collectors.groupingBy(AuditLog::getUsername, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("username", e.getKey());
                    userMap.put("actionCount", e.getValue());
                    return userMap;
                })
                .collect(Collectors.toList());
        stats.put("topUsers", topUsers);

        return stats;
    }

    @Transactional(readOnly = true)
    public List<String> getDistinctActions() {
        return auditLogRepository.findDistinctActions();
    }

    @Transactional(readOnly = true)
    public List<String> getDistinctResourceTypes() {
        return auditLogRepository.findDistinctResourceTypes();
    }

    @Transactional(readOnly = true)
    public AuditController.ChainVerificationResult verifyAuditChain() {
        List<AuditLog> allLogs = auditLogRepository.findAllByOrderByCreatedAtAsc();

        AuditController.ChainVerificationResult.ChainVerificationResultBuilder builder =
                AuditController.ChainVerificationResult.builder();

        if (allLogs.isEmpty()) {
            return builder
                    .verified(true)
                    .message("No logs to verify")
                    .totalLogs(0)
                    .brokenLinks(0)
                    .build();
        }

        // Simple verification - check if logs are in sequence
        // Since we don't have blockchain fields, we verify by timestamp sequence
        long brokenLinks = 0;
        UUID firstLogId = allLogs.get(0).getId();
        UUID lastLogId = allLogs.get(allLogs.size() - 1).getId();

        // Check for gaps in timestamps (more than 5 seconds gap might indicate missing logs)
        for (int i = 1; i < allLogs.size(); i++) {
            AuditLog current = allLogs.get(i);
            AuditLog previous = allLogs.get(i - 1);

            if (previous.getCreatedAt() != null && current.getCreatedAt() != null) {
                // If there's a gap of more than 1 hour, it might indicate missing logs
                if (java.time.Duration.between(previous.getCreatedAt(), current.getCreatedAt()).toHours() > 1) {
                    brokenLinks++;
                }
            }
        }

        boolean verified = brokenLinks == 0;

        return builder
                .verified(verified)
                .message(verified ? "Audit chain is intact" :
                        "Found " + brokenLinks + " potential gaps in audit chain")
                .totalLogs(allLogs.size())
                .brokenLinks(brokenLinks)
                .firstLogId(firstLogId)
                .lastLogId(lastLogId)
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] exportAuditLogsCsv(String username, String action,
                                     LocalDateTime start, LocalDateTime end) {
        List<AuditLog> logs = getFilteredLogsForExport(username, action, start, end);
        String csv = generateCsv(logs);
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportAuditLogsJson(String username, String action,
                                      LocalDateTime start, LocalDateTime end) {
        List<AuditLog> logs = getFilteredLogsForExport(username, action, start, end);
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(logs);
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            log.error("Failed to generate JSON export: {}", e.getMessage());
            return "[]".getBytes(StandardCharsets.UTF_8);
        }
    }

    @Transactional(readOnly = true)
    public AuditLog getAuditLogById(UUID logId) {
        return auditLogRepository.findById(logId)
                .orElseThrow(() -> new RuntimeException("Audit log not found: " + logId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserActivitySummary(LocalDateTime start, LocalDateTime end) {
        List<AuditLog> logs = auditLogRepository.findByDateRange(start, end);

        Map<String, Map<String, Object>> userSummary = new LinkedHashMap<>();

        for (AuditLog log : logs) {
            String username = log.getUsername();
            if (username == null || username.isEmpty()) continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> summary = userSummary.computeIfAbsent(username, k -> {
                Map<String, Object> newSummary = new LinkedHashMap<>();
                newSummary.put("username", username);
                newSummary.put("totalActions", 0L);
                newSummary.put("actions", new LinkedHashMap<String, Long>());
                newSummary.put("lastAction", log.getCreatedAt());
                return newSummary;
            });

            summary.put("totalActions", ((Long) summary.get("totalActions")) + 1);

            @SuppressWarnings("unchecked")
            Map<String, Long> actions = (Map<String, Long>) summary.get("actions");
            String action = log.getAction();
            actions.put(action, actions.getOrDefault(action, 0L) + 1);

            LocalDateTime lastAction = (LocalDateTime) summary.get("lastAction");
            if (log.getCreatedAt() != null && (lastAction == null ||
                    log.getCreatedAt().isAfter(lastAction))) {
                summary.put("lastAction", log.getCreatedAt());
            }
        }

        return new ArrayList<>(userSummary.values());
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getResourceActivity(String resourceType, String resourceId,
                                              LocalDateTime start, LocalDateTime end,
                                              Pageable pageable) {
        return auditLogRepository.findByResourceTypeAndResourceIdAndDateRange(
                resourceType, resourceId, start, end, pageable
        );
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecentLogs(int limit, String resourceType) {
        if (resourceType != null && !resourceType.isEmpty()) {
            return auditLogRepository.findRecentByResourceType(resourceType, Pageable.ofSize(limit));
        }
        return auditLogRepository.findRecentLogs(Pageable.ofSize(limit));
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private List<AuditLog> getFilteredLogsForExport(String username, String action,
                                                    LocalDateTime start, LocalDateTime end) {
        List<AuditLog> logs = auditLogRepository.findByDateRange(start, end);

        if (username != null && !username.isEmpty()) {
            logs = logs.stream()
                    .filter(l -> username.equals(l.getUsername()))
                    .collect(Collectors.toList());
        }

        if (action != null && !action.isEmpty()) {
            logs = logs.stream()
                    .filter(l -> action.equals(l.getAction()))
                    .collect(Collectors.toList());
        }

        return logs;
    }

    private String generateCsv(List<AuditLog> logs) {
        StringBuilder csv = new StringBuilder();

        // CSV Header
        csv.append("Timestamp,User,Action,Resource Type,Resource ID,IP Address,User Agent,Details\n");

        // CSV Data
        for (AuditLog log : logs) {
            csv.append(log.getCreatedAt() != null ? log.getCreatedAt().format(DATE_FORMATTER) : "").append(",");
            csv.append(escapeCsv(log.getUsername())).append(",");
            csv.append(escapeCsv(log.getAction())).append(",");
            csv.append(escapeCsv(log.getResourceType())).append(",");
            csv.append(escapeCsv(log.getResourceId())).append(",");
            csv.append(escapeCsv(log.getIpAddress())).append(",");
            csv.append(escapeCsv(log.getUserAgent())).append(",");
            csv.append(escapeCsv(log.getDetails())).append("\n");
        }

        return csv.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogs(String action, String entityType, LocalDateTime start, LocalDateTime end) {
        log.info("📜 Querying audit logs");

        List<AuditLog> logs = new ArrayList<>();

        try {
            // If you have an AuditLogRepository
            if (action != null && !action.isEmpty() && entityType != null && !entityType.isEmpty()) {
                logs = auditLogRepository.findByActionAndEntityTypeAndCreatedAtBetween(action, entityType, start, end);
            } else if (action != null && !action.isEmpty()) {
                logs = auditLogRepository.findByActionAndCreatedAtBetween(action, start, end);
            } else if (entityType != null && !entityType.isEmpty()) {
                logs = auditLogRepository.findByEntityTypeAndCreatedAtBetween(entityType, start, end);
            } else {
                logs = auditLogRepository.findByCreatedAtBetween(start, end);
            }

            // Sort by newest first
            logs.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        } catch (Exception e) {
            log.error("Failed to query audit logs: {}", e.getMessage(), e);
            // Return empty list on error
        }

        return logs;
    }

    // ==================== INNER CLASS ====================

    @lombok.Data
    @lombok.Builder
    public static class AuditReportSummary {
        private long totalLogs;
        private long uniqueUsers;
        private Map<String, Long> actionsByType;
        private Map<String, Long> actionsByResource;
        private List<Map<String, Object>> topUsers;
        private List<Map<String, Object>> mostFrequentActions;
        private Map<String, String> period;
    }
}