package com.mvura.service;

import com.mvura.model.Ticket;
import com.mvura.model.TicketStatus;
import com.mvura.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class UssdService {

    private final TicketRepository ticketRepository;
    private final AuditService auditService;
    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${app.ussd.api-key:}")
    private String apiKey;

    @Value("${app.ussd.username:}")
    private String username;

    @Value("${app.ussd.short-code:}")
    private String shortCode;

    @Value("${app.ussd.api-url:https://api.africastalking.com/version1/messaging}")
    private String apiUrl;

    private RestTemplate restTemplate;
    private final Map<String, UssdSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> requestTimestamps = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong(0);

    @PostConstruct
    public void init() {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        int removed = sessions.entrySet().removeIf(entry ->
                entry.getValue().getLastActivity().isBefore(cutoff)
        ) ? sessions.size() : 0;
        if (removed > 0) {
            log.info("Cleaned up {} expired USSD sessions", removed);
        }
    }

    public boolean isRateLimited(String phoneNumber) {
        LocalDateTime lastRequest = requestTimestamps.get(phoneNumber);
        if (lastRequest == null) {
            requestTimestamps.put(phoneNumber, LocalDateTime.now());
            requestCounts.put(phoneNumber, 1);
            return false;
        }
        if (lastRequest.isAfter(LocalDateTime.now().minusMinutes(1))) {
            int count = requestCounts.getOrDefault(phoneNumber, 0);
            if (count >= 5) {
                log.warn("Rate limit exceeded for phone: {}", phoneNumber);
                return true;
            }
            requestCounts.put(phoneNumber, count + 1);
        } else {
            requestCounts.put(phoneNumber, 1);
        }
        requestTimestamps.put(phoneNumber, LocalDateTime.now());
        return false;
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public long getTotalRequestCount() {
        return totalRequests.get();
    }

    public String handleUssdRequest(String sessionId, String phoneNumber, String text) {
        totalRequests.incrementAndGet();
        log.info("USSD Request - Session: {}, Phone: {}, Text: {}", sessionId, phoneNumber, text);

        // Rate limiting
        if (isRateLimited(phoneNumber)) {
            return "END Too many requests. Please try again later.";
        }

        // Get or create session
        UssdSession session = sessions.getOrDefault(sessionId, new UssdSession());
        session.setPhoneNumber(phoneNumber);
        session.setLastActivity(LocalDateTime.now());

        String response;
        boolean endSession = false;

        // Parse input
        String[] inputs = text.split("\\*");
        String currentInput = inputs[inputs.length - 1];

        if (text.isEmpty()) {
            response = buildMainMenu();
        } else if (inputs.length == 1) {
            response = handleMainMenu(currentInput, session);
        } else if (inputs.length == 2) {
            response = handleTicketEntry(currentInput, session);
        } else if (inputs.length == 3) {
            String pin = currentInput;
            response = handlePinVerification(pin, session);
            endSession = true;
        } else {
            response = "Invalid input. Please start again.\n" + buildMainMenu();
            endSession = true;
        }

        if (!endSession) {
            sessions.put(sessionId, session);
        } else {
            sessions.remove(sessionId);
        }

        return buildUssdResponse(response, endSession);
    }

    private String handleMainMenu(String input, UssdSession session) {
        switch (input) {
            case "1":
                session.setStep("CHECK_STATUS");
                return "Enter your ticket number:";
            case "2":
                session.setStep("CANCEL_TICKET");
                return "Enter your ticket number to cancel:";
            case "3":
                session.setStep("BOOK_APPOINTMENT");
                return "📅 Appointment booking coming soon. Please use the app.";
            default:
                return "Invalid option. Please try again.\n" + buildMainMenu();
        }
    }

    private String handleTicketEntry(String ticketNumber, UssdSession session) {
        session.setTicketNumber(ticketNumber);

        String validationError = validateTicketNumber(ticketNumber);
        if (validationError != null) {
            return validationError + "\n" + buildMainMenu();
        }

        if (session.getStep().equals("CHECK_STATUS")) {
            session.setStep("VERIFY_PIN");
            return "Enter your 4-digit PIN:";
        } else if (session.getStep().equals("CANCEL_TICKET")) {
            session.setStep("VERIFY_CANCEL");
            return "Enter your 4-digit PIN to confirm cancellation:";
        } else {
            return "Session expired. Please start again.\n" + buildMainMenu();
        }
    }

    private String handlePinVerification(String pin, UssdSession session) {
        if (pin == null || pin.length() != 4 || !pin.matches("\\d{4}")) {
            return "Invalid PIN format. Please enter 4 digits.";
        }

        if (session.getStep().equals("VERIFY_PIN")) {
            return verifyTicket(session.getTicketNumber(), pin);
        } else if (session.getStep().equals("VERIFY_CANCEL")) {
            return cancelTicket(session.getTicketNumber(), pin);
        } else {
            return "Invalid request. Please start again.\n" + buildMainMenu();
        }
    }

    private String validateTicketNumber(String ticketNumber) {
        if (ticketNumber == null || ticketNumber.trim().isEmpty()) {
            return "Invalid ticket number. Please try again.";
        }
        if (!ticketNumber.matches("^[A-Z]{3,4}-[A-Z]{2,4}-\\d{4}$")) {
            return "Invalid ticket format. Expected: FAC-DEPT-XXXX";
        }
        return null;
    }

    private String buildMainMenu() {
        return "MVURA Health System\n" +
                "1. Check Queue Status\n" +
                "2. Cancel My Ticket\n" +
                "3. Book Appointment\n" +
                "\nChoose option:";
    }

    private String verifyTicket(String ticketNumber, String pin) {
        try {
            Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                    .orElseThrow(() -> new RuntimeException("Ticket not found"));

            String expectedPin = getPinForTicket(ticket);
            if (!expectedPin.equals(pin)) {
                auditService.logSecurityEvent(
                        "USSD_INVALID_PIN",
                        ticket.getPatient() != null ? ticket.getPatient().getUsername() : "anonymous",
                        null,
                        null,
                        "Ticket: " + ticketNumber
                );
                return "Invalid PIN. Please try again or visit reception.";
            }

            if (!ticket.isActive() ||
                    ticket.getStatus() == TicketStatus.DISCHARGED ||
                    ticket.getStatus() == TicketStatus.CANCELLED) {
                return "Ticket is no longer active. Please visit reception.";
            }

            String status = ticket.getStatus().toString().replace('_', ' ');
            return "Ticket: " + ticketNumber + "\n" +
                    "Status: " + status + "\n" +
                    "Priority: " + ticket.getPriority() + "\n" +
                    "Position: #" + ticket.getQueuePosition() + "\n" +
                    "Est. Wait: " + ticket.getEstimatedWaitMinutes() + " mins";

        } catch (Exception e) {
            log.error("USSD verification error: {}", e.getMessage());
            return "Ticket not found. Please check and try again.";
        }
    }

    private String cancelTicket(String ticketNumber, String pin) {
        try {
            Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                    .orElseThrow(() -> new RuntimeException("Ticket not found"));

            String expectedPin = getPinForTicket(ticket);
            if (!expectedPin.equals(pin)) {
                auditService.logSecurityEvent(
                        "USSD_INVALID_PIN_CANCEL",
                        ticket.getPatient() != null ? ticket.getPatient().getUsername() : "anonymous",
                        null,
                        null,
                        "Ticket: " + ticketNumber
                );
                return "Invalid PIN. Please try again.";
            }

            ticket.setActive(false);
            ticket.setStatus(TicketStatus.CANCELLED);
            ticketRepository.save(ticket);

            auditService.logSecurityEvent(
                    "USSD_TICKET_CANCELLED",
                    ticket.getPatient() != null ? ticket.getPatient().getUsername() : "anonymous",
                    null,
                    null,
                    "Ticket: " + ticketNumber
            );

            return "Ticket " + ticketNumber + " has been cancelled.";

        } catch (Exception e) {
            log.error("USSD cancellation error: {}", e.getMessage());
            return "Failed to cancel ticket. Please visit reception.";
        }
    }

    private String getPinForTicket(Ticket ticket) {
        String raw = ticket.getId().toString() + ticket.getCreatedAt().toString();
        return String.format("%04d", Math.abs(raw.hashCode() % 10000));
    }

    private String buildUssdResponse(String message, boolean endSession) {
        return (endSession ? "END " : "CON ") + message;
    }

    public void sendSmsNotification(String phoneNumber, String message) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("username", username);
            request.put("to", phoneNumber);
            request.put("message", message);
            request.put("from", shortCode);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

            auditService.logSecurityEvent(
                    "SMS_NOTIFICATION_SENT",
                    "system",
                    null,
                    null,
                    "Phone: " + phoneNumber
            );

            log.info("SMS notification sent to {}: {}", phoneNumber, message);

        } catch (Exception e) {
            log.error("Failed to send SMS notification: {}", e.getMessage());
        }
    }

    @lombok.Data
    private static class UssdSession {
        private String phoneNumber;
        private String step;
        private String ticketNumber;
        private LocalDateTime lastActivity;
    }
}