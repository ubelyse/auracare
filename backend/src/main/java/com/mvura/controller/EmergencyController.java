package com.mvura.controller;

import com.mvura.dto.FacilityDTO;
import com.mvura.dto.TicketDTO;
import com.mvura.model.Facility;
import com.mvura.model.Ticket;
import com.mvura.model.User;
import com.mvura.repository.UserRepository;
import com.mvura.service.EmergencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/emergency")
@RequiredArgsConstructor
@Slf4j
public class EmergencyController {

    private final EmergencyService emergencyService;
    private final UserRepository userRepository;

    private User getCurrentUser(Authentication auth) {
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
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

        return dto;
    }

    // ===== Helper method to convert Facility to FacilityDTO =====
    private FacilityDTO convertToFacilityDTO(Facility facility) {
        FacilityDTO dto = new FacilityDTO();
        dto.setId(facility.getId());
        dto.setName(facility.getName());
        dto.setCode(facility.getCode());
        dto.setAddress(facility.getAddress());
        dto.setPhone(facility.getPhone());
        dto.setEmail(facility.getEmail());
        dto.setActive(facility.isActive());
        return dto;
    }

    private List<FacilityDTO> convertToFacilityDTOList(List<Facility> facilities) {
        return facilities.stream().map(this::convertToFacilityDTO).collect(Collectors.toList());
    }

    @PostMapping("/activate")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> activateEmergency(
            @RequestParam UUID facilityId,
            @RequestParam UUID departmentId,
            @RequestParam(required = false, defaultValue = "30") int durationMinutes,
            Authentication auth) {

        UUID doctorId = getCurrentUser(auth).getId();

        log.info("Doctor {} activating emergency mode for facility {} department {}",
                doctorId, facilityId, departmentId);

        emergencyService.activateEmergencyMode(facilityId, departmentId, doctorId, durationMinutes);

        return ResponseEntity.ok(Map.of(
                "message", "Emergency mode activated",
                "facilityId", facilityId,
                "departmentId", departmentId
        ));
    }

    @PostMapping("/deactivate")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> deactivateEmergency(
            @RequestParam UUID facilityId,
            @RequestParam UUID departmentId,
            Authentication auth) {

        UUID doctorId = getCurrentUser(auth).getId();
        emergencyService.deactivateEmergencyMode(facilityId, departmentId, doctorId);

        return ResponseEntity.ok(Map.of(
                "message", "Emergency mode deactivated",
                "facilityId", facilityId,
                "departmentId", departmentId
        ));
    }

    // ===== FIXED: Returns TicketDTO =====
    @PostMapping("/choice")
    public ResponseEntity<?> handleEmergencyChoice(
            @RequestParam UUID ticketId,
            @RequestParam String choice,
            @RequestParam(required = false) UUID targetFacilityId,
            Authentication auth) {

        UUID requestingUserId = getCurrentUser(auth).getId();

        log.info("User {} responding to emergency choice for ticket {}: {} (target: {})",
                requestingUserId, ticketId, choice, targetFacilityId);

        Ticket ticket = emergencyService.handleEmergencyChoice(ticketId, choice, targetFacilityId, requestingUserId);
        TicketDTO ticketDTO = convertToTicketDTO(ticket);

        return ResponseEntity.ok(Map.of(
                "message", "Emergency choice processed",
                "ticket", ticketDTO,
                "status", ticket.getStatus()
        ));
    }

    // ===== FIXED: Returns FacilityDTO list =====
    @GetMapping("/available-facilities")
    public ResponseEntity<?> getAvailableFacilities(
            @RequestParam UUID facilityId,
            @RequestParam String departmentCode) {

        List<Facility> facilities = emergencyService.findAvailableFacilities(facilityId, departmentCode);
        List<FacilityDTO> facilityDTOs = convertToFacilityDTOList(facilities);

        log.info("Found {} available facilities for transfer", facilityDTOs.size());

        return ResponseEntity.ok(Map.of(
                "facilities", facilityDTOs,
                "count", facilityDTOs.size()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getEmergencyStatus(
            @RequestParam UUID facilityId,
            @RequestParam UUID departmentId) {

        return ResponseEntity.ok(emergencyService.getEmergencyStatus(facilityId, departmentId));
    }
}