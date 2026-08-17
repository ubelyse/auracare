package com.mvura.service;

import com.mvura.model.Priority;
import com.mvura.model.TriageResult;
import com.mvura.model.Ticket;
import com.mvura.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TriageService {

    private final AiTriageService aiTriageService;
    private final RuleBasedTriageService ruleBasedTriageService;
    private final AuditService auditService;
    private final TicketRepository ticketRepository;

    // ==================== CACHE ====================

    private final Map<String, CachedTriageResult> triageCache = new ConcurrentHashMap<>();
    private static final int CACHE_MAX_SIZE = 1000;
    private static final int CACHE_TTL_MINUTES = 30;

    // ==================== METRICS ====================

    private final Map<String, AtomicLong> methodUsage = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> priorityDistribution = new ConcurrentHashMap<>();
    private final AtomicLong totalTriages = new AtomicLong(0);
    private final AtomicLong aiSuccessCount = new AtomicLong(0);
    private final AtomicLong ruleBasedCount = new AtomicLong(0);
    private final AtomicLong defaultCount = new AtomicLong(0);

    // ==================== INITIALIZATION ====================

    @PostConstruct
    public void initMetrics() {
        methodUsage.put("AI", new AtomicLong(0));
        methodUsage.put("RULE_BASED", new AtomicLong(0));
        methodUsage.put("DEFAULT", new AtomicLong(0));

        for (Priority priority : Priority.values()) {
            priorityDistribution.put(priority.name(), new AtomicLong(0));
        }

        log.info("TriageService initialized with cache size: {}, TTL: {} minutes",
                CACHE_MAX_SIZE, CACHE_TTL_MINUTES);
    }

    // ==================== MAIN TRIAGE METHOD ====================

    public TriageResult performTriage(Ticket ticket) {
        log.info("Performing triage for ticket: {}", ticket.getTicketNumber());

        // Check cache first
        String cacheKey = ticket.getId().toString();
        CachedTriageResult cached = triageCache.get(cacheKey);
        if (cached != null && !isCacheExpired(cached)) {
            log.debug("Returning cached triage result for ticket: {}", ticket.getTicketNumber());
            return cached.getResult();
        }

        TriageResult result = performTriageInternal(ticket);

        // Cache the result
        if (result != null) {
            triageCache.put(cacheKey, new CachedTriageResult(result, LocalDateTime.now()));
            // Limit cache size
            if (triageCache.size() > CACHE_MAX_SIZE) {
                trimCache();
            }
        }

        // Update metrics
        updateMetrics(result);

        // Audit the triage
        auditTriage(ticket, result);

        return result;
    }

    // ==================== INTERNAL TRIAGE LOGIC ====================

    private TriageResult performTriageInternal(Ticket ticket) {
        // First, scrub PHI for AI
        String sanitizedSymptoms = ticket.getSanitizedSymptoms();

        TriageResult result = null;
        String triageMethod = "RULE_BASED";

        // Try AI first
        try {
            log.debug("Attempting AI triage for ticket: {}", ticket.getTicketNumber());
            result = aiTriageService.analyzeSymptoms(
                    sanitizedSymptoms,
                    ticket.getAge(),
                    ticket.getGender(),
                    ticket.getIsPregnant()
            );

            if (result != null && isValidTriageResult(result)) {
                triageMethod = "AI";
                aiSuccessCount.incrementAndGet();
                log.info("AI triage successful for ticket: {}", ticket.getTicketNumber());
            } else {
                log.warn("AI triage returned invalid result for ticket: {}", ticket.getTicketNumber());
                result = null;
            }
        } catch (Exception e) {
            log.warn("AI triage failed for ticket: {}, falling back to rule-based",
                    ticket.getTicketNumber(), e);
        }

        // Fallback to rule-based if AI failed or returned null
        if (result == null) {
            log.debug("Using rule-based triage for ticket: {}", ticket.getTicketNumber());
            result = ruleBasedTriageService.evaluate(ticket);
            if (isValidTriageResult(result)) {
                triageMethod = "RULE_BASED";
                ruleBasedCount.incrementAndGet();
            } else {
                log.warn("Rule-based triage returned invalid result for ticket: {}", ticket.getTicketNumber());
                result = null;
            }
        }

        // Ultimate default fallback
        if (result == null) {
            log.warn("All triage methods failed for ticket: {}, using default", ticket.getTicketNumber());
            result = createDefaultTriageResult();
            triageMethod = "DEFAULT";
            defaultCount.incrementAndGet();
        }

        // Ensure we have a valid result
        result = ensureValidTriageResult(result);

        // Set the triage method
        result.setTriageMethod(triageMethod);

        // Enhance recommendations
        String enhancedRecommendations = enhanceRecommendations(result, ticket);
        result.setRecommendations(enhancedRecommendations);

        // Validate the final result
        if (!isValidTriageResult(result)) {
            log.error("Final triage result is invalid for ticket: {}", ticket.getTicketNumber());
            result = createDefaultTriageResult();
            result.setTriageMethod("VALIDATION_FALLBACK");
        }

        totalTriages.incrementAndGet();

        log.info("Triage complete for ticket: {} - Priority: {}, Score: {}, Method: {}",
                ticket.getTicketNumber(), result.getPriority(), result.getTriageScore(), triageMethod);

        return result;
    }

    // ==================== VALIDATION ====================

    private boolean isValidTriageResult(TriageResult result) {
        if (result == null) {
            return false;
        }
        if (result.getPriority() == null) {
            return false;
        }
        if (result.getTriageScore() == null || result.getTriageScore() < 0 || result.getTriageScore() > 100) {
            return false;
        }
        if (result.getEstimatedWaitMinutes() == null || result.getEstimatedWaitMinutes() < 0) {
            return false;
        }
        return true;
    }

    private TriageResult ensureValidTriageResult(TriageResult result) {
        if (result == null) {
            return createDefaultTriageResult();
        }

        if (result.getPriority() == null) {
            result.setPriority(Priority.MEDIUM);
        }

        if (result.getTriageScore() == null || result.getTriageScore() < 0 || result.getTriageScore() > 100) {
            result.setTriageScore(50);
        }

        if (result.getEstimatedWaitMinutes() == null || result.getEstimatedWaitMinutes() < 0) {
            result.setEstimatedWaitMinutes(defaultWaitForPriority(result.getPriority()));
        }

        if (result.getRecommendations() == null || result.getRecommendations().isEmpty()) {
            result.setRecommendations("Please consult with a healthcare provider.");
        }

        return result;
    }

    private TriageResult createDefaultTriageResult() {
        return TriageResult.builder()
                .priority(Priority.MEDIUM)
                .triageScore(50)
                .triageMethod("DEFAULT")
                .estimatedWaitMinutes(30)
                .recommendations("Default triage applied. Please consult with doctor.")
                .aiConfidence(0.5)
                .build();
    }

    // ==================== CACHE MANAGEMENT ====================

    private boolean isCacheExpired(CachedTriageResult cached) {
        return cached.getCachedAt().plusMinutes(CACHE_TTL_MINUTES).isBefore(LocalDateTime.now());
    }

    private void trimCache() {
        // Remove oldest entries (by cache time)
        triageCache.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(
                        (a, b) -> a.getCachedAt().compareTo(b.getCachedAt())
                ))
                .limit(triageCache.size() - CACHE_MAX_SIZE)
                .forEach(entry -> triageCache.remove(entry.getKey()));
    }

    @Scheduled(fixedRate = 600000) // Every 10 minutes
    public void cleanupCache() {
        int before = triageCache.size();
        triageCache.entrySet().removeIf(entry -> isCacheExpired(entry.getValue()));
        int after = triageCache.size();
        if (before != after) {
            log.info("Cleaned up {} expired triage cache entries", before - after);
        }
    }

    public void clearCache() {
        triageCache.clear();
        log.info("Triage cache cleared");
    }

    public void clearCacheForTicket(UUID ticketId) {
        triageCache.remove(ticketId.toString());
        log.debug("Triage cache cleared for ticket: {}", ticketId);
    }

    // ==================== METRICS ====================

    private void updateMetrics(TriageResult result) {
        if (result == null) return;

        String method = result.getTriageMethod() != null ? result.getTriageMethod() : "UNKNOWN";
        methodUsage.computeIfAbsent(method, k -> new AtomicLong(0)).incrementAndGet();

        String priority = result.getPriority() != null ? result.getPriority().name() : "UNKNOWN";
        priorityDistribution.computeIfAbsent(priority, k -> new AtomicLong(0)).incrementAndGet();
    }

    public TriageMetrics getTriageMetrics() {
        return TriageMetrics.builder()
                .totalTriages(totalTriages.get())
                .aiSuccessCount(aiSuccessCount.get())
                .ruleBasedCount(ruleBasedCount.get())
                .defaultCount(defaultCount.get())
                .methodUsage(methodUsage.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get())))
                .priorityDistribution(priorityDistribution.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get())))
                .cacheSize(triageCache.size())
                .timestamp(LocalDateTime.now())
                .build();
    }

    public void resetMetrics() {
        methodUsage.clear();
        priorityDistribution.clear();
        totalTriages.set(0);
        aiSuccessCount.set(0);
        ruleBasedCount.set(0);
        defaultCount.set(0);
        initMetrics();
        log.info("Triage metrics reset");
    }

    // ==================== AUDIT LOGGING ====================

    private void auditTriage(Ticket ticket, TriageResult result) {
        try {
            auditService.logAction(
                    "TRIAGE_PERFORMED",
                    "TICKET",
                    ticket.getId().toString(),
                    "system",
                    null,
                    null,
                    Map.of(
                            "ticketNumber", ticket.getTicketNumber(),
                            "priority", result != null ? result.getPriority().name() : "UNKNOWN",
                            "score", result != null ? result.getTriageScore() : 0,
                            "method", result != null ? result.getTriageMethod() : "UNKNOWN",
                            "estimatedWaitMinutes", result != null ? result.getEstimatedWaitMinutes() : 0,
                            "aiConfidence", result != null ? result.getAiConfidence() : 0,
                            "timestamp", LocalDateTime.now().toString()
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to audit triage: {}", e.getMessage());
        }
    }

    // ==================== RECOMMENDATIONS ENHANCEMENT ====================

    private String enhanceRecommendations(TriageResult result, Ticket ticket) {
        StringBuilder sb = new StringBuilder();

        // Start with existing recommendations
        if (result.getRecommendations() != null) {
            sb.append(result.getRecommendations());
        } else {
            sb.append("Please consult with a healthcare provider.");
        }

        sb.append("\n\n");

        // Add patient-specific advice
        if (ticket.getAge() != null && ticket.getAge() >= 65) {
            sb.append("⚠️ Elderly patient (age ").append(ticket.getAge()).append(") - monitor closely.\n");
        }

        if (ticket.getAge() != null && ticket.getAge() < 5) {
            sb.append("⚠️ Young child (age ").append(ticket.getAge()).append(") - requires special attention.\n");
        }

        if (ticket.getIsPregnant() != null && ticket.getIsPregnant()) {
            sb.append("⚠️ Pregnant patient - consider obstetric consultation.\n");
        }

        if (ticket.getChronicConditions() != null && !ticket.getChronicConditions().isEmpty()) {
            sb.append("⚠️ Chronic conditions: ").append(ticket.getChronicConditions()).append("\n");
        }

        if (ticket.getHasAllergies() != null && ticket.getHasAllergies()) {
            sb.append("⚠️ Allergies reported: ").append(
                    ticket.getAllergiesDescription() != null ? ticket.getAllergiesDescription() : "Yes"
            ).append("\n");
        }

        if (ticket.getHasRecentSurgery() != null && ticket.getHasRecentSurgery()) {
            sb.append("⚠️ Recent surgery: ").append(
                    ticket.getRecentSurgeryDetails() != null ? ticket.getRecentSurgeryDetails() : "Yes"
            ).append("\n");
        }

        // Add priority-specific advice
        if (result.getPriority() == Priority.EMERGENCY) {
            sb.append("\n🚨 IMMEDIATE ACTION REQUIRED - Call emergency services or go to ER immediately!");
        } else if (result.getPriority() == Priority.HIGH) {
            sb.append("\n⚡ High priority - Please see a doctor within 15-20 minutes.");
        } else if (result.getPriority() == Priority.MEDIUM) {
            sb.append("\n📋 Medium priority - Please wait for your turn.");
        } else {
            sb.append("\n✅ Low priority - Please wait for your turn.");
        }

        // Add wait time
        sb.append("\n⏱️ Estimated wait time: ").append(result.getEstimatedWaitMinutes()).append(" minutes.");

        return sb.toString();
    }

    // ==================== COMPARISON TOOLS ====================

    public TriageComparison compareTriageMethods(Ticket ticket) {
        String sanitizedSymptoms = ticket.getSanitizedSymptoms();

        TriageResult aiResult = null;
        try {
            aiResult = aiTriageService.analyzeSymptoms(
                    sanitizedSymptoms,
                    ticket.getAge(),
                    ticket.getGender(),
                    ticket.getIsPregnant()
            );
        } catch (Exception e) {
            log.warn("AI comparison failed: {}", e.getMessage());
        }

        TriageResult ruleResult = ruleBasedTriageService.evaluate(ticket);

        return TriageComparison.builder()
                .ticketNumber(ticket.getTicketNumber())
                .aiResult(aiResult)
                .ruleResult(ruleResult)
                .isAgree(aiResult != null &&
                        ruleResult != null &&
                        aiResult.getPriority() == ruleResult.getPriority())
                .build();
    }

    // ==================== EMERGENCY DETECTION ====================

    public boolean isEmergencyTicket(Ticket ticket) {
        TriageResult result = performTriage(ticket);
        return result != null && result.getPriority() == Priority.EMERGENCY;
    }

    public boolean isHighPriorityTicket(Ticket ticket) {
        TriageResult result = performTriage(ticket);
        return result != null &&
                (result.getPriority() == Priority.EMERGENCY ||
                        result.getPriority() == Priority.HIGH);
    }

    public boolean requiresImmediateAttention(Ticket ticket) {
        TriageResult result = performTriage(ticket);
        if (result == null) return false;

        return result.getPriority() == Priority.EMERGENCY ||
                result.getPriority() == Priority.HIGH &&
                        result.getTriageScore() >= 70;
    }

    // ==================== BATCH TRIAGE ====================

    public Map<String, TriageResult> performBatchTriage(List<Ticket> tickets) {
        Map<String, TriageResult> results = new ConcurrentHashMap<>();

        tickets.parallelStream().forEach(ticket -> {
            try {
                TriageResult result = performTriage(ticket);
                results.put(ticket.getId().toString(), result);
            } catch (Exception e) {
                log.error("Batch triage failed for ticket: {}", ticket.getTicketNumber(), e);
            }
        });

        log.info("Batch triage completed for {} tickets", results.size());
        return results;
    }

    // ==================== HELPER METHODS ====================

    private int defaultWaitForPriority(Priority priority) {
        return switch (priority) {
            case EMERGENCY -> 5;
            case HIGH -> 15;
            case MEDIUM -> 30;
            case LOW -> 60;
        };
    }

    // ==================== INNER CLASSES ====================

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class CachedTriageResult {
        private TriageResult result;
        private LocalDateTime cachedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class TriageMetrics {
        private long totalTriages;
        private long aiSuccessCount;
        private long ruleBasedCount;
        private long defaultCount;
        private Map<String, Long> methodUsage;
        private Map<String, Long> priorityDistribution;
        private int cacheSize;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    public static class TriageComparison {
        private String ticketNumber;
        private TriageResult aiResult;
        private TriageResult ruleResult;
        private boolean isAgree;
    }
}