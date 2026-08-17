package com.mvura.controller;

import com.mvura.model.Ticket;
import com.mvura.model.User;
import com.mvura.model.UserRole;
import com.mvura.repository.TicketRepository;
import com.mvura.repository.UserRepository;
import com.mvura.security.JwtUtils;
import com.mvura.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
@Slf4j
public class SseController {

    private final SseService sseService;
    private final JwtUtils jwtUtils;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    private static final Set<UserRole> STAFF_ROLES = Set.of(
            UserRole.DOCTOR, UserRole.STAFF, UserRole.FACILITY_ADMIN, UserRole.DISTRICT_ADMIN
    );

    // ===== FIX: Return SseEmitter directly, NOT ResponseEntity =====
    @GetMapping(value = "/ticket/{ticketNumber}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToTicket(
            @PathVariable String ticketNumber,
            @RequestParam String token) {

        log.info("SSE subscription for ticket: {}", ticketNumber);

        // ===== 1. VALIDATE TOKEN =====
        if (token == null || token.isEmpty()) {
            log.warn("❌ No token provided for SSE connection");
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("Authentication required"));
                errorEmitter.complete();
            } catch (Exception e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        String username;
        try {
            username = jwtUtils.extractUsername(token);
        } catch (Exception e) {
            log.warn("❌ Invalid token: {}", e.getMessage());
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("Invalid token"));
                errorEmitter.complete();
            } catch (Exception ex) {
                errorEmitter.completeWithError(ex);
            }
            return errorEmitter;
        }

        if (username == null) {
            log.warn("❌ Could not extract username from token");
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("Invalid token"));
                errorEmitter.complete();
            } catch (Exception e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        // ===== 2. CHECK TOKEN EXPIRATION =====
        if (jwtUtils.isTokenExpired(token)) {
            log.warn("❌ Token expired for user: {}", username);
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("Token expired"));
                errorEmitter.complete();
            } catch (Exception e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        // ===== 3. GET USER =====
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("👤 User {} connecting to SSE", username);

        // ===== 4. GET TICKET =====
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElse(null);

        if (ticket == null) {
            log.warn("❌ Ticket not found: {}", ticketNumber);
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("Ticket not found"));
                errorEmitter.complete();
            } catch (Exception e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        // ===== 5. CHECK AUTHORIZATION =====
        boolean isOwner = ticket.getPatient() != null &&
                ticket.getPatient().getId().equals(user.getId());
        boolean isStaff = STAFF_ROLES.contains(user.getRole());

        if (!isOwner && !isStaff) {
            log.warn("❌ User {} not authorized to view ticket {}", username, ticketNumber);
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("Not authorized to view this ticket"));
                errorEmitter.complete();
            } catch (Exception e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        // ===== 6. CREATE SSE CONNECTION =====
        log.info("✅ SSE connection authorized for user: {} to ticket: {}", username, ticketNumber);
        return sseService.createEmitter(ticketNumber);
    }

    // ===== FIX: Return SseEmitter directly for queue subscription =====
    @GetMapping(value = "/queue/{facilityId}/{departmentId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToQueue(
            @PathVariable UUID facilityId,
            @PathVariable UUID departmentId,
            @RequestParam String clientId,
            @RequestParam String token) {

        log.info("SSE subscription for facility: {}, department: {}, client: {}",
                facilityId, departmentId, clientId);

        // ===== 1. VALIDATE TOKEN =====
        if (token == null || token.isEmpty()) {
            log.warn("❌ No token provided for SSE queue connection");
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("Authentication required"));
                errorEmitter.complete();
            } catch (Exception e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        String username;
        try {
            username = jwtUtils.extractUsername(token);
        } catch (Exception e) {
            log.warn("❌ Invalid token: {}", e.getMessage());
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("Invalid token"));
                errorEmitter.complete();
            } catch (Exception ex) {
                errorEmitter.completeWithError(ex);
            }
            return errorEmitter;
        }

        if (username == null) {
            log.warn("❌ Could not extract username from token");
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("Invalid token"));
                errorEmitter.complete();
            } catch (Exception e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        // ===== 2. CHECK TOKEN EXPIRATION =====
        if (jwtUtils.isTokenExpired(token)) {
            log.warn("❌ Token expired for user: {}", username);
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("Token expired"));
                errorEmitter.complete();
            } catch (Exception e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        // ===== 3. GET USER =====
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("👤 User {} connecting to queue SSE", username);

        // ===== 4. CHECK AUTHORIZATION (Only staff can view queues) =====
        if (!STAFF_ROLES.contains(user.getRole())) {
            log.warn("❌ User {} not authorized to view queue", username);
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("Not authorized to view queue"));
                errorEmitter.complete();
            } catch (Exception e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        // ===== 5. CREATE SSE CONNECTION =====
        log.info("✅ SSE queue connection authorized for user: {}", username);
        return sseService.createFacilityEmitter(
                facilityId.toString(),
                departmentId.toString(),
                clientId
        );
    }
}