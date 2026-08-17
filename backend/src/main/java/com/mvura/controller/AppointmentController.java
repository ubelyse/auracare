package com.mvura.controller;

import com.mvura.dto.AppointmentDTO;
import com.mvura.model.Appointment;
import com.mvura.model.Ticket;
import com.mvura.model.User;
import com.mvura.repository.UserRepository;
import com.mvura.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@Slf4j
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final UserRepository userRepository;

    // ==================== HELPER METHODS ====================

    private User getAuthenticatedUser(Authentication auth) {
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private AppointmentDTO convertToDTO(Appointment appointment) {
        AppointmentDTO dto = new AppointmentDTO();
        dto.setId(appointment.getId());
        dto.setPatientId(appointment.getPatient().getId());
        dto.setPatientName(appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName());
        dto.setFacilityId(appointment.getFacility().getId());
        dto.setFacilityName(appointment.getFacility().getName());
        dto.setDepartmentId(appointment.getDepartment().getId());
        dto.setDepartmentName(appointment.getDepartment().getName());
        dto.setAppointmentDateTime(appointment.getAppointmentDateTime());
        dto.setCheckInOpens(appointment.getCheckInOpens());
        dto.setCheckInCloses(appointment.getCheckInCloses());
        dto.setStatus(appointment.getStatus().name());
        if (appointment.getDoctor() != null) {
            dto.setDoctorId(appointment.getDoctor().getId());
            dto.setDoctorName(appointment.getDoctor().getFirstName() + " " + appointment.getDoctor().getLastName());
        }
        return dto;
    }

    // ==================== 1. BOOK APPOINTMENT ====================

    @PostMapping("/book")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> bookAppointment(
            @RequestParam UUID facilityId,
            @RequestParam UUID departmentId,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime appointmentTime,
            Authentication auth) {

        UUID patientId = getAuthenticatedUser(auth).getId();

        try {
            Appointment appointment = appointmentService.bookAppointment(
                    patientId, facilityId, departmentId, doctorId, appointmentTime
            );

            return ResponseEntity.ok(Map.of(
                    "message", "Appointment booked successfully",
                    "appointment", convertToDTO(appointment),
                    "checkInOpens", appointment.getCheckInOpens(),
                    "checkInCloses", appointment.getCheckInCloses()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== 2. CHECK-IN FROM APPOINTMENT ====================

    @PostMapping("/checkin")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> checkInFromAppointment(
            @RequestParam UUID appointmentId,
            Authentication auth) {

        UUID patientId = getAuthenticatedUser(auth).getId();

        try {
            Ticket ticket = appointmentService.checkInFromAppointment(appointmentId, patientId);

            return ResponseEntity.ok(Map.of(
                    "message", "Check-in successful",
                    "ticketNumber", ticket.getTicketNumber(),
                    "ticket", ticket,
                    "priority", "HIGH (Booked appointment)"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== 3. RESCHEDULE APPOINTMENT ====================

    @PutMapping("/reschedule/{appointmentId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> rescheduleAppointment(
            @PathVariable UUID appointmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime newTime,
            @RequestParam(required = false) String reason,
            Authentication auth) {

        UUID patientId = getAuthenticatedUser(auth).getId();

        try {
            Appointment appointment = appointmentService.rescheduleAppointment(
                    appointmentId, patientId, newTime, reason
            );

            return ResponseEntity.ok(Map.of(
                    "message", "Appointment rescheduled successfully",
                    "appointment", convertToDTO(appointment)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== 4. CANCEL APPOINTMENT ====================

    @PostMapping("/cancel/{appointmentId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> cancelAppointment(
            @PathVariable UUID appointmentId,
            Authentication auth) {

        UUID patientId = getAuthenticatedUser(auth).getId();

        try {
            appointmentService.cancelAppointment(appointmentId, patientId);

            return ResponseEntity.ok(Map.of(
                    "message", "Appointment cancelled successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== 5. GET UPCOMING APPOINTMENTS ====================

    @GetMapping("/upcoming")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getUpcomingAppointments(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID patientId = getAuthenticatedUser(auth).getId();
        Pageable pageable = PageRequest.of(page, size);

        Page<Appointment> appointments = appointmentService.getUpcomingAppointmentsPaginated(patientId, pageable);
        List<AppointmentDTO> dtos = appointments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "appointments", dtos,
                "totalElements", appointments.getTotalElements(),
                "totalPages", appointments.getTotalPages(),
                "currentPage", appointments.getNumber(),
                "pageSize", appointments.getSize()
        ));
    }

    // ==================== 6. GET APPOINTMENT HISTORY ====================

    @GetMapping("/history")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getAppointmentHistory(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        UUID patientId = getAuthenticatedUser(auth).getId();
        Pageable pageable = PageRequest.of(page, size);

        Page<Appointment> appointments = appointmentService.getAppointmentHistoryPaginated(patientId, pageable, status);
        List<AppointmentDTO> dtos = appointments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "appointments", dtos,
                "totalElements", appointments.getTotalElements(),
                "totalPages", appointments.getTotalPages(),
                "currentPage", appointments.getNumber(),
                "pageSize", appointments.getSize()
        ));
    }

    // ==================== 7. CHECK CHECK-IN WINDOW ====================

    @GetMapping("/check-window/{appointmentId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> checkAppointmentWindow(
            @PathVariable UUID appointmentId,
            Authentication auth) {

        UUID patientId = getAuthenticatedUser(auth).getId();

        try {
            Appointment appointment = appointmentService.getAppointment(appointmentId);

            if (!appointment.getPatient().getId().equals(patientId)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "This appointment does not belong to you"
                ));
            }

            LocalDateTime now = LocalDateTime.now();
            boolean canCheckIn = now.isAfter(appointment.getCheckInOpens()) &&
                    now.isBefore(appointment.getCheckInCloses());

            return ResponseEntity.ok(Map.of(
                    "canCheckIn", canCheckIn,
                    "checkInOpens", appointment.getCheckInOpens(),
                    "checkInCloses", appointment.getCheckInCloses(),
                    "status", appointment.getStatus(),
                    "minutesUntilCheckIn", appointment.getCheckInOpens().isAfter(now) ?
                            java.time.Duration.between(now, appointment.getCheckInOpens()).toMinutes() : 0,
                    "minutesUntilCheckInCloses", appointment.getCheckInCloses().isAfter(now) ?
                            java.time.Duration.between(now, appointment.getCheckInCloses()).toMinutes() : 0
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== 8. GET AVAILABLE SLOTS ====================

    @GetMapping("/available-slots")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getAvailableSlots(
            @RequestParam UUID facilityId,
            @RequestParam UUID departmentId,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        try {
            List<LocalDateTime> slots = appointmentService.getAvailableSlots(
                    facilityId, departmentId, doctorId, date
            );

            return ResponseEntity.ok(Map.of(
                    "date", date,
                    "availableSlots", slots,
                    "count", slots.size()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== 9. VALIDATE BOOKING ====================

    @PostMapping("/validate-booking")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> validateBooking(
            @RequestParam UUID facilityId,
            @RequestParam UUID departmentId,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime appointmentTime) {

        ValidationResult result = appointmentService.validateBooking(
                facilityId, departmentId, doctorId, appointmentTime
        );

        return ResponseEntity.ok(Map.of(
                "valid", result.isValid(),
                "message", result.getMessage(),
                "availableSlots", result.getAvailableSlots()
        ));
    }

    // ==================== 10. APPOINTMENT STATISTICS ====================

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getAppointmentStatistics(Authentication auth) {
        UUID patientId = getAuthenticatedUser(auth).getId();

        Map<String, Object> stats = appointmentService.getAppointmentStatistics(patientId);

        return ResponseEntity.ok(Map.of(
                "statistics", stats
        ));
    }

    // ==================== 11. GET APPOINTMENT BY ID ====================

    @GetMapping("/{appointmentId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getAppointment(@PathVariable UUID appointmentId, Authentication auth) {
        UUID patientId = getAuthenticatedUser(auth).getId();

        try {
            Appointment appointment = appointmentService.getAppointment(appointmentId);

            if (!appointment.getPatient().getId().equals(patientId)) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "You do not have access to this appointment"
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "appointment", convertToDTO(appointment)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== 12. SEND REMINDER ====================

    @PostMapping("/send-reminder/{appointmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACILITY_ADMIN')")
    public ResponseEntity<?> sendReminder(
            @PathVariable UUID appointmentId,
            Authentication auth) {

        try {
            appointmentService.sendAppointmentReminder(appointmentId);
            return ResponseEntity.ok(Map.of(
                    "message", "Reminder sent successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== 13. DOCTOR APPOINTMENTS ====================

    @GetMapping("/doctor/scheduled")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getDoctorAppointments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication auth) {

        User doctor = getAuthenticatedUser(auth);
        UUID doctorId = doctor.getId();

        List<Appointment> appointments = appointmentService.getDoctorAppointmentsForDate(
                doctorId, date
        );

        List<AppointmentDTO> dtos = appointments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "date", date,
                "appointments", dtos,
                "count", dtos.size()
        ));
    }

    @GetMapping("/doctor/upcoming")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getDoctorUpcomingAppointments(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User doctor = getAuthenticatedUser(auth);
        UUID doctorId = doctor.getId();
        Pageable pageable = PageRequest.of(page, size);

        Page<Appointment> appointments = appointmentService.getDoctorUpcomingAppointments(doctorId, pageable);
        List<AppointmentDTO> dtos = appointments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "appointments", dtos,
                "totalElements", appointments.getTotalElements(),
                "totalPages", appointments.getTotalPages(),
                "currentPage", appointments.getNumber(),
                "pageSize", appointments.getSize()
        ));
    }

    // ==================== 14. ADMIN APPOINTMENT MANAGEMENT ====================

    @GetMapping("/admin/facility/{facilityId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACILITY_ADMIN')")
    public ResponseEntity<?> getFacilityAppointments(
            @PathVariable UUID facilityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Appointment> appointments = appointmentService.getFacilityAppointmentsForDate(
                facilityId, date, pageable
        );

        List<AppointmentDTO> dtos = appointments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "date", date,
                "appointments", dtos,
                "totalElements", appointments.getTotalElements(),
                "totalPages", appointments.getTotalPages(),
                "currentPage", appointments.getNumber(),
                "pageSize", appointments.getSize()
        ));
    }

    @GetMapping("/admin/facility/{facilityId}/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACILITY_ADMIN')")
    public ResponseEntity<?> getFacilityAppointmentStats(
            @PathVariable UUID facilityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Map<String, Object> stats = appointmentService.getFacilityAppointmentStats(facilityId, date);

        return ResponseEntity.ok(Map.of(
                "date", date,
                "statistics", stats
        ));
    }

    // ==================== INNER CLASSES ====================

    @lombok.Data
    @lombok.Builder
    public static class ValidationResult {
        private boolean valid;
        private String message;
        private List<LocalDateTime> availableSlots;
    }

    @lombok.Data
    public static class BulkBookingRequest {
        private List<UUID> patientIds;
        private UUID facilityId;
        private UUID departmentId;
        private UUID doctorId;
        private List<LocalDateTime> appointmentTimes;
    }
}