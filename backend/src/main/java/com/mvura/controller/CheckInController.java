package com.mvura.controller;

import com.mvura.dto.DepartmentWithDoctorsDTO;
import com.mvura.dto.DoctorDTO;
import com.mvura.dto.FacilityDTO;
import com.mvura.dto.TicketDTO;
import com.mvura.dto.CheckInRequest;
import com.mvura.model.Department;
import com.mvura.model.Ticket;
import com.mvura.model.User;
import com.mvura.model.UserRole;
import com.mvura.repository.TicketRepository;
import com.mvura.repository.UserRepository;
import com.mvura.service.CheckInService;
import com.mvura.service.FacilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
@Slf4j
public class CheckInController {

    private final CheckInService checkInService;
    private final FacilityService facilityService;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;

    // Roles allowed to look up any ticket at their facility (not just their
    // own) — clinical/admin staff need this, patients don't.
    private static final Set<UserRole> STAFF_ROLES = Set.of(
            UserRole.DOCTOR, UserRole.STAFF, UserRole.FACILITY_ADMIN, UserRole.DISTRICT_ADMIN
    );

    private User getAuthenticatedUser(Authentication auth) {
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /**
     * A ticket may only be viewed by the patient it belongs to, or by staff
     * roles. This closes the IDOR that previously let anyone fetch any
     * patient's ticket (symptoms, priority, insurance, pregnancy status,
     * allergies) just by guessing a sequential ticket number.
     */
    private void assertCanViewTicket(Ticket ticket, User requester) {
        boolean isOwner = ticket.getPatient() != null
                && ticket.getPatient().getId().equals(requester.getId());
        boolean isStaff = STAFF_ROLES.contains(requester.getRole());

        if (!isOwner && !isStaff) {
            log.warn("Access denied: user {} attempted to view ticket {} belonging to another patient",
                    requester.getUsername(), ticket.getTicketNumber());
            throw new AccessDeniedException("You do not have permission to view this ticket");
        }
    }

    // ===== TEST ENDPOINT - Check if @PreAuthorize is working =====
    @GetMapping("/test-auth")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> testAuth(Authentication auth) {
        log.info("🔴🔴🔴🔴🔴 TEST-AUTH ENDPOINT REACHED! 🔴🔴🔴🔴🔴");
        try {
            User user = getAuthenticatedUser(auth);
            log.info("🔴 Test auth - User: {}, Role: {}", user.getUsername(), user.getRole());
            return ResponseEntity.ok(Map.of(
                    "message", "Authentication works!",
                    "username", user.getUsername(),
                    "role", user.getRole().name(),
                    "userId", user.getId().toString()
            ));
        } catch (Exception e) {
            log.error("🔴 Test auth error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ===== TEST ENDPOINT - Debug user role =====
    @GetMapping("/debug/role")
    public ResponseEntity<?> debugRole(Authentication auth) {
        log.info("🔴🔴🔴 DEBUG ROLE ENDPOINT REACHED 🔴🔴🔴");
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "error", "No authentication found"
                ));
            }
            User user = getAuthenticatedUser(auth);
            return ResponseEntity.ok(Map.of(
                    "username", user.getUsername(),
                    "role", user.getRole().name(),
                    "userId", user.getId().toString(),
                    "isAuthenticated", auth.isAuthenticated(),
                    "authorities", auth.getAuthorities().stream()
                            .map(a -> a.getAuthority())
                            .collect(Collectors.toList())
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> initiateCheckIn(@RequestBody CheckInRequest request, Authentication auth) {
        log.info("🔴 Initiate check-in called");
        UUID patientId = getAuthenticatedUser(auth).getId();
        request.setPatientId(patientId);

        Ticket ticket = checkInService.initiateCheckIn(request);
        log.info("Check-in initiated for patient: {}, Ticket: {}", patientId, ticket.getTicketNumber());

        TicketDTO ticketDTO = convertToTicketDTO(ticket);

        return ResponseEntity.ok(Map.of(
                "ticket", ticketDTO,
                "message", "Check-in successful"
        ));
    }

    @GetMapping("/ticket/{ticketNumber}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getTicket(@PathVariable String ticketNumber, Authentication auth) {
        log.info("🔴 Get ticket called for: {}", ticketNumber);
        User requester = getAuthenticatedUser(auth);
        Ticket ticket = checkInService.getTicketByNumber(ticketNumber);
        assertCanViewTicket(ticket, requester);

        TicketDTO ticketDTO = convertToTicketDTO(ticket);
        return ResponseEntity.ok(ticketDTO);
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getActiveTicket(Authentication auth) {
        log.info("🔴 Get active ticket called");
        try {
            User patient = getAuthenticatedUser(auth);
            log.info("🔴🔴🔴 getActiveTicket called for patient: {}", patient.getId());

            if (patient.getRole() != UserRole.PATIENT) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "User is not a patient"
                ));
            }

            Ticket ticket = checkInService.getActiveTicketForPatient(patient.getId());
            log.info("🔴🔴🔴 Ticket found: {}", ticket != null ? ticket.getTicketNumber() : "null");

            TicketDTO ticketDTO = convertToTicketDTO(ticket);
            log.info("🔴🔴🔴 TicketDTO created: {}", ticketDTO != null ? ticketDTO.getTicketNumber() : "null");

            return ResponseEntity.ok(ticketDTO);
        } catch (Exception e) {
            log.error("🔴🔴🔴 Error in getActiveTicket: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/has-active")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> hasActiveTicket(Authentication auth) {
        log.info("🔴🔴🔴 HAS-ACTIVE ENDPOINT CALLED 🔴🔴🔴");
        try {
            User patient = getAuthenticatedUser(auth);

            if (patient.getRole() != UserRole.PATIENT) {
                return ResponseEntity.ok(Map.of(
                        "hasActiveTicket", false,
                        "message", "User is not a patient"
                ));
            }

            log.info("Patient ID: {}", patient.getId());

            boolean hasActive = ticketRepository.hasActiveTicket(patient.getId());

            log.info("Patient {} has active ticket: {}", patient.getUsername(), hasActive);

            return ResponseEntity.ok(Map.of(
                    "hasActiveTicket", hasActive
            ));
        } catch (Exception e) {
            log.warn("Error checking active ticket: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "hasActiveTicket", false
            ));
        }
    }

    @GetMapping("/status/{ticketNumber}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getTicketStatus(@PathVariable String ticketNumber, Authentication auth) {
        log.info("🔴 Get ticket status called for: {}", ticketNumber);
        User requester = getAuthenticatedUser(auth);
        Ticket ticket = checkInService.getTicketByNumber(ticketNumber);
        assertCanViewTicket(ticket, requester);

        TicketDTO ticketDTO = convertToTicketDTO(ticket);
        return ResponseEntity.ok(ticketDTO);
    }

    // ===== PUBLIC ENDPOINTS - No authentication required =====
    @GetMapping("/facilities")
    public ResponseEntity<?> getFacilities() {
        log.info("📋 Getting all facilities");
        List<FacilityDTO> facilities = facilityService.getAllFacilitiesDTO();
        log.info("📋 Returning {} facilities", facilities.size());
        return ResponseEntity.ok(facilities);
    }

    @GetMapping("/facilities/{facilityId}/departments")
    public ResponseEntity<?> getDepartmentsWithDoctors(@PathVariable UUID facilityId) {
        log.info("📋 Getting departments for facility: {}", facilityId);
        List<Department> departments = facilityService.getDepartmentsByFacility(facilityId);

        List<DepartmentWithDoctorsDTO> response = departments.stream().map(dept -> {
            DepartmentWithDoctorsDTO dto = new DepartmentWithDoctorsDTO();
            dto.setId(dept.getId());
            dto.setName(dept.getName());
            dto.setCode(dept.getCode());
            dto.setDescription(dept.getDescription());
            dto.setActive(dept.isActive());

            List<User> doctors = checkInService.getAvailableDoctors(dept.getId());
            List<DoctorDTO> doctorDTOs = doctors.stream().map(doctor -> {
                DoctorDTO doctorDTO = new DoctorDTO();
                doctorDTO.setId(doctor.getId());
                doctorDTO.setFirstName(doctor.getFirstName());
                doctorDTO.setLastName(doctor.getLastName());
                doctorDTO.setEmail(doctor.getEmail());
                return doctorDTO;
            }).collect(Collectors.toList());

            dto.setAvailableDoctors(doctorDTOs);
            return dto;
        }).collect(Collectors.toList());

        log.info("📋 Returning {} departments for facility {}", response.size(), facilityId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/department/{departmentId}/doctors")
    public ResponseEntity<?> getAvailableDoctors(@PathVariable UUID departmentId) {
        log.info("📋 Getting doctors for department: {}", departmentId);
        List<User> doctors = checkInService.getAvailableDoctors(departmentId);

        List<DoctorDTO> doctorDTOs = doctors.stream().map(doctor -> {
            DoctorDTO doctorDTO = new DoctorDTO();
            doctorDTO.setId(doctor.getId());
            doctorDTO.setFirstName(doctor.getFirstName());
            doctorDTO.setLastName(doctor.getLastName());
            doctorDTO.setEmail(doctor.getEmail());
            return doctorDTO;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "doctors", doctorDTOs,
                "count", doctorDTOs.size()
        ));
    }

    // ===== PREVIEW ENDPOINT - Requires PATIENT role =====
    @GetMapping("/preview")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> previewQueueStatus(
            @RequestParam UUID facilityId,
            @RequestParam UUID departmentId,
            @RequestParam UUID doctorId,  // ← CHANGED: Now required
            @RequestParam(required = false) UUID appointmentId,
            Authentication auth) {

        log.info("🔴 PREVIEW ENDPOINT REACHED!");
        log.info("🔴 Facility: {}, Department: {}, Doctor: {}", facilityId, departmentId, doctorId);

        try {
            User patient = getAuthenticatedUser(auth);
            log.info("🔴 Preview called by user: {}, Role: {}", patient.getUsername(), patient.getRole());

            CheckInService.QueuePreview preview = checkInService.previewQueueStatus(
                    facilityId,
                    departmentId,
                    doctorId,  // ← Pass doctorId
                    patient.getId(),
                    appointmentId
            );

            log.info("🔴 Preview response: position={}, wait={}min",
                    preview.getEstimatedPosition(), preview.getEstimatedWaitMinutes());

            return ResponseEntity.ok(preview);
        } catch (RuntimeException e) {
            log.error("🔴 Preview error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("🔴 Unexpected preview error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "An unexpected error occurred: " + e.getMessage()
            ));
        }
    }

    // ===== Helper method to convert Ticket to TicketDTO =====
    private TicketDTO convertToTicketDTO(Ticket ticket) {
        TicketDTO dto = new TicketDTO();
        dto.setId(ticket.getId());
        dto.setTicketNumber(ticket.getTicketNumber());
        dto.setStatus(ticket.getStatus().name());
        dto.setPriority(ticket.getPriority().name());
        dto.setQueuePosition(ticket.getQueuePosition());
        dto.setEstimatedWaitMinutes(ticket.getEstimatedWaitMinutes());

        if (ticket.getFacility() != null) {
            dto.setFacilityId(ticket.getFacility().getId());
            dto.setFacilityName(ticket.getFacility().getName());
        }

        if (ticket.getDepartment() != null) {
            dto.setDepartmentId(ticket.getDepartment().getId());
            dto.setDepartmentName(ticket.getDepartment().getName());
            dto.setDepartmentCode(ticket.getDepartment().getCode());
        }

        // ===== GENERATE PATIENT-FRIENDLY MESSAGE =====
        int queuePosition = ticket.getQueuePosition() != null ? ticket.getQueuePosition() : 0;
        int waitTime = ticket.getEstimatedWaitMinutes() != null ? ticket.getEstimatedWaitMinutes() : 0;

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

        dto.setMessage(positionMessage);
        dto.setIsFirstInLine(queuePosition == 1);
        dto.setIsNearFront(queuePosition >= 2 && queuePosition <= 3);
        dto.setHasLongWait(queuePosition > 6);

        return dto;
    }
}