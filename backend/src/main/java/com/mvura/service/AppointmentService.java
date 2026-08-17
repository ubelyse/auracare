package com.mvura.service;

import com.mvura.controller.AppointmentController;
import com.mvura.model.*;
import com.mvura.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final FacilityRepository facilityRepository;
    private final DepartmentRepository departmentRepository;
    private final TicketRepository ticketRepository;
    private final AuditService auditService;
    private final EmailService emailService;

    private static final int CHECK_IN_OPEN_MINUTES = 30;
    private static final int CHECK_IN_CLOSE_MINUTES = 15;

    @Value("${app.appointments.cancellation-window-hours:2}")
    private int cancellationWindowHours;

    @Value("${app.appointments.max-per-day:5}")
    private int maxAppointmentsPerDay;

    @Value("${app.appointments.reminder-hours-before:24}")
    private int reminderHoursBefore;

    // ==================== 1. APPOINTMENT BOOKING (WITH VALIDATION) ====================

    @Transactional
    public Appointment bookAppointment(UUID patientId, UUID facilityId, UUID departmentId,
                                       UUID doctorId, LocalDateTime appointmentTime) {

        // ===== VALIDATION 1: Check if patient already has an active ticket =====
        if (ticketRepository.hasActiveTicket(patientId)) {
            throw new RuntimeException("You already have an active ticket. Please complete your current visit before booking another appointment.");
        }

        // ===== VALIDATION 2: Check daily limit =====
        validateDailyLimit(patientId, appointmentTime);

        // ===== VALIDATION 3: Check if time is in the past =====
        if (appointmentTime.isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cannot book appointments in the past");
        }

        // ===== VALIDATION 4: Check if time is within working hours =====
        validateWorkingHours(appointmentTime);

        // ===== VALIDATION 5: Check if appointment is too soon =====
        if (appointmentTime.isBefore(LocalDateTime.now().plusMinutes(30))) {
            throw new RuntimeException("Appointments must be booked at least 30 minutes in advance");
        }

        // ===== LOAD ENTITIES =====
        User patient = userRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        User doctor = null;
        if (doctorId != null) {
            doctor = userRepository.findById(doctorId)
                    .orElseThrow(() -> new RuntimeException("Doctor not found"));

            if (doctor.getRole() != UserRole.DOCTOR) {
                throw new RuntimeException("Selected user is not a doctor");
            }

            // Check if doctor is available at this time
            validateDoctorAvailability(doctorId, appointmentTime);
        }

        // ===== CHECK SLOT AVAILABILITY =====
        validateSlotAvailability(facilityId, departmentId, doctorId, appointmentTime);

        // ===== CALCULATE CHECK-IN WINDOW =====
        LocalDateTime checkInOpens = appointmentTime.minusMinutes(CHECK_IN_OPEN_MINUTES);
        LocalDateTime checkInCloses = appointmentTime.plusMinutes(CHECK_IN_CLOSE_MINUTES);

        // ===== CREATE APPOINTMENT =====
        Appointment appointment = Appointment.builder()
                .patient(patient)
                .facility(facility)
                .department(department)
                .doctor(doctor)
                .appointmentDateTime(appointmentTime)
                .checkInOpens(checkInOpens)
                .checkInCloses(checkInCloses)
                .status(AppointmentStatus.SCHEDULED)
                .build();

        Appointment saved = appointmentRepository.save(appointment);

        // ===== AUDIT LOG =====
        auditService.logAction(
                "APPOINTMENT_BOOKED",
                "APPOINTMENT",
                saved.getId().toString(),
                patient.getUsername(),
                null,
                null,
                Map.of(
                        "appointmentTime", appointmentTime,
                        "facilityId", facilityId,
                        "departmentId", departmentId,
                        "doctorId", doctorId
                )
        );

        log.info("Appointment booked for patient {} at {}", patient.getUsername(), appointmentTime);
        return saved;
    }

    // ==================== 2. APPOINTMENT RESCHEDULING ====================

    @Transactional
    public Appointment rescheduleAppointment(UUID appointmentId, UUID patientId,
                                             LocalDateTime newTime, String reason) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new RuntimeException("This appointment does not belong to you");
        }

        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new RuntimeException("This appointment cannot be rescheduled");
        }

        // ===== VALIDATE NEW TIME =====
        validateWorkingHours(newTime);
        validateDailyLimit(patientId, newTime);

        if (newTime.isBefore(LocalDateTime.now().plusMinutes(30))) {
            throw new RuntimeException("Appointments must be booked at least 30 minutes in advance");
        }

        // ===== CHECK AVAILABILITY =====
        validateSlotAvailability(
                appointment.getFacility().getId(),
                appointment.getDepartment().getId(),
                appointment.getDoctor() != null ? appointment.getDoctor().getId() : null,
                newTime
        );

        // ===== CHECK DOCTOR AVAILABILITY =====
        if (appointment.getDoctor() != null) {
            validateDoctorAvailability(appointment.getDoctor().getId(), newTime);
        }

        // ===== STORE OLD TIME FOR AUDIT =====
        LocalDateTime oldTime = appointment.getAppointmentDateTime();

        // ===== UPDATE APPOINTMENT =====
        appointment.setAppointmentDateTime(newTime);
        appointment.setCheckInOpens(newTime.minusMinutes(CHECK_IN_OPEN_MINUTES));
        appointment.setCheckInCloses(newTime.plusMinutes(CHECK_IN_CLOSE_MINUTES));

        Appointment saved = appointmentRepository.save(appointment);

        // ===== AUDIT LOG =====
        auditService.logAction(
                "APPOINTMENT_RESCHEDULED",
                "APPOINTMENT",
                appointmentId.toString(),
                appointment.getPatient().getUsername(),
                null,
                null,
                Map.of(
                        "oldTime", oldTime,
                        "newTime", newTime,
                        "reason", reason
                )
        );

        log.info("Appointment {} rescheduled from {} to {}", appointmentId, oldTime, newTime);
        return saved;
    }

    // ==================== 3. RECURRING APPOINTMENTS ====================

    @Transactional
    public List<Appointment> bookRecurringAppointments(UUID patientId, UUID facilityId, UUID departmentId,
                                                       UUID doctorId, LocalDateTime startDate, LocalDateTime endDate,
                                                       String recurrencePattern, List<String> daysOfWeek) {
        List<Appointment> appointments = new ArrayList<>();
        LocalDateTime current = startDate;

        while (!current.isAfter(endDate)) {
            // Check if this day matches the recurrence pattern
            if (shouldIncludeDate(current, recurrencePattern, daysOfWeek)) {
                try {
                    // Direct call - no self-invocation issue since we're calling the same method
                    Appointment appointment = bookAppointment(
                            patientId, facilityId, departmentId, doctorId, current
                    );
                    appointments.add(appointment);
                    log.info("Created recurring appointment for {}", current);
                } catch (Exception e) {
                    log.warn("Could not book appointment for {}: {}", current, e.getMessage());
                }
            }
            current = current.plusDays(1);
        }

        // ===== AUDIT LOG =====
        auditService.logAction(
                "RECURRING_APPOINTMENTS_BOOKED",
                "APPOINTMENT",
                null,
                "SYSTEM",
                null,
                null,
                Map.of(
                        "patientId", patientId,
                        "startDate", startDate,
                        "endDate", endDate,
                        "pattern", recurrencePattern,
                        "count", appointments.size()
                )
        );

        log.info("Created {} recurring appointments for patient from {} to {}",
                appointments.size(), startDate, endDate);
        return appointments;
    }

    private boolean shouldIncludeDate(LocalDateTime date, String pattern, List<String> daysOfWeek) {
        return switch (pattern) {
            case "DAILY" -> true;
            case "WEEKLY" -> daysOfWeek.contains(date.getDayOfWeek().toString());
            case "MONTHLY" -> true; // Simplified - you can add more logic
            default -> false;
        };
    }

    // ==================== 4. DOCTOR SCHEDULE MANAGEMENT ====================

    @Transactional
    public List<Appointment> createDoctorSchedule(UUID doctorId, LocalDate startDate, LocalDate endDate,
                                                  LocalTime startTime, LocalTime endTime, int durationMinutes) {
        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        if (doctor.getRole() != UserRole.DOCTOR) {
            throw new RuntimeException("User is not a doctor");
        }

        List<Appointment> appointments = new ArrayList<>();
        LocalDateTime current = startDate.atTime(startTime);

        while (!current.toLocalDate().isAfter(endDate)) {
            while (current.toLocalTime().isBefore(endTime)) {
                if (!isSlotBooked(current, doctorId)) {
                    Appointment appointment = createAppointmentSlot(current, doctor);
                    appointments.add(appointmentRepository.save(appointment));
                }
                current = current.plusMinutes(durationMinutes);
            }
            current = current.plusDays(1).with(startTime);
        }

        // ===== AUDIT LOG =====
        auditService.logAction(
                "DOCTOR_SCHEDULE_CREATED",
                "DOCTOR_SCHEDULE",
                doctorId.toString(),
                "SYSTEM",
                null,
                null,
                Map.of(
                        "startDate", startDate,
                        "endDate", endDate,
                        "startTime", startTime,
                        "endTime", endTime,
                        "durationMinutes", durationMinutes,
                        "appointmentsCreated", appointments.size()
                )
        );

        log.info("Created {} appointment slots for doctor {} from {} to {}",
                appointments.size(), doctorId, startDate, endDate);
        return appointments;
    }

    private Appointment createAppointmentSlot(LocalDateTime time, User doctor) {
        Facility facility = doctor.getPrimaryFacility();
        Department department = doctor.getPrimaryDepartment();

        if (facility == null || department == null) {
            throw new RuntimeException("Doctor has no primary facility or department assigned");
        }

        return Appointment.builder()
                .doctor(doctor)
                .facility(facility)
                .department(department)
                .appointmentDateTime(time)
                .checkInOpens(time.minusMinutes(CHECK_IN_OPEN_MINUTES))
                .checkInCloses(time.plusMinutes(CHECK_IN_CLOSE_MINUTES))
                .status(AppointmentStatus.SCHEDULED)
                .build();
    }

    @Transactional
    public void removeDoctorSchedule(UUID doctorId, LocalDate startDate, LocalDate endDate) {
        List<Appointment> appointments = appointmentRepository.findByDoctorIdAndAppointmentDateTimeBetween(
                doctorId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59)
        );

        appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED)
                .forEach(a -> a.setStatus(AppointmentStatus.CANCELLED));

        appointmentRepository.saveAll(appointments);

        long cancelled = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CANCELLED)
                .count();

        log.info("Removed {} appointment slots for doctor {} from {} to {}",
                cancelled, doctorId, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<Appointment> getDoctorSchedule(UUID doctorId, LocalDate startDate, LocalDate endDate) {
        return appointmentRepository.findByDoctorIdAndAppointmentDateTimeBetween(
                doctorId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59)
        );
    }

    @Transactional(readOnly = true)
    public List<Appointment> getAvailableSlotsForDoctor(UUID doctorId, LocalDate date) {
        return appointmentRepository.findAvailableSlotsForDoctor(doctorId, date);
    }

    // ==================== APPOINTMENT REMINDERS ====================

    @Scheduled(cron = "0 0 8 * * *") // Run daily at 8 AM
    public void sendAppointmentReminders() {
        LocalDateTime reminderTime = LocalDateTime.now().plusHours(reminderHoursBefore);
        LocalDateTime startWindow = reminderTime.minusHours(1);
        LocalDateTime endWindow = reminderTime.plusHours(1);

        List<Appointment> upcomingAppointments = appointmentRepository
                .findByAppointmentDateTimeBetweenAndStatus(startWindow, endWindow, AppointmentStatus.SCHEDULED);

        int remindersSent = 0;
        for (Appointment appointment : upcomingAppointments) {
            try {
                sendReminderForAppointment(appointment);
                remindersSent++;
            } catch (Exception e) {
                log.error("Failed to send reminder for appointment {}: {}", appointment.getId(), e.getMessage());
            }
        }

        log.info("Sent {} appointment reminders for tomorrow", remindersSent);
    }

    @Scheduled(cron = "0 30 9 * * *") // Run daily at 9:30 AM
    public void sendCheckInReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetTime = now.plusMinutes(30);
        LocalDateTime startWindow = targetTime.minusMinutes(5);
        LocalDateTime endWindow = targetTime.plusMinutes(5);

        List<Appointment> appointments = appointmentRepository
                .findByAppointmentDateTimeBetweenAndStatus(startWindow, endWindow, AppointmentStatus.SCHEDULED);

        int remindersSent = 0;
        for (Appointment appointment : appointments) {
            try {
                sendCheckInReminder(appointment);
                remindersSent++;
            } catch (Exception e) {
                log.error("Failed to send check-in reminder for appointment {}: {}", appointment.getId(), e.getMessage());
            }
        }

        log.info("Sent {} check-in reminders", remindersSent);
    }

    private void sendReminderForAppointment(Appointment appointment) {
        User patient = appointment.getPatient();
        String subject = "Appointment Reminder - " + appointment.getFacility().getName();
        String body = buildReminderEmail(appointment);

        try {
            emailService.sendHtmlEmail(patient.getEmail(), subject, body);
            log.info("Reminder sent for appointment {} to patient {}", appointment.getId(), patient.getUsername());
        } catch (MessagingException e) {
            log.error("Failed to send email reminder for appointment {}: {}", appointment.getId(), e.getMessage());
            throw new RuntimeException("Failed to send email reminder: " + e.getMessage(), e);
        }
    }

    private void sendCheckInReminder(Appointment appointment) {
        User patient = appointment.getPatient();
        String subject = "Time to Check In! - " + appointment.getFacility().getName();
        String body = buildCheckInReminderEmail(appointment);

        try {
            emailService.sendHtmlEmail(patient.getEmail(), subject, body);
            log.info("Check-in reminder sent for appointment {} to patient {}", appointment.getId(), patient.getUsername());
        } catch (MessagingException e) {
            log.error("Failed to send check-in reminder for appointment {}: {}", appointment.getId(), e.getMessage());
            throw new RuntimeException("Failed to send check-in reminder: " + e.getMessage(), e);
        }
    }

    // ==================== VALIDATION HELPERS ====================

    private void validateDailyLimit(UUID patientId, LocalDateTime appointmentTime) {
        LocalDate date = appointmentTime.toLocalDate();
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);

        long todayAppointments = appointmentRepository.countByPatientIdAndAppointmentDateTimeBetween(
                patientId, startOfDay, endOfDay
        );

        if (todayAppointments >= maxAppointmentsPerDay) {
            throw new RuntimeException("You have reached the maximum number of appointments for this day (" +
                    maxAppointmentsPerDay + ")");
        }
    }

    private void validateWorkingHours(LocalDateTime time) {
        int hour = time.getHour();

        if (hour < 8 || hour >= 17) {
            throw new RuntimeException("Appointments are only available between 8:00 AM and 5:00 PM");
        }
    }

    private void validateDoctorAvailability(UUID doctorId, LocalDateTime time) {
        List<Appointment> doctorAppointments = appointmentRepository
                .findByDoctorIdAndAppointmentDateTimeBetween(
                        doctorId,
                        time.minusMinutes(30),
                        time.plusMinutes(30)
                );

        if (!doctorAppointments.isEmpty()) {
            throw new RuntimeException("Doctor is already booked at this time");
        }
    }

    private void validateSlotAvailability(UUID facilityId, UUID departmentId, UUID doctorId, LocalDateTime time) {
        List<Appointment> existing = appointmentRepository.findScheduledAppointmentsBetween(
                facilityId, departmentId,
                time.minusMinutes(30),
                time.plusMinutes(30)
        );

        // If a specific doctor is selected, also check doctor availability
        if (doctorId != null) {
            existing = existing.stream()
                    .filter(a -> a.getDoctor() != null && a.getDoctor().getId().equals(doctorId))
                    .collect(Collectors.toList());
        }

        if (!existing.isEmpty()) {
            throw new RuntimeException("This time slot is already booked. Please choose another time.");
        }
    }

    private boolean isSlotBooked(LocalDateTime time, UUID doctorId) {
        List<Appointment> existing = appointmentRepository.findByDoctorIdAndAppointmentDateTimeBetween(
                doctorId, time.minusMinutes(30), time.plusMinutes(30)
        );
        return !existing.isEmpty();
    }

    // ==================== REMINDER MESSAGE BUILDERS ====================

    private String buildReminderEmail(Appointment appointment) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<h2>Appointment Reminder</h2>");
        sb.append("<p>Dear ").append(appointment.getPatient().getFirstName()).append(",</p>");
        sb.append("<p>This is a reminder for your upcoming appointment:</p>");
        sb.append("<ul>");
        sb.append("<li><b>Date:</b> ").append(appointment.getAppointmentDateTime()).append("</li>");
        sb.append("<li><b>Facility:</b> ").append(appointment.getFacility().getName()).append("</li>");
        sb.append("<li><b>Department:</b> ").append(appointment.getDepartment().getName()).append("</li>");
        if (appointment.getDoctor() != null) {
            sb.append("<li><b>Doctor:</b> Dr. ").append(appointment.getDoctor().getFirstName())
                    .append(" ").append(appointment.getDoctor().getLastName()).append("</li>");
        }
        sb.append("</ul>");
        sb.append("<p><b>Check-in Window:</b> ").append(appointment.getCheckInOpens())
                .append(" to ").append(appointment.getCheckInCloses()).append("</p>");
        sb.append("<p>Please arrive on time. You can check in from ").append(appointment.getCheckInOpens()).append(".</p>");
        sb.append("<br>");
        sb.append("<p>Thank you,<br>MVURA Health Team</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String buildCheckInReminderEmail(Appointment appointment) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<h2>Time to Check In!</h2>");
        sb.append("<p>Dear ").append(appointment.getPatient().getFirstName()).append(",</p>");
        sb.append("<p>Your appointment is in 30 minutes. Please check in now.</p>");
        sb.append("<ul>");
        sb.append("<li><b>Time:</b> ").append(appointment.getAppointmentDateTime()).append("</li>");
        sb.append("<li><b>Facility:</b> ").append(appointment.getFacility().getName()).append("</li>");
        sb.append("</ul>");
        sb.append("<p>You can check in at the front desk or use the self-service kiosk.</p>");
        sb.append("<br>");
        sb.append("<p>Thank you,<br>MVURA Health Team</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    // ==================== ORIGINAL EXISTING METHODS ====================

    @Transactional
    public Ticket checkInFromAppointment(UUID appointmentId, UUID patientId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new RuntimeException("This appointment does not belong to you");
        }

        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new RuntimeException("This appointment is already checked in or cancelled");
        }

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(appointment.getCheckInOpens())) {
            throw new RuntimeException("Check-in opens at " + appointment.getCheckInOpens());
        }

        if (now.isAfter(appointment.getCheckInCloses())) {
            appointment.setStatus(AppointmentStatus.NO_SHOW);
            appointmentRepository.save(appointment);
            throw new RuntimeException("Check-in window has closed. Please re-book.");
        }

        int queuePosition = ticketRepository.countActiveTickets(
                appointment.getFacility().getId(),
                appointment.getDepartment().getId()
        ) + 1;

        int estimatedWaitMinutes = queuePosition * 10;

        Ticket ticket = Ticket.builder()
                .ticketNumber(generateTicketNumber(appointment.getFacility().getCode(), appointment.getDepartment().getCode()))
                .patient(appointment.getPatient())
                .facility(appointment.getFacility())
                .department(appointment.getDepartment())
                .status(TicketStatus.CHECKED_IN)
                .priority(Priority.HIGH)
                .symptoms("Booked appointment")
                .active(true)
                .isBooked(true)
                .appointmentId(appointment.getId())
                .appointmentTime(appointment.getAppointmentDateTime())
                .checkInOpens(appointment.getCheckInOpens())
                .checkInCloses(appointment.getCheckInCloses())
                .queuePosition(queuePosition)
                .estimatedWaitMinutes(estimatedWaitMinutes)
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);

        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        appointment.setTicketId(savedTicket.getId());
        appointmentRepository.save(appointment);

        auditService.logSecurityEvent(
                "APPOINTMENT_CHECK_IN",
                appointment.getPatient().getUsername(),
                appointment.getPatient().getId(),
                null,
                "Appointment: " + appointment.getId() + " checked in"
        );

        log.info("Patient {} checked in from appointment {}", appointment.getPatient().getUsername(), appointmentId);
        return savedTicket;
    }

    @Transactional(readOnly = true)
    public List<Appointment> getPatientAppointments(UUID patientId) {
        return appointmentRepository.findByPatientId(patientId);
    }

    @Transactional(readOnly = true)
    public List<Appointment> getUpcomingAppointments(UUID patientId) {
        return appointmentRepository.findUpcomingAppointmentsForPatient(patientId);
    }

    @Transactional(readOnly = true)
    public boolean hasUpcomingAppointment(UUID facilityId, UUID departmentId, LocalDateTime now) {
        LocalDateTime soon = now.plusMinutes(15);
        return appointmentRepository.hasUpcomingAppointment(facilityId, departmentId, now, soon);
    }

    @Transactional(readOnly = true)
    public List<Appointment> getScheduledAppointmentsForDepartment(UUID facilityId, UUID departmentId) {
        return appointmentRepository.findScheduledAppointmentsByFacilityAndDepartment(facilityId, departmentId);
    }

    private String generateTicketNumber(String facilityCode, String departmentCode) {
        String prefix = facilityCode + "-" + departmentCode + "-BK";
        String sequence = String.format("%04d", System.currentTimeMillis() % 9999 + 1);
        return prefix + "-" + sequence;
    }

    @Transactional
    public void cancelAppointment(UUID appointmentId, UUID patientId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new RuntimeException("This appointment does not belong to you");
        }

        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new RuntimeException("This appointment cannot be cancelled");
        }

        // Check cancellation window
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffTime = appointment.getAppointmentDateTime().minusHours(cancellationWindowHours);

        if (now.isAfter(cutoffTime)) {
            throw new RuntimeException("Appointments must be cancelled at least " +
                    cancellationWindowHours + " hours in advance");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);

        auditService.logAction(
                "APPOINTMENT_CANCELLED",
                "APPOINTMENT",
                appointmentId.toString(),
                appointment.getPatient().getUsername(),
                null,
                null,
                Map.of("appointmentTime", appointment.getAppointmentDateTime())
        );

        log.info("Appointment {} cancelled by patient {}", appointmentId, patientId);
    }

    @Transactional(readOnly = true)
    public Appointment getAppointment(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
    }

    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentHistory(UUID patientId) {
        return appointmentRepository.findAppointmentHistoryForPatient(patientId);
    }

    @Transactional(readOnly = true)
    public Page<Appointment> getUpcomingAppointmentsPaginated(UUID patientId, Pageable pageable) {
        return appointmentRepository.findUpcomingAppointmentsForPatient(patientId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Appointment> getAppointmentHistoryPaginated(UUID patientId, Pageable pageable, String status) {
        if (status != null && !status.isEmpty()) {
            return appointmentRepository.findHistoryByPatientAndStatus(patientId, status, pageable);
        }
        return appointmentRepository.findAppointmentHistoryForPatient(patientId, pageable);
    }

    @Transactional(readOnly = true)
    public List<LocalDateTime> getAvailableSlots(UUID facilityId, UUID departmentId, UUID doctorId, LocalDate date) {
        List<LocalDateTime> slots = new ArrayList<>();
        LocalDateTime start = date.atTime(8, 0);
        LocalDateTime end = date.atTime(17, 0);

        while (start.isBefore(end)) {
            if (isSlotAvailable(start, facilityId, departmentId, doctorId)) {
                slots.add(start);
            }
            start = start.plusMinutes(30);
        }
        return slots;
    }

    private boolean isSlotAvailable(LocalDateTime time, UUID facilityId, UUID departmentId, UUID doctorId) {
        try {
            // Check if slot is already booked
            List<Appointment> existing = appointmentRepository.findScheduledAppointmentsBetween(
                    facilityId, departmentId, time.minusMinutes(30), time.plusMinutes(30)
            );

            if (!existing.isEmpty()) {
                return false;
            }

            // If doctor specified, check doctor availability
            if (doctorId != null) {
                List<Appointment> doctorAppointments = appointmentRepository
                        .findByDoctorIdAndAppointmentDateTimeBetween(doctorId, time.minusMinutes(30), time.plusMinutes(30));
                if (!doctorAppointments.isEmpty()) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("Error checking slot availability: {}", e.getMessage());
            return false;
        }
    }

    public AppointmentController.ValidationResult validateBooking(UUID facilityId, UUID departmentId, UUID doctorId, LocalDateTime time) {
        AppointmentController.ValidationResult.ValidationResultBuilder builder =
                AppointmentController.ValidationResult.builder();

        try {
            // Check if time is in the past
            if (time.isBefore(LocalDateTime.now())) {
                return builder
                        .valid(false)
                        .message("Cannot book appointments in the past")
                        .availableSlots(getAvailableSlots(facilityId, departmentId, doctorId, time.toLocalDate()))
                        .build();
            }

            // Check working hours
            validateWorkingHours(time);

            // Check slot availability
            validateSlotAvailability(facilityId, departmentId, doctorId, time);

            // Check doctor availability if specified
            if (doctorId != null) {
                validateDoctorAvailability(doctorId, time);
            }

            return builder
                    .valid(true)
                    .message("Slot is available")
                    .availableSlots(getAvailableSlots(facilityId, departmentId, doctorId, time.toLocalDate()))
                    .build();

        } catch (RuntimeException e) {
            return builder
                    .valid(false)
                    .message(e.getMessage())
                    .availableSlots(getAvailableSlots(facilityId, departmentId, doctorId, time.toLocalDate()))
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAppointmentStatistics(UUID patientId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", appointmentRepository.countByPatientId(patientId));
        stats.put("upcoming", appointmentRepository.countByPatientIdAndStatus(patientId, AppointmentStatus.SCHEDULED));
        stats.put("completed", appointmentRepository.countByPatientIdAndStatus(patientId, AppointmentStatus.COMPLETED));
        stats.put("cancelled", appointmentRepository.countByPatientIdAndStatus(patientId, AppointmentStatus.CANCELLED));
        stats.put("noShow", appointmentRepository.countByPatientIdAndStatus(patientId, AppointmentStatus.NO_SHOW));
        return stats;
    }

    public void sendAppointmentReminder(UUID appointmentId) {
        Appointment appointment = getAppointment(appointmentId);
        if (appointment.getStatus() == AppointmentStatus.SCHEDULED) {
            try {
                sendReminderForAppointment(appointment);
                log.info("Manually sent reminder for appointment {}", appointmentId);
            } catch (Exception e) {
                log.error("Failed to send reminder for appointment {}: {}", appointmentId, e.getMessage());
                throw new RuntimeException("Failed to send reminder: " + e.getMessage(), e);
            }
        } else {
            throw new RuntimeException("Cannot send reminder for appointment with status: " + appointment.getStatus());
        }
    }

    @Transactional(readOnly = true)
    public List<Appointment> getDoctorAppointmentsForDate(UUID doctorId, LocalDate date) {
        return appointmentRepository.findByDoctorIdAndAppointmentDateTimeBetween(
                doctorId, date.atStartOfDay(), date.atTime(23, 59, 59)
        );
    }

    @Transactional(readOnly = true)
    public Page<Appointment> getDoctorUpcomingAppointments(UUID doctorId, Pageable pageable) {
        return appointmentRepository.findByDoctorIdAndStatusAndAppointmentDateTimeAfter(
                doctorId, AppointmentStatus.SCHEDULED, LocalDateTime.now(), pageable
        );
    }

    @Transactional(readOnly = true)
    public Page<Appointment> getFacilityAppointmentsForDate(UUID facilityId, LocalDate date, Pageable pageable) {
        return appointmentRepository.findByFacilityIdAndAppointmentDateTimeBetween(
                facilityId, date.atStartOfDay(), date.atTime(23, 59, 59), pageable
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFacilityAppointmentStats(UUID facilityId, LocalDate date) {
        Map<String, Object> stats = new HashMap<>();

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);

        try {
            // Get all appointments for the day
            List<Appointment> dayAppointments = appointmentRepository.findByFacilityIdAndDateBetween(
                    facilityId, startOfDay, endOfDay
            );

            long total = dayAppointments.size();
            long scheduled = dayAppointments.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED)
                    .count();
            long completed = dayAppointments.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                    .count();
            long noShow = dayAppointments.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW)
                    .count();
            long cancelled = dayAppointments.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.CANCELLED)
                    .count();
            long checkedIn = dayAppointments.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.CHECKED_IN)
                    .count();

            stats.put("date", date);
            stats.put("totalAppointments", total);
            stats.put("scheduled", scheduled);
            stats.put("completed", completed);
            stats.put("noShow", noShow);
            stats.put("cancelled", cancelled);
            stats.put("checkedIn", checkedIn);
            stats.put("checkInRate", total > 0 ? (double) checkedIn / total * 100 : 0);
            stats.put("completionRate", total > 0 ? (double) completed / total * 100 : 0);
            stats.put("noShowRate", total > 0 ? (double) noShow / total * 100 : 0);

        } catch (Exception e) {
            log.error("Error getting appointment stats for facility {} on {}: {}",
                    facilityId, date, e.getMessage(), e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }
}