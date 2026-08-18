package com.mvura.controller;

import com.mvura.model.Ticket;
import com.mvura.service.StaffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasAnyRole('STAFF', 'DOCTOR', 'FACILITY_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class StaffController {

    private final StaffService staffService;

    // ==================== HELPER ====================

    private UUID getCurrentUserId(Authentication auth) {
        String username = auth.getName();
        return staffService.getUserIdByUsername(username);
    }

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard/stats")
    public ResponseEntity<?> getDashboardStats(Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("📊 Getting dashboard stats for staff: {}", userId);

        Map<String, Object> stats = staffService.getDashboardStats(userId);
        return ResponseEntity.ok(stats);
    }

    // ==================== QUEUE ====================

    @GetMapping("/queue")
    public ResponseEntity<?> getQueue(Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("📋 Getting queue for staff: {}", userId);

        List<Ticket> queue = staffService.getQueueForStaff(userId);
        return ResponseEntity.ok(Map.of(
                "queue", queue,
                "count", queue.size()
        ));
    }

    // ==================== PATIENTS ====================

    @GetMapping("/patients")
    public ResponseEntity<?> getPatients(Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("👥 Getting patients for staff: {}", userId);

        List<Map<String, Object>> patients = staffService.getPatientsForStaff(userId);
        return ResponseEntity.ok(Map.of(
                "patients", patients,
                "count", patients.size()
        ));
    }

    @GetMapping("/patients/{patientId}")
    public ResponseEntity<?> getPatientDetails(@PathVariable UUID patientId, Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("🔍 Getting patient details: {}", patientId);

        Map<String, Object> patient = staffService.getPatientDetails(patientId, userId);
        return ResponseEntity.ok(patient);
    }

    // ==================== TICKETS ====================

    @GetMapping("/tickets/{ticketId}")
    public ResponseEntity<?> getTicketDetails(@PathVariable UUID ticketId, Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("🎫 Getting ticket details: {}", ticketId);

        Ticket ticket = staffService.getTicketDetails(ticketId, userId);
        return ResponseEntity.ok(ticket);
    }

    // ==================== BILLING ====================

    @GetMapping("/billing")
    public ResponseEntity<?> getBilling(Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("💰 Getting billing for staff: {}", userId);

        List<Map<String, Object>> billing = staffService.getBillingForStaff(userId);
        return ResponseEntity.ok(Map.of(
                "billing", billing,
                "count", billing.size()
        ));
    }

    @GetMapping("/billing/{billingId}")
    public ResponseEntity<?> getBillingDetails(@PathVariable UUID billingId, Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("📄 Getting billing details: {}", billingId);

        Map<String, Object> billing = staffService.getBillingDetails(billingId, userId);
        return ResponseEntity.ok(billing);
    }

    // ==================== NOTIFICATIONS ====================

    @GetMapping("/notifications")
    public ResponseEntity<?> getNotifications(Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("🔔 Getting notifications for staff: {}", userId);

        List<Map<String, Object>> notifications = staffService.getNotifications(userId);
        return ResponseEntity.ok(Map.of(
                "notifications", notifications,
                "count", notifications.size()
        ));
    }

    @PutMapping("/notifications/{notificationId}/read")
    public ResponseEntity<?> markNotificationRead(@PathVariable String notificationId, Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("📖 Marking notification {} as read for staff: {}", notificationId, userId);

        staffService.markNotificationRead(notificationId, userId);
        return ResponseEntity.ok(Map.of(
                "message", "Notification marked as read",
                "notificationId", notificationId
        ));
    }

    // ==================== CONSULTATION ====================

    @PostMapping("/consultation/start/{ticketId}")
    public ResponseEntity<?> startConsultation(@PathVariable UUID ticketId, Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("👨‍⚕️ Starting consultation for ticket: {} by staff: {}", ticketId, userId);

        Ticket ticket = staffService.startConsultation(ticketId, userId);
        return ResponseEntity.ok(Map.of(
                "message", "Consultation started successfully",
                "ticketId", ticket.getId(),
                "ticketNumber", ticket.getTicketNumber(),
                "status", ticket.getStatus().name()
        ));
    }

    @PostMapping("/consultation/complete/{ticketId}")
    public ResponseEntity<?> completeConsultation(@PathVariable UUID ticketId, Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("✅ Completing consultation for ticket: {} by staff: {}", ticketId, userId);

        Ticket ticket = staffService.completeConsultation(ticketId, userId);
        return ResponseEntity.ok(Map.of(
                "message", "Consultation completed successfully",
                "ticketId", ticket.getId(),
                "ticketNumber", ticket.getTicketNumber(),
                "status", ticket.getStatus().name()
        ));
    }

    // ==================== QUEUE MANAGEMENT ====================

    @PostMapping("/queue/update-positions/{facilityId}/{departmentId}")
    @PreAuthorize("hasRole('FACILITY_ADMIN')")
    public ResponseEntity<?> updateQueuePositions(
            @PathVariable UUID facilityId,
            @PathVariable UUID departmentId,
            Authentication auth) {
        UUID userId = getCurrentUserId(auth);
        log.info("🔄 Updating queue positions for facility: {}, department: {} by: {}", facilityId, departmentId, userId);

        staffService.updateQueuePositions(facilityId, departmentId);
        return ResponseEntity.ok(Map.of(
                "message", "Queue positions updated successfully",
                "facilityId", facilityId,
                "departmentId", departmentId
        ));
    }

    // ==================== HEALTH CHECK ====================

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "service", "Staff API",
                "timestamp", System.currentTimeMillis()
        ));
    }
}