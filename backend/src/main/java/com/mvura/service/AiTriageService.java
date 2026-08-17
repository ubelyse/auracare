package com.mvura.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mvura.model.Gender;
import com.mvura.model.Priority;
import com.mvura.model.TriageResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AiTriageService {

    @Value("${app.ai.huggingface.api-key:}")
    private String apiKey;

    @Value("${app.ai.huggingface.model:mistralai/Mistral-7B-Instruct-v0.1}")
    private String model;

    @Value("${app.ai.huggingface.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${app.ai.huggingface.retry.backoff-ms:1000}")
    private long backoffMs;

    @Value("${app.ai.huggingface.cache.ttl-minutes:5}")
    private int cacheTtlMinutes;

    @Value("${app.ai.huggingface.cache.max-size:1000}")
    private int cacheMaxSize;

    private final RestTemplate restTemplate;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache for storing AI results
    private Cache<String, TriageResult> resultCache;

    // Circuit breaker for API calls
    private CircuitBreaker circuitBreaker;

    // Pattern to extract JSON from AI response
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[^{}]*\\}");

    public AiTriageService(RestTemplateBuilder builder, AuditService auditService) {
        this.auditService = auditService;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(8))
                .build();
    }

    @PostConstruct
    public void init() {
        // Initialize cache
        this.resultCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(cacheMaxSize)
                .recordStats()
                .build();

        // Initialize circuit breaker
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // 50% failure rate triggers open
                .slowCallRateThreshold(50) // 50% slow calls triggers open
                .slowCallDurationThreshold(Duration.ofSeconds(5)) // 5 seconds = slow
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        this.circuitBreaker = registry.circuitBreaker("aiTriageService");
    }

    public TriageResult analyzeSymptoms(String symptoms, Integer age, Gender gender, Boolean isPregnant) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Hugging Face API key not configured, skipping AI triage");
            return null;
        }

        // Generate cache key
        String cacheKey = generateCacheKey(symptoms, age, gender, isPregnant);

        // Check cache first
        TriageResult cachedResult = resultCache.getIfPresent(cacheKey);
        if (cachedResult != null) {
            log.info("Returning cached AI triage result for symptoms: {}",
                    symptoms.substring(0, Math.min(30, symptoms.length())) + "...");
            return cachedResult;
        }

        // Perform with circuit breaker and retry
        try {
            // Use executeSupplier with a Supplier
            TriageResult result = circuitBreaker.executeSupplier(() ->
                    performWithRetry(symptoms, age, gender, isPregnant)
            );

            // Cache the result if successful
            if (result != null && result.getPriority() != null) {
                resultCache.put(cacheKey, result);
                log.info("Cached AI triage result for future requests");
            }

            return result;

        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker is OPEN - skipping AI triage call");
            return null;
        } catch (Exception e) {
            log.error("AI triage failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private TriageResult performWithRetry(String symptoms, Integer age, Gender gender, Boolean isPregnant) {
        String prompt = buildPrompt(symptoms, age, gender, isPregnant);

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                log.debug("AI triage attempt {}/{}", attempt, maxRetryAttempts);
                long startTime = System.currentTimeMillis();

                TriageResult result = callAiApi(prompt);

                long duration = System.currentTimeMillis() - startTime;

                // Audit the AI call
                auditAICall(prompt, result, duration, attempt, true);

                if (result != null && result.getPriority() != null) {
                    log.info("AI triage succeeded on attempt {}: priority={}, duration={}ms",
                            attempt, result.getPriority(), duration);
                    return result;
                }

                // If we got a null result but no exception, try again
                log.warn("AI triage attempt {} returned null result", attempt);

            } catch (Exception e) {
                log.warn("AI triage attempt {} failed: {}", attempt, e.getMessage());

                // Audit the failed call
                auditAICall(prompt, null, 0, attempt, false);

                // If not the last attempt, wait before retrying with exponential backoff
                if (attempt < maxRetryAttempts) {
                    long waitTime = backoffMs * (long) Math.pow(2, attempt - 1);
                    log.info("Waiting {}ms before retry", waitTime);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }
            }
        }

        // All retries failed
        log.error("All {} retry attempts failed for AI triage", maxRetryAttempts);
        return null;
    }

    private TriageResult callAiApi(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> request = Map.of(
                    "inputs", prompt,
                    "parameters", Map.of(
                            "max_new_tokens", 500,
                            "temperature", 0.3,
                            "return_full_text", false
                    )
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
//            String url = "https://api-inference.huggingface.co/models/" + model;
            String url = "https://router.huggingface.co/hf-inference/v1/models/" + model;

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getBody() != null) {
                return parseAiResponse(response.getBody());
            }

            return null;

        } catch (Exception e) {
            log.error("AI API call error: {}", e.getMessage());
            throw e; // Let the retry logic handle it
        }
    }

    private String buildPrompt(String symptoms, Integer age, Gender gender, Boolean isPregnant) {
        String sanitizedSymptoms = symptoms
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .trim();

        if (sanitizedSymptoms.length() > 500) {
            sanitizedSymptoms = sanitizedSymptoms.substring(0, 500);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a medical triage assistant. Analyze the following patient symptoms and provide a triage assessment.\n\n");
        prompt.append("Patient Information:\n");
        prompt.append("- Age: ").append(age != null ? age : "Unknown").append("\n");
        prompt.append("- Gender: ").append(gender != null ? gender : "Unknown").append("\n");
        if (isPregnant != null && isPregnant) {
            prompt.append("- Pregnant: Yes\n");
        }
        prompt.append("- Symptoms: ").append(sanitizedSymptoms).append("\n\n");
        prompt.append("Based on the symptoms, respond with ONLY a single JSON object, no other text, ")
                .append("using exactly these keys:\n");
        prompt.append("{\"priority\": \"EMERGENCY|HIGH|MEDIUM|LOW\", ")
                .append("\"triageScore\": <integer 0-100, higher = more urgent>, ")
                .append("\"confidence\": <float 0.0-1.0, your own certainty in this assessment>, ")
                .append("\"waitTimeMinutes\": <integer>, ")
                .append("\"recommendations\": \"<short string>\"}");

        return prompt.toString();
    }

    private TriageResult parseAiResponse(String response) {
        if (response == null || response.isBlank()) {
            log.warn("Empty AI response");
            return null;
        }

        TriageResult fromJson = tryParseJson(response);
        if (fromJson != null) {
            return fromJson;
        }

        log.warn("AI response was not valid JSON, falling back to keyword/regex extraction");
        return parseWithFallbackHeuristics(response);
    }

    private TriageResult tryParseJson(String response) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(response);
        if (!matcher.find()) {
            return null;
        }

        try {
            String jsonBlock = matcher.group();
            JsonNode node = objectMapper.readTree(jsonBlock);

            String priorityText = node.path("priority").asText("").toUpperCase();
            Priority priority = switch (priorityText) {
                case "EMERGENCY" -> Priority.EMERGENCY;
                case "HIGH" -> Priority.HIGH;
                case "MEDIUM" -> Priority.MEDIUM;
                case "LOW" -> Priority.LOW;
                default -> null;
            };
            if (priority == null) {
                return null;
            }

            int triageScore = node.path("triageScore").asInt(-1);
            if (triageScore < 0 || triageScore > 100) {
                triageScore = defaultScoreForPriority(priority);
            }

            double confidence = node.path("confidence").asDouble(-1);
            if (confidence < 0.0 || confidence > 1.0) {
                confidence = -1;
            }

            int waitTime = node.path("waitTimeMinutes").asInt(-1);
            if (waitTime < 0) {
                waitTime = defaultWaitForPriority(priority);
            }

            String recommendations = node.path("recommendations").asText(
                    "Please consult with a healthcare provider for further evaluation.");

            return TriageResult.builder()
                    .priority(priority)
                    .triageScore(triageScore)
                    .triageMethod("AI")
                    .estimatedWaitMinutes(waitTime)
                    .recommendations(recommendations)
                    .aiConfidence(confidence)
                    .build();

        } catch (Exception e) {
            log.debug("JSON parse of AI response failed: {}", e.getMessage());
            return null;
        }
    }

    private TriageResult parseWithFallbackHeuristics(String response) {
        String lowerResponse = response.toLowerCase();

        Priority priority;
        if (lowerResponse.contains("emergency")) {
            priority = Priority.EMERGENCY;
        } else if (lowerResponse.contains("high")) {
            priority = Priority.HIGH;
        } else if (lowerResponse.contains("medium")) {
            priority = Priority.MEDIUM;
        } else if (lowerResponse.contains("low")) {
            priority = Priority.LOW;
        } else {
            log.warn("Could not determine priority from fallback parsing");
            return null;
        }

        int triageScore = defaultScoreForPriority(priority);

        Pattern waitPattern = Pattern.compile("(\\d+)\\s*(?:min|minute)");
        Matcher matcher = waitPattern.matcher(response);
        int waitTime = matcher.find() ? Integer.parseInt(matcher.group(1)) : defaultWaitForPriority(priority);

        Pattern recPattern = Pattern.compile("recommendations?[:\\\"]\\s*(.+?)(?:,|$|\\n)");
        matcher = recPattern.matcher(response);
        String recommendations = matcher.find() ? matcher.group(1).trim()
                : "Please consult with a healthcare provider for further evaluation.";

        return TriageResult.builder()
                .priority(priority)
                .triageScore(triageScore)
                .triageMethod("AI_FALLBACK")
                .estimatedWaitMinutes(waitTime)
                .recommendations(recommendations)
                .aiConfidence(-1.0)
                .build();
    }

    private String generateCacheKey(String symptoms, Integer age, Gender gender, Boolean isPregnant) {
        String symptomKey = symptoms.length() > 50 ? symptoms.substring(0, 50) : symptoms;
        boolean pregnant = isPregnant != null && isPregnant;
        return String.format("%s|%d|%s|%b",
                symptomKey,
                age != null ? age : 0,
                gender != null ? gender.name() : "UNKNOWN",
                pregnant);
    }

    private void auditAICall(String prompt, TriageResult result, long duration, int attempt, boolean success) {
        try {
            String priority = result != null && result.getPriority() != null ? result.getPriority().name() : "FAILED";
            int triageScore = result != null ? result.getTriageScore() : -1;
            double confidence = result != null ? result.getAiConfidence() : -1;

            Map<String, Object> details = Map.of(
                    "prompt", prompt.substring(0, Math.min(200, prompt.length())),
                    "attempt", attempt,
                    "durationMs", duration,
                    "success", success,
                    "model", model != null ? model : "unknown",
                    "timestamp", LocalDateTime.now().toString(),
                    "priority", priority,
                    "triageScore", triageScore,
                    "confidence", confidence
            );

            auditService.logAction(
                    success ? "AI_TRIAGE_SUCCESS" : "AI_TRIAGE_FAILED",
                    "AI_TRIAGE",
                    null,
                    "SYSTEM",
                    "127.0.0.1",
                    null,
                    details
            );
        } catch (Exception e) {
            log.warn("Failed to audit AI call: {}", e.getMessage());
        }
    }

    private int defaultScoreForPriority(Priority priority) {
        return switch (priority) {
            case EMERGENCY -> 90;
            case HIGH -> 70;
            case MEDIUM -> 50;
            case LOW -> 30;
        };
    }

    private int defaultWaitForPriority(Priority priority) {
        return switch (priority) {
            case EMERGENCY -> 5;
            case HIGH -> 15;
            case MEDIUM -> 30;
            case LOW -> 60;
        };
    }

    // ===== PUBLIC METHODS FOR MONITORING =====

    @SuppressWarnings("unused")
    public CacheStats getCacheStats() {
        return CacheStats.builder()
                .hitCount(resultCache.stats().hitCount())
                .missCount(resultCache.stats().missCount())
                .hitRate(resultCache.stats().hitRate())
                .estimatedSize(resultCache.estimatedSize())
                .build();
    }

    @SuppressWarnings("unused")
    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker != null ? circuitBreaker.getState() : CircuitBreaker.State.CLOSED;
    }

    @SuppressWarnings("unused")
    public void resetCache() {
        if (resultCache != null) {
            resultCache.invalidateAll();
            log.info("AI triage cache cleared");
        }
    }

    @Data
    @Builder
    public static class CacheStats {
        private long hitCount;
        private long missCount;
        private double hitRate;
        private long estimatedSize;
    }
}