package com.mvura.service;

import com.mvura.model.Facility;
import com.mvura.model.Ticket;
import com.mvura.model.User;
import com.mvura.repository.DepartmentRepository;
import com.mvura.repository.FacilityRepository;
import com.mvura.repository.TicketRepository;
import com.mvura.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseService {

    private final TicketRepository ticketRepository;
    private final DepartmentRepository departmentRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;

    // ==================== CONNECTION STORES ====================

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, Map<String, SseEmitter>> facilityEmitters = new ConcurrentHashMap<>();

    // ==================== EVENT HISTORY (for replay) ====================

    private final Map<String, List<SseEvent>> eventHistory = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_SIZE = 100;
    private static final long HISTORY_TTL_MINUTES = 60;

    // ==================== CONNECTION METRICS ====================

    private final Map<String, ConnectionInfo> connectionInfo = new ConcurrentHashMap<>();

    // ==================== CREATE EMITTER ====================

    public SseEmitter createEmitter(String ticketNumber) {
        SseEmitter emitter = new SseEmitter(30_000L);

        String userId = getUserIdFromTicket(ticketNumber);
        connectionInfo.put(ticketNumber, ConnectionInfo.builder()
                .ticketNumber(ticketNumber)
                .userId(userId)
                .connectedAt(LocalDateTime.now())
                .type("TICKET")
                .build());

        emitter.onCompletion(() -> {
            log.debug("SSE connection completed for ticket: {}", ticketNumber);
            emitters.remove(ticketNumber);
            connectionInfo.remove(ticketNumber);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE connection timed out for ticket: {}", ticketNumber);
            emitters.remove(ticketNumber);
            connectionInfo.remove(ticketNumber);
        });

        emitter.onError((e) -> {
            log.error("SSE error for ticket: {}", ticketNumber, e);
            emitters.remove(ticketNumber);
            connectionInfo.remove(ticketNumber);
        });

        emitters.put(ticketNumber, emitter);
        log.info("SSE connection established for ticket: {}", ticketNumber);

        // Send initial status immediately
        sendInitialStatus(ticketNumber, emitter);

        // Replay history if available
        replayHistory(ticketNumber, emitter);

        return emitter;
    }

    public SseEmitter createFacilityEmitter(String facilityId, String departmentId, String clientId) {
        String key = facilityId + ":" + departmentId;
        SseEmitter emitter = new SseEmitter(30_000L);

        connectionInfo.put(clientId, ConnectionInfo.builder()
                .clientId(clientId)
                .facilityId(facilityId)
                .departmentId(departmentId)
                .connectedAt(LocalDateTime.now())
                .type("FACILITY")
                .build());

        emitter.onCompletion(() -> {
            Map<String, SseEmitter> emitters = facilityEmitters.get(key);
            if (emitters != null) {
                emitters.remove(clientId);
                if (emitters.isEmpty()) {
                    facilityEmitters.remove(key);
                }
            }
            connectionInfo.remove(clientId);
        });

        emitter.onTimeout(() -> {
            Map<String, SseEmitter> emitters = facilityEmitters.get(key);
            if (emitters != null) {
                emitters.remove(clientId);
                if (emitters.isEmpty()) {
                    facilityEmitters.remove(key);
                }
            }
            connectionInfo.remove(clientId);
        });

        facilityEmitters.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                .put(clientId, emitter);

        log.info("SSE facility connection established for: {}", key);
        return emitter;
    }

    // ==================== AUTHENTICATED CONNECTIONS ====================

    public SseEmitter createAuthenticatedEmitter(String ticketNumber, String userId) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        if (!ticket.getPatient().getId().toString().equals(userId)) {
            throw new AccessDeniedException("User does not own this ticket");
        }

        return createEmitter(ticketNumber);
    }

    public SseEmitter createAuthenticatedFacilityEmitter(String facilityId, String departmentId,
                                                         String clientId, String userId) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean hasAccess = user.getFacilities().stream()
                .anyMatch(f -> f.getId().toString().equals(facilityId));

        if (!hasAccess) {
            throw new AccessDeniedException("User does not have access to this facility");
        }

        return createFacilityEmitter(facilityId, departmentId, clientId);
    }

    // ==================== SEND INITIAL STATUS ====================

    private void sendInitialStatus(String ticketNumber, SseEmitter emitter) {
        try {
            Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber).orElse(null);
            if (ticket == null) {
                log.warn("Ticket not found for initial status: {}", ticketNumber);
                return;
            }

            // Get queue position and wait time with null safety
            int queuePosition = ticket.getQueuePosition() != null ? ticket.getQueuePosition() : 0;
            int waitTime = ticket.getEstimatedWaitMinutes() != null ? ticket.getEstimatedWaitMinutes() : 0;

            // ===== GENERATE PATIENT-FRIENDLY MESSAGE =====
            String positionMessage;
            if (queuePosition == 1) {
                positionMessage = "🎯 You are next in line! Please be ready for your consultation. " +
                        "Make sure you're at the hospital or within 5 minutes away.";
            } else if (queuePosition <= 3) {
                positionMessage = "📋 You are #" + queuePosition + " in line. " +
                        "Estimated wait: " + waitTime + " minutes. " +
                        "Please stay nearby.";
            } else if (queuePosition <= 6) {
                positionMessage = "📋 You are #" + queuePosition + " in line. " +
                        "Estimated wait: " + waitTime + " minutes. " +
                        "You have time to relax, get a drink, or read a book.";
            } else {
                positionMessage = "📋 You are #" + queuePosition + " in line. " +
                        "Estimated wait: " + waitTime + " minutes. " +
                        "You have time to relax, visit the cafeteria, or check your phone. " +
                        "You'll get a notification when it's your turn.";
            }

            Map<String, Object> data = new HashMap<>();
            data.put("ticketNumber", ticket.getTicketNumber());
            data.put("status", ticket.getStatus() != null ? ticket.getStatus().name() : "CHECKED_IN");
            data.put("priority", ticket.getPriority() != null ? ticket.getPriority().name() : "MEDIUM");
            data.put("queuePosition", queuePosition);
            data.put("estimatedWaitMinutes", waitTime);
            data.put("timestamp", LocalDateTime.now().toString());

            // ===== NEW: Patient-friendly fields =====
            data.put("message", positionMessage);
            data.put("isFirstInLine", queuePosition == 1);
            data.put("isNearFront", queuePosition <= 3);
            data.put("hasLongWait", queuePosition > 6);

            // These can be null - that's fine
            data.put("triageScore", ticket.getTriageScore());
            data.put("triageMethod", ticket.getTriageMethod());
            data.put("aiConfidence", ticket.getAiConfidence());
            data.put("isBooked", ticket.isBooked());

            // Safe patient info
            if (ticket.getPatient() != null) {
                data.put("patientName", ticket.getPatient().getFirstName() + " " + ticket.getPatient().getLastName());
                data.put("patientId", ticket.getPatient().getId().toString());
            } else {
                data.put("patientName", "Unknown");
                data.put("patientId", null);
            }

            // Safe facility info
            if (ticket.getFacility() != null) {
                data.put("facilityName", ticket.getFacility().getName());
                data.put("facilityId", ticket.getFacility().getId().toString());
            } else {
                data.put("facilityName", "Unknown");
                data.put("facilityId", null);
            }

            // Safe department info
            if (ticket.getDepartment() != null) {
                data.put("departmentName", ticket.getDepartment().getName());
                data.put("departmentId", ticket.getDepartment().getId().toString());
            } else {
                data.put("departmentName", "Unknown");
                data.put("departmentId", null);
            }

            // Safe doctor info
            if (ticket.getAssignedDoctor() != null) {
                data.put("doctorName", "Dr. " + ticket.getAssignedDoctor().getFirstName() + " " + ticket.getAssignedDoctor().getLastName());
                data.put("doctorId", ticket.getAssignedDoctor().getId().toString());
            } else {
                data.put("doctorName", "Not assigned");
                data.put("doctorId", null);
            }

            // Timestamps
            data.put("checkedInAt", ticket.getCheckedInAt());
            data.put("triagedAt", ticket.getTriagedAt());
            data.put("updatedAt", ticket.getUpdatedAt());

            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(data));

            log.info("✅ Initial status sent for ticket: {} - Position: {}, Wait: {} min, Message: {}",
                    ticketNumber, queuePosition, waitTime, positionMessage);

        } catch (IOException e) {
            log.error("Failed to send initial status for ticket: {}", ticketNumber, e);
            emitters.remove(ticketNumber);
            connectionInfo.remove(ticketNumber);
        } catch (Exception e) {
            log.error("Unexpected error sending initial status for ticket: {}", ticketNumber, e);
        }
    }

    // ==================== SEND UPDATES ====================

    public void sendTicketUpdate(Ticket ticket) {
        String ticketNumber = ticket.getTicketNumber();

        // Get queue position and wait time with null safety
        int queuePosition = ticket.getQueuePosition() != null ? ticket.getQueuePosition() : 0;
        int waitTime = ticket.getEstimatedWaitMinutes() != null ? ticket.getEstimatedWaitMinutes() : 0;

        // ===== GENERATE PATIENT-FRIENDLY MESSAGE =====
        String positionMessage;
        if (queuePosition == 1) {
            positionMessage = "🎯 You are next in line! Please be ready for your consultation. " +
                    "Make sure you're at the hospital or within 5 minutes away.";
        } else if (queuePosition <= 3) {
            positionMessage = "📋 You are #" + queuePosition + " in line. " +
                    "Estimated wait: " + waitTime + " minutes. " +
                    "Please stay nearby.";
        } else if (queuePosition <= 6) {
            positionMessage = "📋 You are #" + queuePosition + " in line. " +
                    "Estimated wait: " + waitTime + " minutes. " +
                    "You have time to relax, get a drink, or read a book.";
        } else {
            positionMessage = "📋 You are #" + queuePosition + " in line. " +
                    "Estimated wait: " + waitTime + " minutes. " +
                    "You have time to relax, visit the cafeteria, or check your phone. " +
                    "You'll get a notification when it's your turn.";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("ticketNumber", ticketNumber);
        data.put("status", ticket.getStatus() != null ? ticket.getStatus().name() : "CHECKED_IN");
        data.put("priority", ticket.getPriority() != null ? ticket.getPriority().name() : "MEDIUM");
        data.put("queuePosition", queuePosition);
        data.put("estimatedWaitMinutes", waitTime);
        data.put("timestamp", LocalDateTime.now().toString());

        // ===== NEW: Patient-friendly fields =====
        data.put("message", positionMessage);
        data.put("isFirstInLine", queuePosition == 1);
        data.put("isNearFront", queuePosition <= 3);
        data.put("hasLongWait", queuePosition > 6);

        // These can be null - that's fine
        data.put("triageScore", ticket.getTriageScore());
        data.put("triageMethod", ticket.getTriageMethod());
        data.put("aiConfidence", ticket.getAiConfidence());

        // Safe patient info
        if (ticket.getPatient() != null) {
            data.put("patientName", ticket.getPatient().getFirstName() + " " + ticket.getPatient().getLastName());
            data.put("patientId", ticket.getPatient().getId().toString());
        } else {
            data.put("patientName", "Unknown");
            data.put("patientId", null);
        }

        // Safe facility info
        if (ticket.getFacility() != null) {
            data.put("facilityName", ticket.getFacility().getName());
            data.put("facilityId", ticket.getFacility().getId().toString());
        } else {
            data.put("facilityName", "Unknown");
            data.put("facilityId", null);
        }

        // Safe department info
        if (ticket.getDepartment() != null) {
            data.put("departmentName", ticket.getDepartment().getName());
            data.put("departmentId", ticket.getDepartment().getId().toString());
        } else {
            data.put("departmentName", "Unknown");
            data.put("departmentId", null);
        }

        // Safe doctor info
        if (ticket.getAssignedDoctor() != null) {
            data.put("doctorName", "Dr. " + ticket.getAssignedDoctor().getFirstName() + " " + ticket.getAssignedDoctor().getLastName());
            data.put("doctorId", ticket.getAssignedDoctor().getId().toString());
        } else {
            data.put("doctorName", "Not assigned");
            data.put("doctorId", null);
        }

        // Timestamps
        data.put("checkedInAt", ticket.getCheckedInAt());
        data.put("triagedAt", ticket.getTriagedAt());
        data.put("updatedAt", ticket.getUpdatedAt());
        data.put("isBooked", ticket.isBooked());

        SseEvent event = SseEvent.builder()
                .type("ticket-update")
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();

        // Send to the specific ticket emitter
        SseEmitter emitter = emitters.get(ticketNumber);
        if (emitter != null) {
            sendWithRetry(emitter, event, 3);
        }

        // Store in history
        storeHistory(ticketNumber, event);

        // Broadcast to facility watchers
        broadcastToFacility(ticket);
    }

    public void sendEmergencyAlert(String facilityId, String departmentId, Ticket ticket, String message) {
        Map<String, Boolean> options = new HashMap<>();
        options.put("wait", true);
        options.put("internalTransfer", hasOtherAvailableDoctors(ticket));
        options.put("externalTransfer", hasAvailableExternalFacilities(ticket));

        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        payload.put("ticketNumber", ticket.getTicketNumber());
        payload.put("priority", ticket.getPriority() != null ? ticket.getPriority().name() : "MEDIUM");
        payload.put("options", options);
        payload.put("timestamp", LocalDateTime.now().toString());

        // Safe patient info
        if (ticket.getPatient() != null) {
            payload.put("patientName", ticket.getPatient().getFirstName() + " " + ticket.getPatient().getLastName());
        } else {
            payload.put("patientName", "Unknown");
        }

        SseEvent event = SseEvent.builder()
                .type("emergency-alert")
                .timestamp(LocalDateTime.now())
                .data(payload)
                .build();

        // Store in history for this ticket
        storeHistory(ticket.getTicketNumber(), event);

        // Send to the patient's ticket
        SseEmitter ticketEmitter = emitters.get(ticket.getTicketNumber());
        if (ticketEmitter != null) {
            sendWithRetry(ticketEmitter, event, 3);
        }

        // Broadcast to department watchers
        String key = facilityId + ":" + departmentId;
        Map<String, SseEmitter> deptEmitters = facilityEmitters.get(key);
        if (deptEmitters != null) {
            deptEmitters.forEach((clientId, emitter) -> {
                sendWithRetry(emitter, event, 3);
            });
        }

        log.info("Emergency alert sent for ticket: {}", ticket.getTicketNumber());
    }

    public void broadcastToFacility(Ticket ticket) {
        UUID facilityId = ticket.getFacility().getId();
        UUID departmentId = ticket.getDepartment().getId();
        String key = facilityId + ":" + departmentId;

        Map<String, SseEmitter> emitters = facilityEmitters.get(key);
        if (emitters != null) {
            int queueSize = getQueueSize(facilityId, departmentId);

            Map<String, Object> updatedTicket = new HashMap<>();
            updatedTicket.put("ticketNumber", ticket.getTicketNumber());
            updatedTicket.put("priority", ticket.getPriority() != null ? ticket.getPriority().name() : "MEDIUM");
            updatedTicket.put("status", ticket.getStatus() != null ? ticket.getStatus().name() : "CHECKED_IN");
            updatedTicket.put("queuePosition", ticket.getQueuePosition() != null ? ticket.getQueuePosition() : 0);

            Map<String, Object> payload = new HashMap<>();
            payload.put("facilityId", facilityId);
            payload.put("departmentId", departmentId);
            payload.put("queueSize", queueSize);
            payload.put("updatedTicket", updatedTicket);
            payload.put("timestamp", LocalDateTime.now().toString());

            SseEvent event = SseEvent.builder()
                    .type("queue-update")
                    .timestamp(LocalDateTime.now())
                    .data(payload)
                    .build();

            emitters.forEach((clientId, emitter) -> {
                sendWithRetry(emitter, event, 3);
            });
        }
    }

    public void sendDoctorNotification(UUID doctorId, String message) {
        // Find all active tickets assigned to this doctor
        List<Ticket> doctorTickets = ticketRepository.findTicketsForDoctor(doctorId);

        for (Ticket ticket : doctorTickets) {
            Map<String, Object> data = new HashMap<>();
            data.put("message", message);
            data.put("ticketNumber", ticket.getTicketNumber());
            data.put("timestamp", LocalDateTime.now().toString());

            SseEvent event = SseEvent.builder()
                    .type("doctor-notification")
                    .timestamp(LocalDateTime.now())
                    .data(data)
                    .build();

            SseEmitter emitter = emitters.get(ticket.getTicketNumber());
            if (emitter != null) {
                sendWithRetry(emitter, event, 3);
            }
        }

        log.info("Doctor notification sent to {} for {} tickets", doctorId, doctorTickets.size());
    }

    // ==================== EVENT HISTORY (REPLAY) ====================

    private void storeHistory(String ticketNumber, SseEvent event) {
        eventHistory.computeIfAbsent(ticketNumber, k -> new ArrayList<>())
                .add(event);

        // Limit history size
        List<SseEvent> history = eventHistory.get(ticketNumber);
        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }

        // Remove old history periodically
        cleanOldHistory();
    }

    private void replayHistory(String ticketNumber, SseEmitter emitter) {
        List<SseEvent> history = eventHistory.get(ticketNumber);
        if (history != null && !history.isEmpty()) {
            try {
                for (SseEvent event : history) {
                    emitter.send(SseEmitter.event()
                            .name(event.getType())
                            .data(event.getData()));
                }
                log.info("Replayed {} events for ticket: {}", history.size(), ticketNumber);
            } catch (IOException e) {
                log.warn("Failed to replay history for ticket: {}", ticketNumber, e);
            }
        }
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanOldHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(HISTORY_TTL_MINUTES);
        eventHistory.forEach((ticketNumber, events) -> {
            events.removeIf(event -> event.getTimestamp().isBefore(cutoff));
            if (events.isEmpty()) {
                eventHistory.remove(ticketNumber);
            }
        });
    }

    // ==================== HEARTBEAT ====================

    @Scheduled(fixedRate = 25000) // Every 25 seconds
    public void sendHeartbeat() {
        // Send to ticket emitters
        emitters.forEach((ticketNumber, emitter) -> {
            try {
                Map<String, Object> heartbeatData = new HashMap<>();
                heartbeatData.put("timestamp", LocalDateTime.now().toString());

                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data(heartbeatData));
            } catch (IOException e) {
                log.debug("Heartbeat failed for ticket: {}", ticketNumber);
                emitters.remove(ticketNumber);
                connectionInfo.remove(ticketNumber);
            }
        });

        // Send to facility emitters
        facilityEmitters.forEach((key, emitterMap) -> {
            emitterMap.forEach((clientId, emitter) -> {
                try {
                    Map<String, Object> heartbeatData = new HashMap<>();
                    heartbeatData.put("timestamp", LocalDateTime.now().toString());

                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data(heartbeatData));
                } catch (IOException e) {
                    log.debug("Heartbeat failed for facility client: {}", clientId);
                    emitterMap.remove(clientId);
                    connectionInfo.remove(clientId);
                }
            });
        });
    }

    // ==================== STALE CONNECTION CLEANUP ====================

    @Scheduled(fixedRate = 60000) // Every minute
    public void cleanupStaleEmitters() {
        // Clean ticket emitters
        emitters.entrySet().removeIf(entry -> {
            SseEmitter emitter = entry.getValue();
            try {
                Map<String, String> pingData = new HashMap<>();
                pingData.put("ping", "ping");

                emitter.send(SseEmitter.event().name("ping").data(pingData));
                return false;
            } catch (Exception e) {
                log.debug("Removing stale emitter for: {}", entry.getKey());
                connectionInfo.remove(entry.getKey());
                return true;
            }
        });

        // Clean facility emitters
        facilityEmitters.forEach((key, emitterMap) -> {
            emitterMap.entrySet().removeIf(entry -> {
                SseEmitter emitter = entry.getValue();
                try {
                    Map<String, String> pingData = new HashMap<>();
                    pingData.put("ping", "ping");

                    emitter.send(SseEmitter.event().name("ping").data(pingData));
                    return false;
                } catch (Exception e) {
                    log.debug("Removing stale facility emitter for: {}", entry.getKey());
                    connectionInfo.remove(entry.getKey());
                    return true;
                }
            });
        });
    }

    // ==================== METRICS ====================

    public Map<String, Object> getMetrics() {
        int facilityConnections = facilityEmitters.values().stream()
                .mapToInt(Map::size)
                .sum();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("ticketConnections", emitters.size());
        metrics.put("facilityConnections", facilityConnections);
        metrics.put("totalConnections", emitters.size() + facilityConnections);
        metrics.put("facilityChannels", facilityEmitters.size());
        metrics.put("eventHistorySize", eventHistory.size());
        metrics.put("activeUsers", connectionInfo.size());

        // Facility channel details
        Map<String, Integer> channelDetails = facilityEmitters.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().size()
                ));
        metrics.put("channelDetails", channelDetails);

        // Connection timestamps - null-safe
        List<Map<String, Object>> connections = connectionInfo.values().stream()
                .map(info -> {
                    Map<String, Object> conn = new HashMap<>();
                    conn.put("id", info.getTicketNumber() != null ? info.getTicketNumber() : info.getClientId());
                    conn.put("type", info.getType());
                    conn.put("connectedAt", info.getConnectedAt());
                    return conn;
                })
                .collect(Collectors.toList());
        metrics.put("connections", connections);

        metrics.put("timestamp", LocalDateTime.now().toString());

        return metrics;
    }

    public Map<String, Object> getTicketMetrics(String ticketNumber) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("hasActiveConnection", emitters.containsKey(ticketNumber));
        metrics.put("historySize", eventHistory.getOrDefault(ticketNumber, List.of()).size());

        if (connectionInfo.containsKey(ticketNumber)) {
            ConnectionInfo info = connectionInfo.get(ticketNumber);
            metrics.put("connectedAt", info.getConnectedAt());
            metrics.put("type", info.getType());
        }

        return metrics;
    }

    // ==================== BROADCAST TO ALL ====================

    public void broadcastToAll(String eventName, Object data) {
        SseEvent event = SseEvent.builder()
                .type(eventName)
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();

        // Send to all ticket emitters
        emitters.forEach((ticketNumber, emitter) -> {
            sendWithRetry(emitter, event, 2);
        });

        // Send to all facility emitters
        facilityEmitters.forEach((key, emitterMap) -> {
            emitterMap.forEach((clientId, emitter) -> {
                sendWithRetry(emitter, event, 2);
            });
        });

        log.info("Broadcast event '{}' to all {} connections",
                eventName, emitters.size() + facilityEmitters.values().stream().mapToInt(Map::size).sum());
    }

    // ==================== REMOVE CONNECTION ====================

    public void removeEmitter(String ticketNumber) {
        emitters.remove(ticketNumber);
        connectionInfo.remove(ticketNumber);
    }

    public void removeFacilityEmitter(String facilityId, String departmentId, String clientId) {
        String key = facilityId + ":" + departmentId;
        Map<String, SseEmitter> emitters = facilityEmitters.get(key);
        if (emitters != null) {
            emitters.remove(clientId);
            connectionInfo.remove(clientId);
            if (emitters.isEmpty()) {
                facilityEmitters.remove(key);
            }
        }
    }

    // ==================== HELPER METHODS ====================

    private void sendWithRetry(SseEmitter emitter, SseEvent event, int maxRetries) {
        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.getType())
                        .data(event.getData()));
                return;
            } catch (IOException e) {
                attempts++;
                if (attempts >= maxRetries) {
                    log.debug("Failed to send event after {} attempts", maxRetries);
                    emitters.values().remove(emitter);
                }
                try {
                    Thread.sleep(100 * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private String getUserIdFromTicket(String ticketNumber) {
        try {
            Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber).orElse(null);
            if (ticket != null && ticket.getPatient() != null) {
                return ticket.getPatient().getId().toString();
            }
        } catch (Exception e) {
            log.warn("Could not get user ID for ticket: {}", ticketNumber);
        }
        return null;
    }

    private int getQueueSize(UUID facilityId, UUID departmentId) {
        try {
            return ticketRepository.countActiveTickets(facilityId, departmentId);
        } catch (Exception e) {
            log.warn("Could not compute queue size for {}:{}", facilityId, departmentId, e);
            return 0;
        }
    }

    private boolean hasOtherAvailableDoctors(Ticket ticket) {
        UUID currentDoctorId = ticket.getAssignedDoctor() != null ? ticket.getAssignedDoctor().getId() : null;
        List<User> doctors = departmentRepository.findAvailableDoctorsByDepartment(ticket.getDepartment().getId());
        return doctors.stream().anyMatch(doc -> currentDoctorId == null || !doc.getId().equals(currentDoctorId));
    }

    private boolean hasAvailableExternalFacilities(Ticket ticket) {
        List<Facility> facilities = facilityRepository.findAll();
        return facilities.stream()
                .filter(f -> !f.getId().equals(ticket.getFacility().getId()))
                .filter(Facility::isActive)
                .anyMatch(f -> {
                    List<com.mvura.model.Department> depts = departmentRepository.findActiveByFacility(f.getId());
                    return depts.stream().anyMatch(d -> d.getCode().equals(ticket.getDepartment().getCode()));
                });
    }

    // ==================== INNER CLASSES ====================

    @lombok.Data
    @lombok.Builder
    public static class SseEvent {
        private String type;
        private LocalDateTime timestamp;
        private Object data;
    }

    @lombok.Data
    @lombok.Builder
    public static class ConnectionInfo {
        private String ticketNumber;
        private String clientId;
        private String userId;
        private String facilityId;
        private String departmentId;
        private LocalDateTime connectedAt;
        private String type; // "TICKET" or "FACILITY"
    }

    // ==================== EVENT TYPES ====================

    public enum SseEventType {
        TICKET_UPDATE("ticket-update"),
        QUEUE_UPDATE("queue-update"),
        EMERGENCY_ALERT("emergency-alert"),
        HEARTBEAT("heartbeat"),
        DOCTOR_NOTIFICATION("doctor-notification"),
        LAB_RESULT("lab-result"),
        BILLING_UPDATE("billing-update"),
        SYSTEM_ANNOUNCEMENT("system-announcement");

        private final String eventName;

        SseEventType(String eventName) {
            this.eventName = eventName;
        }

        public String getEventName() {
            return eventName;
        }
    }
}