package com.mvura.service;

import com.mvura.dto.CheckInRequest;
import com.mvura.model.*;
import com.mvura.repository.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.mvura.repository.AppointmentRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckInService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final FacilityRepository facilityRepository;
    private final DepartmentRepository departmentRepository;
    private final TriageService triageService;
    private final PhiScrubberService phiScrubberService;
    private final AuditService auditService;
    private final AppointmentRepository appointmentRepository;

    private static final Object TICKET_NUMBER_LOCK = new Object();

    // ===== TODO: Make this configurable per facility =====
    private static final int AVG_MINUTES_PER_PATIENT = 12;
    private static final int POSITION_ONE_WAIT_TIME = 5;  // Position #1 seen in 5 minutes

    @Transactional
    public Ticket initiateCheckIn(CheckInRequest request) {
        log.info("===== STARTING CHECK-IN =====");
        log.info("Patient: {}, Facility: {}, Department: {}",
                request.getPatientId(), request.getFacilityId(), request.getDepartmentId());

        // 1. Validate patient
        User patient = userRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        if (patient.getRole() != UserRole.PATIENT) {
            throw new RuntimeException("User is not a patient");
        }

        // 2. Check existing active ticket
        boolean hasActiveTicket = ticketRepository.hasActiveTicket(patient.getId());
        if (hasActiveTicket) {
            Ticket existingTicket = ticketRepository.findLatestActiveTicketByPatient(patient.getId()).get();
            throw new RuntimeException("Patient already has an active ticket: " + existingTicket.getTicketNumber() +
                    " (Status: " + existingTicket.getStatus() + ")");
        }

        // 3. Validate facility and department
        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"));

        if (!department.getFacility().getId().equals(facility.getId())) {
            throw new RuntimeException("Department does not belong to the selected facility");
        }

        // ===== 4. CALCULATE QUEUE POSITION BEFORE SAVING =====
        int queuePosition = calculateWalkInQueuePosition(facility, department);
        log.info("Queue position calculated BEFORE saving: {}", queuePosition);

        // ===== 5. BUILD TICKET WITH POSITION =====
        String sanitizedSymptoms = phiScrubberService.scrubPhi(request.getSymptoms());
        String ticketNumber = generateTicketNumber(facility.getCode(), department.getCode());

        int age = patient.getAge();
        String chronicConditions = patient.getChronicConditions();

        Boolean isPregnant = (patient.getGender() == Gender.FEMALE)
                ? request.getIsPregnant()
                : null;

        Ticket ticket = Ticket.builder()
                .ticketNumber(ticketNumber)
                .patient(patient)
                .facility(facility)
                .department(department)
                .status(TicketStatus.CHECKED_IN)
                .priority(Priority.MEDIUM)
                .symptoms(request.getSymptoms())
                .sanitizedSymptoms(sanitizedSymptoms)
                .age(age)
                .gender(patient.getGender())
                .isPregnant(isPregnant)
                .chronicConditions(chronicConditions)
                .hasRecentSurgery(request.getHasRecentSurgery())
                .healthChanges(request.getHealthChanges())
                .recentSurgeryDetails(request.getRecentSurgeryDetails())
                .hasAllergies(request.getHasNewAllergies())
                .allergiesDescription(request.getNewAllergiesDetails())
                .insuranceType(request.getInsuranceType())
                .active(true)
                .queuePosition(queuePosition)
                .build();

        // ===== 6. DOCTOR ASSIGNMENT LOGIC =====
        if (request.getDoctorId() != null && !request.getDoctorId().toString().trim().isEmpty()) {

            User doctor = userRepository.findById(request.getDoctorId())
                    .orElseThrow(() -> new RuntimeException("Doctor not found: " + request.getDoctorId()));

            if (doctor.getRole() != UserRole.DOCTOR) {
                log.warn("Rejected doctor assignment: user {} is not a doctor (role: {})",
                        doctor.getUsername(), doctor.getRole());
                throw new RuntimeException("Selected user is not a doctor");
            }

            boolean isInDepartment = departmentRepository.doctorBelongsToDepartment(
                    doctor.getId(), department.getId());

            if (!isInDepartment) {
                log.warn("Rejected doctor assignment: doctor {} is not part of department {}",
                        doctor.getUsername(), department.getName());
                throw new RuntimeException(String.format(
                        "Dr. %s %s is not assigned to %s at this facility.",
                        doctor.getFirstName(), doctor.getLastName(), department.getName()));
            }

            if (doctor.getPrimaryFacility() == null
                    || !doctor.getPrimaryFacility().getId().equals(facility.getId())) {
                log.warn("Rejected doctor assignment: doctor {} primary facility does not match selected facility {}",
                        doctor.getUsername(), facility.getId());
                throw new RuntimeException(String.format(
                        "Dr. %s %s does not belong to this facility.",
                        doctor.getFirstName(), doctor.getLastName()));
            }

            ticket.setAssignedDoctor(doctor);
            log.info("Patient {} assigned to selected doctor: {}", patient.getUsername(), doctor.getUsername());
        } else {
            // Smart Auto-Assignment
            List<User> availableDoctors = departmentRepository.findAvailableDoctorsByDepartment(department.getId());

            if (availableDoctors != null && !availableDoctors.isEmpty()) {
                User leastBusyDoctor = availableDoctors.stream()
                        .min(Comparator.comparingInt(doc -> ticketRepository.countActiveTicketsForDoctor(doc.getId())))
                        .orElse(availableDoctors.get(0));

                ticket.setAssignedDoctor(leastBusyDoctor);
                log.info("Auto-assigned patient {} to least busy doctor: {}",
                        patient.getUsername(), leastBusyDoctor.getUsername());
            } else {
                log.warn("No available doctors found in department {} for auto-assignment.", department.getName());
            }
        }

        // ===== 7. SAVE TICKET =====
        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("Ticket saved: {} with position: {}", ticketNumber, queuePosition);

        // ===== 8. PERFORM TRIAGE =====
        TriageResult triageResult = triageService.performTriage(ticket);
        Priority finalPriority = triageResult.getPriority();

        // ===== 9. CALCULATE WAIT TIME =====
        int waitTime = calculateWaitTimeFromPosition(queuePosition, finalPriority);
        log.info("Wait time calculated: {} minutes for position {} with priority {}",
                waitTime, queuePosition, finalPriority);

        // ===== 10. UPDATE TICKET =====
        savedTicket.setPriority(finalPriority);
        savedTicket.setTriageScore(triageResult.getTriageScore());
        savedTicket.setTriageMethod(triageResult.getTriageMethod());
        savedTicket.setAiConfidence(triageResult.getAiConfidence());
        savedTicket.setEstimatedWaitMinutes(waitTime);
        savedTicket.setQueuePosition(queuePosition);
        savedTicket.setStatus(TicketStatus.TRIAGED);
        savedTicket.setTriagedAt(LocalDateTime.now());

        Ticket finalTicket = ticketRepository.save(savedTicket);

        // ===== 11. AUDIT =====
        auditService.logSecurityEvent(
                "PATIENT_CHECK_IN",
                patient.getUsername(),
                patient.getId(),
                null,
                "Ticket: " + ticketNumber +
                        ", Position: " + queuePosition +
                        ", Wait: " + waitTime + " min" +
                        ", Priority: " + finalPriority +
                        ", Facility: " + facility.getName() +
                        ", Department: " + department.getName()
        );

        log.info("✅ Check-in complete: {} - Position: {}, Wait: {} min, Priority: {}",
                ticketNumber, queuePosition, waitTime, finalPriority);

        return finalTicket;
    }

    // ===== HELPER: Calculate queue position for walk-ins =====
    private int calculateWalkInQueuePosition(Facility facility, Department department) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soon = now.plusMinutes(15);

        // Check if there's an upcoming booked appointment
        boolean hasUpcomingBooking = appointmentRepository.hasUpcomingAppointment(
                facility.getId(),
                department.getId(),
                now,
                soon
        );

        if (hasUpcomingBooking) {
            // Walk-in gets position #2 (behind the booking)
            log.info("Upcoming booking detected. Walk-in gets position #2");
            return 2;
        } else {
            // Count active tickets (called BEFORE saving, so it's accurate)
            long activeCount = ticketRepository.countActiveTickets(facility.getId(), department.getId());
            log.info("Active tickets count: {}, position will be: {}", activeCount, activeCount + 1);
            return (int) (activeCount + 1);
        }
    }

    // ===== HELPER: Calculate wait time from position =====
    private int calculateWaitTimeFromPosition(int queuePosition, Priority priority) {
        // Consultation duration in minutes based on priority
        // EMERGENCY patients are seen quickly (5 min)
        // LOW priority patients take longer (20 min)
        int minutesPerPatient = switch (priority) {
            case EMERGENCY -> 5;
            case HIGH -> 10;
            case MEDIUM -> 15;
            case LOW -> 20;
            default -> 15;
        };

        // Position #1: Next in line, seen in 5 minutes
        if (queuePosition == 1) {
            return POSITION_ONE_WAIT_TIME; // 5 minutes
        }

        // Position #2+: Calculate based on patients ahead
        int patientsAhead = queuePosition - 1;
        int waitTime = patientsAhead * minutesPerPatient;

        // Add buffer for transition between patients (handoff, documentation, etc.)
        int bufferPerPatient = 3;
        int totalBuffer = patientsAhead * bufferPerPatient;
        waitTime += totalBuffer;

        // Ensure minimum wait for position #2
        if (queuePosition == 2 && waitTime < 10) {
            waitTime = 10;
        }

        log.info("⏱️ Position #{}, Priority: {}, Patients ahead: {}, Wait: {} min ({} min/patient + {} min buffer)",
                queuePosition, priority, patientsAhead, waitTime, minutesPerPatient, totalBuffer);

        return waitTime;
    }

    // ===== HELPER: Generate ticket number =====
    private String generateTicketNumber(String facilityCode, String departmentCode) {
        synchronized (TICKET_NUMBER_LOCK) {
            String prefix = facilityCode + "-" + departmentCode;
            String sequence = String.format("%04d", ticketRepository.count() % 9999 + 1);
            return prefix + "-" + sequence;
        }
    }

    // ===== EXISTING METHODS =====

    public Ticket getTicketByNumber(String ticketNumber) {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
    }

    public Ticket getActiveTicketForPatient(UUID patientId) {
        log.info("🔴🔴🔴 Getting active ticket for patient: {}", patientId);

        Optional<Ticket> ticket = ticketRepository.findLatestActiveTicketByPatient(patientId);
        log.info("🔴🔴🔴 Ticket present: {}", ticket.isPresent());

        if (ticket.isEmpty()) {
            List<Ticket> allTickets = ticketRepository.findAllByPatientId(patientId);
            log.info("Total tickets for patient: {}", allTickets.size());

            if (!allTickets.isEmpty()) {
                ticket = Optional.of(allTickets.get(0));
                log.info("No active ticket, returning most recent: {} - Status: {}",
                        ticket.get().getTicketNumber(), ticket.get().getStatus());
            }
        }

        if (ticket.isEmpty()) {
            log.error("No ticket found for patient: {}", patientId);
            throw new RuntimeException("No active ticket found");
        }

        log.info("🔴🔴🔴 Returning ticket: {} with status: {}", ticket.get().getTicketNumber(), ticket.get().getStatus());
        return ticket.get();
    }

    public boolean hasActiveTicket(UUID patientId) {
        log.info("Checking if patient {} has active ticket", patientId);

        boolean hasActive = ticketRepository.hasActiveTicket(patientId);
        log.info("Patient {} has active ticket: {}", patientId, hasActive);

        if (!hasActive) {
            List<Ticket> allTickets = ticketRepository.findAllByPatientId(patientId);
            log.info("Total tickets for patient: {}", allTickets.size());

            if (!allTickets.isEmpty()) {
                log.info("Patient has {} tickets but none are active (all DISCHARGED or CANCELLED)", allTickets.size());
            }
        }

        return hasActive;
    }

    public List<User> getAvailableDoctors(UUID departmentId) {
        return departmentRepository.findAvailableDoctorsByDepartment(departmentId);
    }

    @Transactional(readOnly = true)
    public QueuePreview previewQueueStatus(UUID facilityId, UUID departmentId, UUID doctorId, UUID patientId, UUID appointmentId) {
        log.info("Previewing queue status for patient: {}, facility: {}, department: {}, doctor: {}",
                patientId, facilityId, departmentId, doctorId);

        // Get facility and department
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        // ===== VALIDATE DOCTOR BELONGS TO DEPARTMENT =====
        if (doctorId == null) {
            throw new RuntimeException("Please select a doctor");
        }

        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        if (doctor.getRole() != UserRole.DOCTOR) {
            throw new RuntimeException("Selected user is not a doctor");
        }

        boolean isInDepartment = departmentRepository.doctorBelongsToDepartment(doctor.getId(), department.getId());
        if (!isInDepartment) {
            throw new RuntimeException("Doctor does not belong to this department");
        }

        // ===== GET PATIENT =====
        User patient = userRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        // ===== DETERMINE PRIORITY BASED ON PROFILE DATA =====
        boolean isBooked = appointmentId != null;
        Priority patientPriority = calculatePreviewPriority(patient, isBooked);
        log.info("Preview priority for patient {}: {}", patient.getUsername(), patientPriority);

        // ===== COUNT TICKETS IN THIS DOCTOR'S QUEUE =====
        int doctorQueueCount = ticketRepository.countActiveTicketsForDoctor(doctor.getId());
        log.info("Doctor {} has {} active tickets", doctor.getUsername(), doctorQueueCount);

        // ===== CALCULATE POSITION =====
        int estimatedPosition = doctorQueueCount + 1;
        int estimatedWaitMinutes = calculateWaitTimeFromPosition(estimatedPosition, patientPriority);

        // ===== GET PATIENTS AHEAD IN THIS DOCTOR'S QUEUE =====
        List<PatientAhead> patientsAhead = getPatientsAheadForDoctor(doctor.getId(), 10);

        // ===== BUILD POSITION ESTIMATES FOR THIS DOCTOR =====
        List<PositionEstimate> positionEstimates = new ArrayList<>();
        for (int i = 1; i <= Math.min(doctorQueueCount + 1, 10); i++) {
            int waitTime = calculateWaitTimeFromPosition(i, patientPriority);
            positionEstimates.add(new PositionEstimate(i, waitTime));
        }

        // ===== GENERATE PATIENT-FRIENDLY MESSAGE =====
        String positionMessage;
        if (estimatedPosition == 1) {
            positionMessage = "🎯 You are next in line for Dr. " + doctor.getFirstName() + " " + doctor.getLastName() +
                    "! Please be ready for your consultation.";
        } else if (estimatedPosition <= 3) {
            positionMessage = "📋 You are #" + estimatedPosition + " in Dr. " + doctor.getLastName() + "'s queue. " +
                    "Estimated wait: " + estimatedWaitMinutes + " minutes. Please stay nearby.";
        } else if (estimatedPosition <= 6) {
            positionMessage = "📋 You are #" + estimatedPosition + " in Dr. " + doctor.getLastName() + "'s queue. " +
                    "Estimated wait: " + estimatedWaitMinutes + " minutes. You have time to relax.";
        } else {
            positionMessage = "📋 You are #" + estimatedPosition + " in Dr. " + doctor.getLastName() + "'s queue. " +
                    "Estimated wait: " + estimatedWaitMinutes + " minutes. You'll get a notification when it's your turn.";
        }

        // Build response
        return QueuePreview.builder()
                .facilityName(facility.getName())
                .departmentName(department.getName())
                .doctorName("Dr. " + doctor.getFirstName() + " " + doctor.getLastName())
                .totalPatientsAhead(doctorQueueCount)
                .bookedPatientsAhead(0) // Not used in doctor-specific queue
                .walkInPatientsAhead(0)  // Not used in doctor-specific queue
                .estimatedPosition(estimatedPosition)
                .estimatedWaitMinutes(estimatedWaitMinutes)
                .patientPriority(patientPriority.name())
                .isBooked(isBooked)
                .hasUpcomingBooking(false)
                .patientsAhead(patientsAhead)
                .positionEstimates(positionEstimates)
                .currentTime(LocalDateTime.now())
                .message(positionMessage)
                .isFirstInLine(estimatedPosition == 1)
                .isNearFront(estimatedPosition >= 2 && estimatedPosition <= 3)
                .hasLongWait(estimatedPosition > 6)
                .build();
    }

    // ===== Helper: Get patients ahead for a specific doctor =====
    private List<PatientAhead> getPatientsAheadForDoctor(UUID doctorId, int limit) {
        List<Ticket> doctorTickets = ticketRepository.findTicketsForDoctor(doctorId);

        // Only include waiting tickets (TRIAGED, LAB_PENDING, LAB_COMPLETED)
        // IN_CONSULTATION is being seen now, CONSULTATION_DONE is done
        List<Ticket> waitingTickets = doctorTickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.TRIAGED ||
                        t.getStatus() == TicketStatus.LAB_PENDING ||
                        t.getStatus() == TicketStatus.LAB_COMPLETED)
                .collect(Collectors.toList());

        if (waitingTickets.size() > limit) {
            waitingTickets = waitingTickets.subList(0, limit);
        }

        return waitingTickets.stream()
                .map(ticket -> PatientAhead.builder()
                        .position(ticket.getQueuePosition())
                        .patientName(ticket.getPatient().getFirstName() + " " + ticket.getPatient().getLastName())
                        .priority(ticket.getPriority().name())
                        .estimatedWaitMinutes(ticket.getEstimatedWaitMinutes())
                        .isBooked(ticket.isBooked())
                        .ticketNumber(ticket.getTicketNumber())
                        .build())
                .collect(Collectors.toList());
    }

    // ===== Calculate priority for preview based ONLY on profile data =====
    private Priority calculatePreviewPriority(User patient, boolean isBooked) {
        // Start with base priority based on booking status
        Priority priority = isBooked ? Priority.HIGH : Priority.LOW;

        log.info("Calculating preview priority for patient: {}, Base priority: {}",
                patient.getUsername(), priority);

        // ===== 1. CHECK CHRONIC CONDITIONS (from profile) =====
        String chronicConditions = patient.getChronicConditions();
        if (chronicConditions != null && !chronicConditions.isEmpty()) {
            String[] conditions = chronicConditions.split(",");
            int conditionCount = conditions.length;

            log.info("Patient has {} chronic conditions: {}", conditionCount, chronicConditions);

            if (conditionCount >= 3) {
                priority = Priority.HIGH;
                log.info("Multiple chronic conditions ({}) -> HIGH priority", conditionCount);
            } else if (conditionCount >= 1 && priority == Priority.LOW) {
                priority = Priority.MEDIUM;
                log.info("Chronic condition -> MEDIUM priority");
            } else if (conditionCount >= 1 && priority == Priority.MEDIUM) {
                priority = Priority.HIGH;
                log.info("Chronic condition + other factors -> HIGH priority");
            }
        }

        // ===== 2. CHECK AGE (from profile) =====
        Integer age = patient.getAge();
        if (age != null && age > 0) {
            if (age >= 65) {
                if (priority == Priority.LOW) {
                    priority = Priority.MEDIUM;
                } else if (priority == Priority.MEDIUM) {
                    priority = Priority.HIGH;
                }
                log.info("Elderly patient ({} years) -> priority increased to {}", age, priority);
            } else if (age < 5) {
                if (priority == Priority.LOW) {
                    priority = Priority.MEDIUM;
                }
                log.info("Young child ({} years) -> priority increased to {}", age, priority);
            }
        }

        log.info("Final preview priority for patient {}: {}", patient.getUsername(), priority);
        return priority;
    }

    // ===== Inner Classes =====
    @Data
    @Builder
    public static class QueuePreview {
        private String facilityName;
        private String departmentName;
        private String doctorName;  // ← ADD THIS
        private long totalPatientsAhead;
        private long bookedPatientsAhead;
        private long walkInPatientsAhead;
        private int estimatedPosition;
        private int estimatedWaitMinutes;
        private String patientPriority;
        private boolean isBooked;
        private boolean hasUpcomingBooking;
        private List<PatientAhead> patientsAhead;
        private List<PositionEstimate> positionEstimates;
        private LocalDateTime currentTime;

        // ===== Patient-friendly message fields =====
        private String message;
        private Boolean isFirstInLine;
        private Boolean isNearFront;
        private Boolean hasLongWait;
    }

    @Data
    @Builder
    public static class PatientAhead {
        private int position;
        private String patientName;
        private String priority;
        private int estimatedWaitMinutes;
        private boolean isBooked;
        private String ticketNumber;
    }

    @Data
    @AllArgsConstructor
    public static class PositionEstimate {
        private int position;
        private int estimatedWaitMinutes;
    }
}