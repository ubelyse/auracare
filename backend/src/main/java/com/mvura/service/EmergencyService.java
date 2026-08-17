package com.mvura.service;

import com.mvura.model.*;
import com.mvura.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmergencyService {

    private final TicketRepository ticketRepository;
    private final FacilityRepository facilityRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final SseService sseService;
    private final AuditService auditService;

    private static final int MIN_DURATION_MINUTES = 5;
    private static final int MAX_DURATION_MINUTES = 60;
    private static final int MAX_EXTEND_MINUTES = 30;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== HELPER METHODS ====================

    private String sanitizeForLog(String input) {
        if (input == null) {
            return "";
        }
        String cleaned = input.replaceAll("[\\r\\n\\t]", " ").trim();
        return cleaned.length() > 500 ? cleaned.substring(0, 500) + "...(truncated)" : cleaned;
    }

    private User verifyDoctorFacilityAccess(UUID doctorId, UUID facilityId, UUID departmentId) {
        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        boolean facilityMatches = doctor.getPrimaryFacility() != null
                && doctor.getPrimaryFacility().getId().equals(facilityId);
        boolean departmentMatches = doctor.getDepartments() != null
                && doctor.getDepartments().stream().anyMatch(d -> d.getId().equals(departmentId));

        if (!facilityMatches || !departmentMatches) {
            auditService.logSecurityEvent(
                    "UNAUTHORIZED_EMERGENCY_ACTIVATION_ATTEMPT",
                    doctor.getUsername(),
                    doctor.getId(),
                    null,
                    "Attempted facility: " + facilityId + ", department: " + departmentId
            );
            throw new AccessDeniedException("You are not assigned to this facility/department");
        }

        return doctor;
    }

    private void verifyChoiceAuthorization(Ticket ticket, UUID requestingUserId) {
        if (ticket.getPatient() != null && ticket.getPatient().getId().equals(requestingUserId)) {
            return;
        }

        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isStaffAtFacility = requester.getRole() != null
                && (requester.getRole().name().equals("DOCTOR")
                || requester.getRole().name().equals("STAFF")
                || requester.getRole().name().equals("FACILITY_ADMIN"))
                && requester.getPrimaryFacility() != null
                && ticket.getFacility() != null
                && requester.getPrimaryFacility().getId().equals(ticket.getFacility().getId());

        if (!isStaffAtFacility) {
            auditService.logSecurityEvent(
                    "UNAUTHORIZED_EMERGENCY_CHOICE_ATTEMPT",
                    requester.getUsername(),
                    requester.getId(),
                    null,
                    "Ticket: " + ticket.getTicketNumber() + " does not belong to requester and requester is not facility staff"
            );
            throw new AccessDeniedException("You are not authorized to act on this ticket");
        }
    }

    // ==================== ORIGINAL METHODS ====================

    @Transactional
    public void activateEmergencyMode(UUID facilityId, UUID departmentId, UUID doctorId, int durationMinutes) {
        User doctor = verifyDoctorFacilityAccess(doctorId, facilityId, departmentId);

        int clampedDuration = Math.max(MIN_DURATION_MINUTES, Math.min(MAX_DURATION_MINUTES, durationMinutes));

        log.info("Activating emergency mode. Facility: {}, Department: {}, Duration: {} minutes",
                facilityId, departmentId, clampedDuration);

        List<Ticket> tickets = ticketRepository.findActiveTicketsByFacilityAndDepartment(facilityId, departmentId);

        List<Ticket> affectedTickets = tickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.TRIAGED ||
                        t.getStatus() == TicketStatus.CHECKED_IN ||
                        t.getStatus() == TicketStatus.LAB_PENDING ||
                        t.getStatus() == TicketStatus.LAB_COMPLETED)
                .filter(Ticket::isActive)
                .toList();

        if (affectedTickets.isEmpty()) {
            log.info("No affected tickets found for emergency activation in this department");
        }

        for (Ticket ticket : affectedTickets) {
            ticket.setEmergencyModeActive(true);
            ticket.setEmergencyModeStartedAt(LocalDateTime.now());
            ticket.setEmergencyModeEndedAt(LocalDateTime.now().plusMinutes(clampedDuration));
            ticketRepository.save(ticket);

            sseService.sendEmergencyAlert(
                    facilityId.toString(),
                    departmentId.toString(),
                    ticket,
                    "Emergency mode activated. Doctor is in emergency. Please wait or choose an option."
            );
        }

        auditService.logSecurityEvent(
                "EMERGENCY_MODE_ACTIVATED",
                doctor.getUsername(),
                doctor.getId(),
                null,
                "Facility: " + facilityId + ", Department: " + departmentId +
                        ", Duration: " + clampedDuration + " mins, Affected: " + affectedTickets.size()
        );

        log.info("Emergency mode activated for {} patients", affectedTickets.size());
    }

    @Transactional
    public void deactivateEmergencyMode(UUID facilityId, UUID departmentId, UUID doctorId) {
        verifyDoctorFacilityAccess(doctorId, facilityId, departmentId);

        List<Ticket> tickets = ticketRepository.findActiveEmergencyTickets(facilityId, departmentId);

        if (tickets.isEmpty()) {
            throw new RuntimeException("No active emergency mode found");
        }

        for (Ticket ticket : tickets) {
            ticket.setEmergencyModeActive(false);
            ticket.setEmergencyModeEndedAt(LocalDateTime.now());
            ticketRepository.save(ticket);
            sseService.sendTicketUpdate(ticket);
        }

        auditService.logSecurityEvent(
                "EMERGENCY_MODE_DEACTIVATED",
                "Doctor",
                doctorId,
                null,
                "Facility: " + facilityId + ", Department: " + departmentId
        );

        log.info("Emergency mode deactivated by doctor {} for facility {} department {}",
                doctorId, facilityId, departmentId);
    }

    @Transactional
    public Ticket handleEmergencyChoice(UUID ticketId, String choice, UUID targetFacilityId, UUID requestingUserId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        verifyChoiceAuthorization(ticket, requestingUserId);

        if (!ticket.isEmergencyModeActive()) {
            throw new IllegalStateException("This ticket has no active emergency to respond to");
        }

        if (choice == null || choice.isBlank()) {
            throw new IllegalArgumentException("Choice is required");
        }

        String cleanChoice = choice.trim().toUpperCase();
        ticket.setEmergencyOption(cleanChoice);

        switch (cleanChoice) {
            case "WAIT":
                ticket.setEmergencyModeActive(false);
                log.info("Patient {} chose to wait", ticket.getTicketNumber());
                break;

            case "INTERNAL_TRANSFER":
                return internalTransfer(ticket);

            case "EXTERNAL_TRANSFER":
                if (targetFacilityId == null) {
                    throw new IllegalArgumentException("targetFacilityId is required for external transfer");
                }
                return transferToFacility(ticket, targetFacilityId);

            default:
                throw new IllegalArgumentException("Invalid emergency choice: " + sanitizeForLog(choice));
        }

        Ticket updated = ticketRepository.save(ticket);
        sseService.sendTicketUpdate(updated);

        return updated;
    }

    @Transactional
    public Ticket internalTransfer(Ticket ticket) {
        Department department = ticket.getDepartment();
        UUID currentDoctorId = ticket.getAssignedDoctor() != null ? ticket.getAssignedDoctor().getId() : null;

        List<User> availableDoctors = departmentRepository.findAvailableDoctorsByDepartment(department.getId())
                .stream()
                .filter(doc -> currentDoctorId == null || !doc.getId().equals(currentDoctorId))
                .toList();

        if (availableDoctors.isEmpty()) {
            throw new IllegalStateException(
                    "No other doctor is currently available in this department for an internal transfer. " +
                            "Please choose external transfer instead."
            );
        }

        User leastBusyDoctor = availableDoctors.stream()
                .min(Comparator.comparingInt(doc -> ticketRepository.countActiveTicketsForDoctor(doc.getId())))
                .orElse(availableDoctors.get(0));

        User previousDoctor = ticket.getAssignedDoctor();
        ticket.setAssignedDoctor(leastBusyDoctor);
        ticket.setEmergencyModeActive(false);

        Ticket updated = ticketRepository.save(ticket);
        sseService.sendTicketUpdate(updated);

        String patientUsername = ticket.getPatient() != null ? ticket.getPatient().getUsername() : "unknown";
        UUID patientId = ticket.getPatient() != null ? ticket.getPatient().getId() : null;

        auditService.logSecurityEvent(
                "EMERGENCY_INTERNAL_TRANSFER",
                patientUsername,
                patientId,
                null,
                "Ticket: " + ticket.getTicketNumber() +
                        ", From doctor: " + (previousDoctor != null ? previousDoctor.getUsername() : "none") +
                        ", To doctor: " + leastBusyDoctor.getUsername()
        );

        log.info("Patient {} internally transferred from {} to {} within department {}",
                ticket.getTicketNumber(),
                previousDoctor != null ? previousDoctor.getUsername() : "none",
                leastBusyDoctor.getUsername(),
                department.getName());

        return updated;
    }

    @Transactional
    public Ticket transferToFacility(Ticket ticket, UUID targetFacilityId) {
        Facility targetFacility = facilityRepository.findById(targetFacilityId)
                .orElseThrow(() -> new RuntimeException("Target facility not found"));

        Department matchingDepartment = departmentRepository.findByFacilityIdAndCode(
                targetFacilityId,
                ticket.getDepartment().getCode()
        ).orElseThrow(() -> new IllegalStateException(
                "Target facility has no " + ticket.getDepartment().getCode() + " department"
        ));

        if (matchingDepartment.getAvailableDoctorCount() <= 0) {
            throw new IllegalStateException(
                    "Target department has no active doctor on duty; choose a different facility"
            );
        }

        UUID originalFacilityId = ticket.getFacility().getId();
        UUID originalDepartmentId = ticket.getDepartment().getId();

        ticket.setFacility(targetFacility);
        ticket.setDepartment(matchingDepartment);
        ticket.setTransferFromFacilityId(originalFacilityId);
        ticket.setTransferFromDepartmentId(originalDepartmentId);
        ticket.setTransferredAt(LocalDateTime.now());
        ticket.setTransferReason("Emergency transfer");
        ticket.setEmergencyModeActive(false);

        Ticket updated = ticketRepository.save(ticket);

        int position = ticketRepository.countActiveTickets(
                targetFacility.getId(),
                matchingDepartment.getId()
        );
        updated.setQueuePosition(position);
        ticketRepository.save(updated);

        sseService.sendTicketUpdate(updated);

        String patientUsername = ticket.getPatient() != null ? ticket.getPatient().getUsername() : "unknown";
        UUID patientId = ticket.getPatient() != null ? ticket.getPatient().getId() : null;

        auditService.logSecurityEvent(
                "EMERGENCY_TRANSFER",
                patientUsername,
                patientId,
                null,
                "From: " + originalFacilityId + " To: " + targetFacilityId +
                        ", Ticket: " + ticket.getTicketNumber()
        );

        log.info("Patient {} transferred from {} to {} due to emergency",
                ticket.getTicketNumber(), originalFacilityId, targetFacilityId);

        return updated;
    }

    public List<Facility> findAvailableFacilities(UUID facilityId, String departmentCode) {
        List<Facility> facilities = facilityRepository.findAll();

        return facilities.stream()
                .filter(f -> !f.getId().equals(facilityId))
                .filter(Facility::isActive)
                .filter(f -> {
                    List<Department> depts = departmentRepository.findActiveByFacility(f.getId());
                    return depts.stream().anyMatch(d ->
                            d.getCode().equals(departmentCode) && d.getAvailableDoctorCount() > 0
                    );
                })
                .toList();
    }

    public Map<String, Object> getEmergencyStatus(UUID facilityId, UUID departmentId) {
        List<Ticket> activeEmergencyTickets = ticketRepository.findActiveEmergencyTickets(facilityId, departmentId);

        if (activeEmergencyTickets.isEmpty()) {
            return Map.of("active", false);
        }

        LocalDateTime latestEnd = activeEmergencyTickets.stream()
                .map(Ticket::getEmergencyModeEndedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return Map.of(
                "active", true,
                "endsAt", latestEnd
        );
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void expireEmergencyMode() {
        List<Ticket> expired = ticketRepository.findExpiredEmergencyTickets();
        for (Ticket ticket : expired) {
            ticket.setEmergencyModeActive(false);
            ticketRepository.save(ticket);
        }
        if (!expired.isEmpty()) {
            log.info("Auto-expired emergency mode for {} tickets", expired.size());
        }
    }

    // ==================== ENHANCEMENT 1: EXTEND EMERGENCY MODE TIMEOUT ====================

    @Transactional
    public void extendEmergencyMode(UUID facilityId, UUID departmentId, UUID doctorId, int additionalMinutes) {
        User doctor = verifyDoctorFacilityAccess(doctorId, facilityId, departmentId);

        if (additionalMinutes < 0) {
            throw new IllegalArgumentException("Additional minutes cannot be negative");
        }

        int clampedAdditional = Math.min(additionalMinutes, MAX_EXTEND_MINUTES);

        List<Ticket> emergencyTickets = ticketRepository.findActiveEmergencyTickets(facilityId, departmentId);

        if (emergencyTickets.isEmpty()) {
            throw new RuntimeException("No active emergency mode found to extend");
        }

        int extendedCount = 0;
        for (Ticket ticket : emergencyTickets) {
            LocalDateTime currentEnd = ticket.getEmergencyModeEndedAt();
            LocalDateTime newEndTime = currentEnd.plusMinutes(clampedAdditional);
            ticket.setEmergencyModeEndedAt(newEndTime);
            ticketRepository.save(ticket);

            sseService.sendEmergencyAlert(
                    facilityId.toString(),
                    departmentId.toString(),
                    ticket,
                    "Emergency mode extended by " + clampedAdditional + " minutes. New end time: " + newEndTime
            );

            extendedCount++;
        }

        auditService.logSecurityEvent(
                "EMERGENCY_MODE_EXTENDED",
                doctor.getUsername(),
                doctor.getId(),
                null,
                "Facility: " + facilityId + ", Department: " + departmentId +
                        ", Additional: " + clampedAdditional + " mins, Extended tickets: " + extendedCount
        );

        log.info("Emergency mode extended by {} minutes for facility {} department {}, affecting {} tickets",
                clampedAdditional, facilityId, departmentId, extendedCount);
    }

    // ==================== ENHANCEMENT 2: PRIORITY ESCALATION ====================

    @Transactional
    public void escalateEmergencyPriority(UUID ticketId, UUID doctorId, String reason) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        // Verify doctor has access
        verifyDoctorFacilityAccess(doctorId, ticket.getFacility().getId(), ticket.getDepartment().getId());

        if (!ticket.isEmergencyModeActive()) {
            throw new IllegalStateException("Ticket is not in emergency mode");
        }

        // Store old values for audit
        Priority oldPriority = ticket.getPriority();
        int oldPosition = ticket.getQueuePosition() != null ? ticket.getQueuePosition() : 0;

        // Escalate priority
        ticket.setPriority(Priority.EMERGENCY);
        ticket.setQueuePosition(1);
        ticket.setEstimatedWaitMinutes(0);
        ticket.setEmergencyOption("ESCALATED");
        ticketRepository.save(ticket);

        // Notify all available doctors in the department
        List<User> availableDoctors = departmentRepository.findAvailableDoctorsByDepartment(
                ticket.getDepartment().getId()
        );

        for (User doc : availableDoctors) {
            sseService.sendDoctorNotification(
                    doc.getId(),
                    "🚨 EMERGENCY ESCALATION: Patient " + ticket.getTicketNumber() +
                            " needs immediate attention. Reason: " + (reason != null ? reason : "Condition worsening")
            );
        }

        // Update patient via SSE
        sseService.sendTicketUpdate(ticket);

        auditService.logSecurityEvent(
                "EMERGENCY_PRIORITY_ESCALATED",
                ticket.getPatient() != null ? ticket.getPatient().getUsername() : "unknown",
                ticket.getPatient() != null ? ticket.getPatient().getId() : null,
                null,
                "Ticket: " + ticket.getTicketNumber() +
                        ", Old priority: " + oldPriority +
                        ", New priority: EMERGENCY" +
                        ", Old position: " + oldPosition +
                        ", Reason: " + (reason != null ? reason : "Not specified")
        );

        log.info("🚨 Emergency priority escalated for ticket: {} by doctor: {}",
                ticket.getTicketNumber(), doctor.getUsername());
    }

    // ==================== ENHANCEMENT 3: EMERGENCY HISTORY ====================

    public List<EmergencyLog> getEmergencyHistory(UUID facilityId, LocalDateTime startDate, LocalDateTime endDate) {
        // Get all tickets that were in emergency mode during the period
        List<Ticket> allTickets = ticketRepository.findAll();

        List<Ticket> emergencyTickets = allTickets.stream()
                .filter(t -> t.getEmergencyModeStartedAt() != null)
                .filter(t -> {
                    LocalDateTime started = t.getEmergencyModeStartedAt();
                    return (started.isAfter(startDate) && started.isBefore(endDate)) ||
                            (t.getEmergencyModeEndedAt() != null &&
                                    t.getEmergencyModeEndedAt().isAfter(startDate) &&
                                    t.getEmergencyModeEndedAt().isBefore(endDate));
                })
                .filter(t -> t.getFacility() != null && t.getFacility().getId().equals(facilityId))
                .toList();

        return emergencyTickets.stream()
                .map(t -> EmergencyLog.builder()
                        .ticketNumber(t.getTicketNumber())
                        .patientName(t.getPatient() != null ?
                                t.getPatient().getFirstName() + " " + t.getPatient().getLastName() :
                                "Unknown Patient")
                        .facilityName(t.getFacility() != null ? t.getFacility().getName() : "Unknown")
                        .departmentName(t.getDepartment() != null ? t.getDepartment().getName() : "Unknown")
                        .doctorName(t.getAssignedDoctor() != null ?
                                "Dr. " + t.getAssignedDoctor().getFirstName() + " " + t.getAssignedDoctor().getLastName() :
                                "Not Assigned")
                        .startedAt(t.getEmergencyModeStartedAt())
                        .endedAt(t.getEmergencyModeEndedAt())
                        .durationMinutes(calculateDuration(t))
                        .action(t.getEmergencyOption())
                        .isActive(t.isEmergencyModeActive())
                        .status(t.getStatus() != null ? t.getStatus().name() : "UNKNOWN")
                        .priority(t.getPriority() != null ? t.getPriority().name() : "UNKNOWN")
                        .build())
                .sorted(Comparator.comparing(EmergencyLog::getStartedAt).reversed())
                .collect(Collectors.toList());
    }

    private long calculateDuration(Ticket ticket) {
        if (ticket.getEmergencyModeStartedAt() == null) return 0;
        LocalDateTime end = ticket.getEmergencyModeEndedAt() != null ?
                ticket.getEmergencyModeEndedAt() : LocalDateTime.now();
        return java.time.Duration.between(ticket.getEmergencyModeStartedAt(), end).toMinutes();
    }

    // ==================== ENHANCEMENT 4: EMERGENCY STATISTICS ====================

    public EmergencyStats getEmergencyStats(UUID facilityId, LocalDateTime startDate, LocalDateTime endDate) {
        // Get emergency tickets in the period
        List<Ticket> allTickets = ticketRepository.findAll();
        List<Ticket> emergencyTickets = allTickets.stream()
                .filter(t -> t.getEmergencyModeStartedAt() != null)
                .filter(t -> t.getEmergencyModeStartedAt().isAfter(startDate) &&
                        t.getEmergencyModeStartedAt().isBefore(endDate))
                .filter(t -> t.getFacility() != null && t.getFacility().getId().equals(facilityId))
                .toList();

        if (emergencyTickets.isEmpty()) {
            return EmergencyStats.builder()
                    .totalEmergencies(0)
                    .waitChoices(0)
                    .internalTransfers(0)
                    .externalTransfers(0)
                    .averageDurationMinutes(0)
                    .build();
        }

        long totalEmergencies = emergencyTickets.size();

        long waitChoices = emergencyTickets.stream()
                .filter(t -> "WAIT".equals(t.getEmergencyOption()))
                .count();

        long internalTransfers = emergencyTickets.stream()
                .filter(t -> "INTERNAL_TRANSFER".equals(t.getEmergencyOption()))
                .count();

        long externalTransfers = emergencyTickets.stream()
                .filter(t -> "EXTERNAL_TRANSFER".equals(t.getEmergencyOption()))
                .count();

        long escalatedCases = emergencyTickets.stream()
                .filter(t -> "ESCALATED".equals(t.getEmergencyOption()))
                .count();

        double avgDuration = emergencyTickets.stream()
                .mapToLong(this::calculateDuration)
                .average()
                .orElse(0);

        // Priority distribution
        Map<String, Long> priorityDistribution = emergencyTickets.stream()
                .filter(t -> t.getPriority() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getPriority().name(),
                        Collectors.counting()
                ));

        // Status distribution
        Map<String, Long> statusDistribution = emergencyTickets.stream()
                .filter(t -> t.getStatus() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getStatus().name(),
                        Collectors.counting()
                ));

        return EmergencyStats.builder()
                .totalEmergencies(totalEmergencies)
                .waitChoices(waitChoices)
                .internalTransfers(internalTransfers)
                .externalTransfers(externalTransfers)
                .escalatedCases(escalatedCases)
                .averageDurationMinutes(avgDuration)
                .priorityDistribution(priorityDistribution)
                .statusDistribution(statusDistribution)
                .periodStart(startDate)
                .periodEnd(endDate)
                .build();
    }

    // ==================== ENHANCEMENT 5: FACILITY-WIDE EMERGENCY ====================

    @Transactional
    public void activateFacilityWideEmergency(UUID facilityId, UUID doctorId, int durationMinutes) {
        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        // Verify doctor belongs to this facility
        if (doctor.getPrimaryFacility() == null ||
                !doctor.getPrimaryFacility().getId().equals(facilityId)) {
            throw new AccessDeniedException("Doctor does not belong to this facility");
        }

        int clampedDuration = Math.max(MIN_DURATION_MINUTES, Math.min(MAX_DURATION_MINUTES, durationMinutes));

        // Get all departments in the facility
        List<Department> departments = departmentRepository.findActiveByFacility(facilityId);

        if (departments.isEmpty()) {
            throw new RuntimeException("No active departments found in this facility");
        }

        int totalAffected = 0;
        int departmentsActivated = 0;

        for (Department department : departments) {
            try {
                activateEmergencyMode(facilityId, department.getId(), doctorId, clampedDuration);
                departmentsActivated++;
                int affectedInDept = ticketRepository.findActiveEmergencyTickets(facilityId, department.getId()).size();
                totalAffected += affectedInDept;
            } catch (Exception e) {
                log.warn("Failed to activate emergency for department {}: {}",
                        department.getName(), e.getMessage());
            }
        }

        auditService.logSecurityEvent(
                "FACILITY_WIDE_EMERGENCY_ACTIVATED",
                doctor.getUsername(),
                doctor.getId(),
                null,
                "Facility: " + facilityId +
                        ", Duration: " + clampedDuration +
                        " mins, Departments: " + departmentsActivated +
                        ", Total affected: " + totalAffected
        );

        log.info("🏥 Facility-wide emergency activated for {} departments, {} patients affected",
                departmentsActivated, totalAffected);
    }

    @Transactional
    public void deactivateFacilityWideEmergency(UUID facilityId, UUID doctorId) {
        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        // Verify doctor belongs to this facility
        if (doctor.getPrimaryFacility() == null ||
                !doctor.getPrimaryFacility().getId().equals(facilityId)) {
            throw new AccessDeniedException("Doctor does not belong to this facility");
        }

        // Get all departments in the facility
        List<Department> departments = departmentRepository.findActiveByFacility(facilityId);

        int deactivatedCount = 0;

        for (Department department : departments) {
            try {
                List<Ticket> emergencyTickets = ticketRepository.findActiveEmergencyTickets(facilityId, department.getId());
                if (!emergencyTickets.isEmpty()) {
                    for (Ticket ticket : emergencyTickets) {
                        ticket.setEmergencyModeActive(false);
                        ticket.setEmergencyModeEndedAt(LocalDateTime.now());
                        ticketRepository.save(ticket);
                        sseService.sendTicketUpdate(ticket);
                        deactivatedCount++;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to deactivate emergency for department {}: {}",
                        department.getName(), e.getMessage());
            }
        }

        auditService.logSecurityEvent(
                "FACILITY_WIDE_EMERGENCY_DEACTIVATED",
                doctor.getUsername(),
                doctor.getId(),
                null,
                "Facility: " + facilityId + ", Tickets deactivated: " + deactivatedCount
        );

        log.info("🏥 Facility-wide emergency deactivated for {} patients", deactivatedCount);
    }

    // ==================== INNER CLASSES ====================

    @lombok.Data
    @lombok.Builder
    public static class EmergencyLog {
        private String ticketNumber;
        private String patientName;
        private String facilityName;
        private String departmentName;
        private String doctorName;
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;
        private long durationMinutes;
        private String action;
        private boolean isActive;
        private String status;
        private String priority;
    }

    @lombok.Data
    @lombok.Builder
    public static class EmergencyStats {
        private long totalEmergencies;
        private long waitChoices;
        private long internalTransfers;
        private long externalTransfers;
        private long escalatedCases;
        private double averageDurationMinutes;
        private Map<String, Long> priorityDistribution;
        private Map<String, Long> statusDistribution;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
    }
}