package com.mvura.service;

import com.mvura.model.*;
import com.mvura.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffService {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final DepartmentRepository departmentRepository;
    private final FacilityRepository facilityRepository;
    private final BillingRepository billingRepository;

    // ==================== HELPER METHODS ====================

    public UUID getUserIdByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }

    private User getCurrentUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Department getStaffDepartment(UUID userId) {
        User user = getCurrentUser(userId);
        if (user.getPrimaryDepartment() != null) {
            return user.getPrimaryDepartment();
        }
        if (user.getDepartments() != null && !user.getDepartments().isEmpty()) {
            return user.getDepartments().iterator().next();
        }
        throw new RuntimeException("Staff member has no assigned department");
    }

    private Facility getStaffFacility(UUID userId) {
        User user = getCurrentUser(userId);
        if (user.getPrimaryFacility() != null) {
            return user.getPrimaryFacility();
        }
        if (user.getFacilities() != null && !user.getFacilities().isEmpty()) {
            return user.getFacilities().iterator().next();
        }
        throw new RuntimeException("Staff member has no assigned facility");
    }

    // ==================== DASHBOARD STATS ====================

    public Map<String, Object> getDashboardStats(UUID userId) {
        Department department = getStaffDepartment(userId);
        Facility facility = getStaffFacility(userId);

        int totalPatients = ticketRepository.countActiveTickets(
                facility.getId(), department.getId()
        );
        int waitingPatients = ticketRepository.countActiveQueueTickets(
                facility.getId(), department.getId()
        );
        int inConsultation = ticketRepository.findTicketsForDoctor(userId).size();
        int completedToday = 0;

        // Count completed consultations today
        try {
            List<Ticket> completedTickets = ticketRepository.findActiveTicketsByFacilityAndDepartment(
                    facility.getId(), department.getId()
            );
            completedToday = (int) completedTickets.stream()
                    .filter(t -> t.getStatus() == TicketStatus.CONSULTATION_DONE)
                    .filter(t -> t.getConsultationCompletedAt() != null)
                    .filter(t -> t.getConsultationCompletedAt().toLocalDate().equals(LocalDateTime.now().toLocalDate()))
                    .count();
        } catch (Exception e) {
            log.warn("Could not count completed consultations: {}", e.getMessage());
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPatients", totalPatients);
        stats.put("waitingPatients", waitingPatients);
        stats.put("inConsultation", inConsultation);
        stats.put("completedToday", completedToday);

        return stats;
    }

    // ==================== QUEUE ====================

    public List<Ticket> getQueueForStaff(UUID userId) {
        Department department = getStaffDepartment(userId);
        Facility facility = getStaffFacility(userId);

        return ticketRepository.findActiveQueueTicketsByFacilityAndDepartment(
                facility.getId(), department.getId()
        );
    }

    // ==================== PATIENTS ====================

    public List<Map<String, Object>> getPatientsForStaff(UUID userId) {
        Department department = getStaffDepartment(userId);
        Facility facility = getStaffFacility(userId);

        List<Ticket> tickets = ticketRepository.findActiveTicketsByFacilityAndDepartment(
                facility.getId(), department.getId()
        );

        return tickets.stream().map(ticket -> {
            Map<String, Object> patient = new HashMap<>();
            patient.put("id", ticket.getId());
            patient.put("ticketNumber", ticket.getTicketNumber());
            patient.put("status", ticket.getStatus() != null ? ticket.getStatus().name() : "UNKNOWN");
            patient.put("priority", ticket.getPriority() != null ? ticket.getPriority().name() : "MEDIUM");
            patient.put("queuePosition", ticket.getQueuePosition());
            patient.put("estimatedWaitMinutes", ticket.getEstimatedWaitMinutes());
            patient.put("createdAt", ticket.getCreatedAt());
            patient.put("symptoms", ticket.getSymptoms());

            if (ticket.getPatient() != null) {
                User p = ticket.getPatient();
                patient.put("firstName", p.getFirstName());
                patient.put("lastName", p.getLastName());
                patient.put("email", p.getEmail());
                patient.put("phone", p.getPhone());
                patient.put("age", p.getAge());
                patient.put("gender", p.getGender());
            }

            if (ticket.getAssignedDoctor() != null) {
                patient.put("assignedDoctor", ticket.getAssignedDoctor().getFirstName() + " " +
                        ticket.getAssignedDoctor().getLastName());
            }

            return patient;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getPatientDetails(UUID patientId, UUID userId) {
        Ticket ticket = ticketRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient ticket not found"));

        Map<String, Object> details = new HashMap<>();
        details.put("id", ticket.getId());
        details.put("ticketNumber", ticket.getTicketNumber());
        details.put("status", ticket.getStatus() != null ? ticket.getStatus().name() : "UNKNOWN");
        details.put("priority", ticket.getPriority() != null ? ticket.getPriority().name() : "MEDIUM");
        details.put("queuePosition", ticket.getQueuePosition());
        details.put("estimatedWaitMinutes", ticket.getEstimatedWaitMinutes());
        details.put("symptoms", ticket.getSymptoms());
        details.put("createdAt", ticket.getCreatedAt());
        details.put("checkedInAt", ticket.getCheckedInAt());
        details.put("triagedAt", ticket.getTriagedAt());

        if (ticket.getPatient() != null) {
            User p = ticket.getPatient();
            details.put("firstName", p.getFirstName());
            details.put("lastName", p.getLastName());
            details.put("email", p.getEmail());
            details.put("phone", p.getPhone());
            details.put("age", p.getAge());
            details.put("gender", p.getGender());
            details.put("bloodType", p.getBloodType());
            details.put("allergies", p.getAllergies());
            details.put("chronicConditions", p.getChronicConditions());
        }

        if (ticket.getAssignedDoctor() != null) {
            details.put("assignedDoctor", ticket.getAssignedDoctor().getFirstName() + " " +
                    ticket.getAssignedDoctor().getLastName());
        }

        if (ticket.getFacility() != null) {
            details.put("facilityName", ticket.getFacility().getName());
        }

        if (ticket.getDepartment() != null) {
            details.put("departmentName", ticket.getDepartment().getName());
        }

        return details;
    }

    // ==================== BILLING ====================

    public List<Map<String, Object>> getBillingForStaff(UUID userId) {
        Facility facility = getStaffFacility(userId);

        List<Billing> bills = billingRepository.findByFacilityId(facility.getId());

        if (bills.isEmpty()) {
            return new ArrayList<>();
        }

        return bills.stream().map(bill -> {
            Map<String, Object> billing = new HashMap<>();
            billing.put("id", bill.getId());
            billing.put("invoiceNumber", bill.getInvoiceNumber());
            billing.put("amount", bill.getTotalAmount());
            billing.put("status", bill.getStatus() != null ? bill.getStatus().name() : "PENDING");
            billing.put("issuedAt", bill.getIssuedAt());
            billing.put("dueDate", bill.getDueDate());

            if (bill.getTicket() != null && bill.getTicket().getPatient() != null) {
                billing.put("patientName", bill.getTicket().getPatient().getFirstName() + " " +
                        bill.getTicket().getPatient().getLastName());
            }

            return billing;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getBillingDetails(UUID billingId, UUID userId) {
        Billing bill = billingRepository.findById(billingId)
                .orElseThrow(() -> new RuntimeException("Billing record not found"));

        Map<String, Object> details = new HashMap<>();
        details.put("id", bill.getId());
        details.put("invoiceNumber", bill.getInvoiceNumber());
        details.put("totalAmount", bill.getTotalAmount());
        details.put("patientAmount", bill.getPatientAmount());
        details.put("insuranceAmount", bill.getInsuranceAmount());
        details.put("paidAmount", bill.getPaidAmount());
        details.put("status", bill.getStatus() != null ? bill.getStatus().name() : "PENDING");
        details.put("issuedAt", bill.getIssuedAt());
        details.put("dueDate", bill.getDueDate());
        details.put("paidAt", bill.getPaidAt());
        details.put("paymentMethod", bill.getPaymentMethod());

        if (bill.getTicket() != null) {
            details.put("ticketNumber", bill.getTicket().getTicketNumber());
            if (bill.getTicket().getPatient() != null) {
                details.put("patientName", bill.getTicket().getPatient().getFirstName() + " " +
                        bill.getTicket().getPatient().getLastName());
            }
        }

        return details;
    }

    // ==================== NOTIFICATIONS (Using Ticket Data) ====================

    public List<Map<String, Object>> getNotifications(UUID userId) {
        List<Map<String, Object>> notifications = new ArrayList<>();

        Department department = getStaffDepartment(userId);
        Facility facility = getStaffFacility(userId);

        // Get waiting tickets as notifications
        List<Ticket> waitingTickets = ticketRepository.findActiveQueueTicketsByFacilityAndDepartment(
                facility.getId(), department.getId()
        );

        // Add waiting patients as notifications
        for (Ticket ticket : waitingTickets) {
            Map<String, Object> notif = new HashMap<>();
            notif.put("id", ticket.getId().toString());
            String patientName = ticket.getPatient() != null ?
                    ticket.getPatient().getFirstName() + " " + ticket.getPatient().getLastName() :
                    "Unknown Patient";
            notif.put("message", "👤 " + patientName + " (Ticket #" + ticket.getTicketNumber() + ") is waiting in queue");
            notif.put("read", false);
            notif.put("createdAt", ticket.getCheckedInAt() != null ? ticket.getCheckedInAt() : ticket.getCreatedAt());
            notifications.add(notif);
        }

        // Get in-consultation tickets
        List<Ticket> consultationTickets = ticketRepository.findTicketsForDoctor(userId);
        for (Ticket ticket : consultationTickets) {
            if (ticket.getStatus() == TicketStatus.IN_CONSULTATION) {
                Map<String, Object> notif = new HashMap<>();
                notif.put("id", "consult-" + ticket.getId().toString());
                String patientName = ticket.getPatient() != null ?
                        ticket.getPatient().getFirstName() + " " + ticket.getPatient().getLastName() :
                        "Unknown Patient";
                notif.put("message", "👨‍⚕️ " + patientName + " (Ticket #" + ticket.getTicketNumber() + ") is in consultation");
                notif.put("read", false);
                notif.put("createdAt", ticket.getConsultationStartedAt() != null ?
                        ticket.getConsultationStartedAt() : ticket.getCreatedAt());
                notifications.add(notif);
            }
        }

        // Sort by most recent first
        notifications.sort((a, b) -> {
            LocalDateTime dateA = (LocalDateTime) a.get("createdAt");
            LocalDateTime dateB = (LocalDateTime) b.get("createdAt");
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            return dateB.compareTo(dateA);
        });

        // Limit to top 10
        return notifications.stream().limit(10).collect(Collectors.toList());
    }

    @Transactional
    public void markNotificationRead(String notificationId, UUID userId) {
        // Since we're using virtual notifications from tickets, we don't persist read status.
        // The frontend will remove it from the dropdown after marking as read.
        log.info("Notification {} marked as read by user {}", notificationId, userId);
    }

    // ==================== CONSULTATION ====================

    @Transactional
    public Ticket startConsultation(UUID ticketId, UUID userId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        // Verify staff has access to this ticket
        Department staffDept = getStaffDepartment(userId);
        if (!ticket.getDepartment().getId().equals(staffDept.getId())) {
            throw new RuntimeException("You do not have access to this ticket");
        }

        // Verify ticket is in a valid state for consultation
        if (ticket.getStatus() == TicketStatus.IN_CONSULTATION) {
            throw new RuntimeException("This ticket is already in consultation");
        }
        if (ticket.getStatus() == TicketStatus.CONSULTATION_DONE) {
            throw new RuntimeException("This consultation has already been completed");
        }
        if (ticket.getStatus() == TicketStatus.DISCHARGED) {
            throw new RuntimeException("This patient has been discharged");
        }

        ticket.setStatus(TicketStatus.IN_CONSULTATION);
        ticket.setConsultationStartedAt(LocalDateTime.now());
        ticket.setAssignedDoctor(getCurrentUser(userId));

        Ticket saved = ticketRepository.save(ticket);

        log.info("Consultation started for ticket: {} by staff: {}", ticket.getTicketNumber(), userId);

        return saved;
    }

    @Transactional
    public Ticket completeConsultation(UUID ticketId, UUID userId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        // Verify staff has access to this ticket
        Department staffDept = getStaffDepartment(userId);
        if (!ticket.getDepartment().getId().equals(staffDept.getId())) {
            throw new RuntimeException("You do not have access to this ticket");
        }

        // Verify ticket is in consultation
        if (ticket.getStatus() != TicketStatus.IN_CONSULTATION) {
            throw new RuntimeException("This ticket is not in consultation");
        }

        ticket.setStatus(TicketStatus.CONSULTATION_DONE);
        ticket.setConsultationCompletedAt(LocalDateTime.now());

        Ticket saved = ticketRepository.save(ticket);

        log.info("Consultation completed for ticket: {} by staff: {}", ticket.getTicketNumber(), userId);

        return saved;
    }

    // ==================== TICKET DETAILS ====================

    public Ticket getTicketDetails(UUID ticketId, UUID userId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        // Verify staff has access to this ticket
        Department staffDept = getStaffDepartment(userId);
        if (!ticket.getDepartment().getId().equals(staffDept.getId())) {
            throw new RuntimeException("You do not have access to this ticket");
        }

        return ticket;
    }

    // ==================== QUEUE POSITION UPDATE ====================

    @Transactional
    public void updateQueuePositions(UUID facilityId, UUID departmentId) {
        List<Ticket> waitingTickets = ticketRepository.findWaitingTicketsByFacilityAndDepartment(
                facilityId, departmentId
        );

        int position = 1;
        for (Ticket ticket : waitingTickets) {
            ticket.setQueuePosition(position++);
            ticketRepository.save(ticket);
        }

        log.info("Updated queue positions for facility: {}, department: {}, total: {}",
                facilityId, departmentId, waitingTickets.size());
    }
}