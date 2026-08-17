package com.mvura.controller;

import com.mvura.service.UssdService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ussd")
@RequiredArgsConstructor
@Slf4j
public class UssdController {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final UssdService ussdService;

    @Value("${app.ussd.api-key:}")
    private String apiKey;

    @PostMapping(value = "/callback", produces = "text/plain")
    public ResponseEntity<String> handleUssdCallback(
            @RequestParam String sessionId,
            @RequestParam String phoneNumber,
            @RequestParam(required = false, defaultValue = "") String text,
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            HttpServletRequest request) {

        // Generate correlation ID if not provided
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        // Set MDC for logging
        MDC.put("correlationId", correlationId);
        MDC.put("sessionId", sessionId);
        MDC.put("phoneNumber", phoneNumber);

        log.info("USSD Callback - Text: {}, IP: {}", text, request.getRemoteAddr());

        try {
            // Validate API key (if configured)
            if (apiKey != null && !apiKey.isEmpty()) {
                if (apiKeyHeader == null || !apiKeyHeader.equals(apiKey)) {
                    log.warn("Unauthorized USSD request from IP: {}", request.getRemoteAddr());
                    return ResponseEntity.status(401).body("END Unauthorized");
                }
            }

            // Input validation
            if (sessionId == null || sessionId.isEmpty()) {
                return ResponseEntity.ok("END Invalid session ID");
            }
            if (phoneNumber == null || phoneNumber.isEmpty()) {
                return ResponseEntity.ok("END Invalid phone number");
            }

            // Process request
            String response = ussdService.handleUssdRequest(sessionId, phoneNumber, text);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("USSD callback error: {}", e.getMessage(), e);
            return ResponseEntity.ok("END An error occurred. Please try again later.");
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> testUssd() {
        return ResponseEntity.ok("CON MVURA USSD Test\n1. Test Menu\n2. Exit\n\nChoose option:");
    }

    @PostMapping("/test")
    public ResponseEntity<String> testUssdCallback(
            @RequestParam String sessionId,
            @RequestParam String phoneNumber,
            @RequestParam String text) {

        log.info("Test USSD Callback - Session: {}, Phone: {}, Text: {}", sessionId, phoneNumber, text);

        if (text.isEmpty()) {
            return ResponseEntity.ok("CON MVURA USSD Test\n1. Test Menu\n2. Exit\n\nChoose option:");
        }

        switch (text) {
            case "1":
                return ResponseEntity.ok("CON You selected Test Menu\n0. Back\n\nChoose option:");
            case "2":
            case "0":
                return ResponseEntity.ok("END Goodbye!");
            default:
                return ResponseEntity.ok("END Invalid option!");
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getUssdStats() {
        return ResponseEntity.ok(Map.of(
                "activeSessions", ussdService.getActiveSessionCount(),
                "totalRequests", ussdService.getTotalRequestCount()
        ));
    }
}