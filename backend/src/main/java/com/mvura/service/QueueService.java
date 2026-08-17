package com.mvura.service;

import com.mvura.model.*;
import com.mvura.repository.ServicePricingRepository;
import com.mvura.repository.TicketRepository;
import com.mvura.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ServicePricingRepository servicePricingRepository;
    private final SseService sseService;
    private final AuditService auditService;
    private final BillingService billingService;
    private final ConsultationService consultationService;
    private final QueuePositionService queuePositionService;

    private String sanitizeForLog(String input) {
        if (input == null) {
            return "";
        }
        String cleaned = input.replaceAll("[\\r\\n\\t]", " ").trim();
        return cleaned.length() > 500 ? cleaned.substring(0, 500) + "...(truncated)" : cleaned;
    }

    private void verifyDoctorOwnsTicket(Ticket ticket, UUID doctorId) {
        User assigned = ticket.getAssignedDoctor();
        if (assigned == null || !assigned.getId().equals(doctorId)) {
            User doctor = userRepository.findById(doctorId).orElse(null);
            auditService.logSecurityEvent(
                    "UNAUTHORIZED_TICKET_ACCESS_ATTEMPT",
                    doctor != null ? doctor.getUsername() : "unknown",
                    doctorId,
                    null,
                    "Ticket: " + ticket.getTicketNumber() + " is not assigned to requesting doctor"
            );
            throw new AccessDeniedException("You are not the assigned doctor for this ticket");
        }
    }

    private void verifyStatus(Ticket ticket, TicketStatus expected) {
        if (ticket.getStatus() != expected) {
            throw new IllegalStateException(
                    "Invalid state transition: ticket " + ticket.getTicketNumber() +
                            " is " + ticket.getStatus() + ", expected " + expected
            );
        }
    }

    @Transactional
    public Ticket startConsultation(UUID ticketId, UUID doctorId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        if (ticket.getStatus() != TicketStatus.TRIAGED && ticket.getStatus() != TicketStatus.LAB_COMPLETED) {
            throw new IllegalStateException(
                    "Cannot start consultation from status: " + ticket.getStatus()
            );
        }

        User currentlyAssigned = ticket.getAssignedDoctor();
        if (currentlyAssigned != null && !currentlyAssigned.getId().equals(doctorId)
                && ticket.getStatus() == TicketStatus.LAB_COMPLETED) {
            verifyDoctorOwnsTicket(ticket, doctorId);
        }

        consultationService.getOrCreateConsultation(ticket, doctor);

        // Clear position before moving to IN_CONSULTATION
        queuePositionService.clearTicketPosition(ticket);

        ticket.setStatus(TicketStatus.IN_CONSULTATION);
        ticket.setAssignedDoctor(doctor);
        ticket.setConsultationStartedAt(LocalDateTime.now());
        ticket.setQueuePosition(0);
        ticket.setEstimatedWaitMinutes(0);

        Ticket updated = ticketRepository.save(ticket);

        // Recalculate queue for remaining waiting tickets
        queuePositionService.recalculateQueue(
                updated.getFacility().getId(),
                updated.getDepartment().getId()
        );

        sseService.sendTicketUpdate(updated);

        auditService.logSecurityEvent(
                "CONSULTATION_STARTED",
                doctor.getUsername(),
                doctor.getId(),
                null,
                "Ticket: " + ticket.getTicketNumber()
        );

        log.info("Consultation started for ticket: {} by doctor: {}", ticket.getTicketNumber(), doctor.getUsername());

        return updated;
    }

    @Transactional
    public Ticket completeConsultation(UUID ticketId, UUID doctorId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        // ===== FIX: Allow both IN_CONSULTATION and LAB_COMPLETED statuses =====
        if (ticket.getStatus() != TicketStatus.IN_CONSULTATION &&
                ticket.getStatus() != TicketStatus.LAB_COMPLETED) {
            throw new IllegalStateException(
                    "Cannot complete consultation: ticket " + ticket.getTicketNumber() +
                            " is " + ticket.getStatus() +
                            ". Must be IN_CONSULTATION or LAB_COMPLETED"
            );
        }
        // ===== END FIX =====

        verifyDoctorOwnsTicket(ticket, doctorId);

        if (ticket.getFacility() == null) {
            throw new IllegalStateException("Cannot generate bill: Ticket missing facility association");
        }
        if (ticket.getPatient() == null) {
            throw new IllegalStateException("Cannot generate bill: Ticket missing patient association");
        }

        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        // ===== GET CONSULTATION BEFORE COMPLETING =====
        Consultation consultation = consultationService.getOrCreateConsultation(ticket, doctor);

        boolean hasLabOrders = consultation.getLabOrders() != null && !consultation.getLabOrders().isEmpty();
        boolean hasLabResults = consultation.getLabResults() != null && !consultation.getLabResults().isEmpty();

        if (hasLabOrders && !hasLabResults) {
            log.warn("⚠️ Lab tests are pending for ticket: {}. Cannot complete consultation without lab results.",
                    ticket.getTicketNumber());
            throw new IllegalStateException(
                    "Cannot complete consultation: Lab tests have been ordered but results are not yet recorded. " +
                            "Please enter lab results before completing the consultation."
            );
        }

        // ===== COMPLETE THE CONSULTATION RECORD =====
        consultationService.completeConsultationRecord(ticket);

        ticket.setStatus(TicketStatus.CONSULTATION_DONE);
        ticket.setConsultationCompletedAt(LocalDateTime.now());

        Ticket updated = ticketRepository.save(ticket);

        // Remove from queue and recalculate
        queuePositionService.removeTicketFromQueue(updated);

        // ===== GENERATE BILL USING THE CONSULTATION WE ALREADY HAVE =====
        log.info("Auto-generating bill for ticket: {}", ticket.getTicketNumber());
        Billing billing = billingService.generateBill(ticketId, "CONSULTATION", consultation);
        log.info("Bill generated: {}, Amount: {}", billing.getInvoiceNumber(), billing.getTotalAmount());

        sseService.sendTicketUpdate(updated);

        auditService.logSecurityEvent(
                "CONSULTATION_COMPLETED",
                ticket.getPatient().getUsername(),
                ticket.getPatient().getId(),
                null,
                "Ticket: " + ticket.getTicketNumber() + ", Bill: " + billing.getInvoiceNumber()
        );

        log.info("Consultation completed for ticket: {}", ticket.getTicketNumber());

        return updated;
    }

    // ===== KEEP EXISTING METHOD FOR BACKWARD COMPATIBILITY =====
    @Transactional
    public Ticket orderLabTest(UUID ticketId, LabTestType testType, UUID doctorId) {
        if (testType == null) {
            throw new IllegalArgumentException("Test type is required");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        verifyStatus(ticket, TicketStatus.IN_CONSULTATION);
        verifyDoctorOwnsTicket(ticket, doctorId);

        consultationService.recordLabOrder(ticket, testType);

        ticket.setStatus(TicketStatus.LAB_PENDING);
        Ticket updated = ticketRepository.save(ticket);

        // Recalculate queue (ticket now has position in waiting queue)
        queuePositionService.recalculateQueue(
                updated.getFacility().getId(),
                updated.getDepartment().getId()
        );

        sseService.sendTicketUpdate(updated);

        User doctor = userRepository.findById(doctorId).orElse(null);
        auditService.logSecurityEvent(
                "LAB_ORDERED",
                doctor != null ? doctor.getUsername() : "unknown",
                doctorId,
                null,
                "Ticket: " + ticket.getTicketNumber() + ", Test: " + testType
        );

        log.info("Lab test ordered for ticket: {}, Test: {}", ticket.getTicketNumber(), testType);
        return updated;
    }

    // ===== NEW METHOD: Order Lab Test with Service Code from Database =====
    @Transactional
    public Ticket orderLabTestWithServiceCode(UUID ticketId, String serviceCode, UUID doctorId) {
        log.info("Ordering lab test for ticket: {} with service code: {}", ticketId, serviceCode);

        if (serviceCode == null || serviceCode.isEmpty()) {
            throw new IllegalArgumentException("Service code is required");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        if (ticket.getStatus() != TicketStatus.IN_CONSULTATION) {
            throw new IllegalStateException(
                    "Cannot order lab test from status: " + ticket.getStatus() +
                            ". Ticket must be IN_CONSULTATION."
            );
        }

        verifyDoctorOwnsTicket(ticket, doctorId);

        ServicePricing pricing = servicePricingRepository.findByServiceCode(serviceCode)
                .filter(service -> "LAB".equalsIgnoreCase(service.getCategory()))
                .filter(ServicePricing::isActive)
                .orElseThrow(() -> new RuntimeException("Lab service not found or inactive: " + serviceCode));

        log.info("✅ Lab service validated: {} - Price: {} RWF", pricing.getServiceName(), pricing.getBasePrice());

        consultationService.recordLabOrder(ticket, serviceCode, pricing);

        ticket.setStatus(TicketStatus.LAB_PENDING);
        Ticket updated = ticketRepository.save(ticket);

        // Recalculate queue (ticket now has position in waiting queue)
        queuePositionService.recalculateQueue(
                updated.getFacility().getId(),
                updated.getDepartment().getId()
        );

        sseService.sendTicketUpdate(updated);

        User doctor = userRepository.findById(doctorId).orElse(null);
        auditService.logSecurityEvent(
                "LAB_ORDERED_WITH_SERVICE_CODE",
                doctor != null ? doctor.getUsername() : "unknown",
                doctorId,
                null,
                "Ticket: " + ticket.getTicketNumber() +
                        ", Service: " + pricing.getServiceName() +
                        " (" + serviceCode + ")" +
                        ", Price: " + pricing.getBasePrice()
        );

        log.info("✅ Lab test ordered for ticket: {}, Service: {} ({})",
                ticket.getTicketNumber(), pricing.getServiceName(), serviceCode);

        return updated;
    }

    @Transactional
    public Ticket completeLabTest(UUID ticketId, String result, UUID requestingUserId) {
        if (result == null || result.isBlank()) {
            throw new IllegalArgumentException("Lab result is required");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        verifyStatus(ticket, TicketStatus.LAB_PENDING);

        String cleanResult = sanitizeForLog(result);

        consultationService.recordLabResult(ticket, cleanResult);

        ticket.setStatus(TicketStatus.LAB_COMPLETED);

        Ticket updated = ticketRepository.save(ticket);

        // Recalculate queue (ticket now has position in waiting queue to see doctor again)
        queuePositionService.recalculateQueue(
                updated.getFacility().getId(),
                updated.getDepartment().getId()
        );

        sseService.sendTicketUpdate(updated);

        User requester = userRepository.findById(requestingUserId).orElse(null);
        auditService.logSecurityEvent(
                "LAB_COMPLETED",
                requester != null ? requester.getUsername() : requestingUserId.toString(),
                requestingUserId,
                null,
                "Ticket: " + ticket.getTicketNumber() + ", Result recorded"
        );

        log.info("Lab test completed for ticket: {}, Result: {}", ticket.getTicketNumber(), cleanResult);

        return updated;
    }

    public List<Ticket> getDoctorQueue(UUID doctorId) {
        return ticketRepository.findTicketsForDoctor(doctorId);
    }

    public List<Ticket> getDepartmentQueue(UUID facilityId, UUID departmentId) {
        if (facilityId == null || departmentId == null) {
            log.warn("facilityId or departmentId is null");
            return List.of();
        }
        return ticketRepository.findAllQueueTicketsForDisplay(facilityId, departmentId);
    }

    public Map<String, Object> getQueueMetrics(UUID facilityId, UUID departmentId) {
        if (facilityId == null || departmentId == null) {
            log.warn("facilityId or departmentId is null, returning empty metrics");
            return Map.of(
                    "total", 0,
                    "emergency", 0,
                    "high", 0,
                    "medium", 0,
                    "low", 0,
                    "averageWaitMinutes", 0
            );
        }

        try {
            List<Ticket> tickets = ticketRepository.findAllQueueTicketsForDisplay(
                    facilityId, departmentId
            );

            long emergency = tickets.stream().filter(t -> t.getPriority() == Priority.EMERGENCY).count();
            long high = tickets.stream().filter(t -> t.getPriority() == Priority.HIGH).count();
            long medium = tickets.stream().filter(t -> t.getPriority() == Priority.MEDIUM).count();
            long low = tickets.stream().filter(t -> t.getPriority() == Priority.LOW).count();

            double avgWait = tickets.stream()
                    .filter(t -> t.getEstimatedWaitMinutes() != null && t.getEstimatedWaitMinutes() > 0)
                    .mapToInt(Ticket::getEstimatedWaitMinutes)
                    .average()
                    .orElse(0);

            return Map.of(
                    "total", tickets.size(),
                    "emergency", emergency,
                    "high", high,
                    "medium", medium,
                    "low", low,
                    "averageWaitMinutes", Math.round(avgWait)
            );
        } catch (Exception e) {
            log.error("Error getting queue metrics: {}", e.getMessage(), e);
            return Map.of(
                    "total", 0,
                    "emergency", 0,
                    "high", 0,
                    "medium", 0,
                    "low", 0,
                    "averageWaitMinutes", 0,
                    "error", e.getMessage()
            );
        }
    }
}