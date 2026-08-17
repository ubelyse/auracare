package com.mvura.controller;

import com.mvura.dto.TicketDTO;
import com.mvura.model.Billing;
import com.mvura.model.Consultation;
import com.mvura.model.ServicePricing;
import com.mvura.model.Ticket;
import com.mvura.model.User;
import com.mvura.repository.BillingRepository;
import com.mvura.repository.ConsultationRepository;
import com.mvura.repository.ServicePricingRepository;
import com.mvura.repository.TicketRepository;
import com.mvura.repository.UserRepository;
import com.mvura.service.AuditService;
import com.mvura.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorController {

    private final QueueService queueService;
    private final UserRepository userRepository;
    private final ServicePricingRepository servicePricingRepository;
    private final TicketRepository ticketRepository;
    private final AuditService auditService;
    private final ConsultationRepository consultationRepository;
    private final BillingRepository billingRepository;

    private User getDoctor(Authentication auth) {
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Doctor not found: " + username));
    }

    private UUID getDoctorId(Authentication auth) {
        return getDoctor(auth).getId();
    }

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

        dto.setSymptoms(ticket.getSymptoms());
        dto.setSanitizedSymptoms(ticket.getSanitizedSymptoms());
        dto.setAge(ticket.getAge());
        dto.setTriageScore(ticket.getTriageScore());
        dto.setTriageMethod(ticket.getTriageMethod());
        dto.setAiConfidence(ticket.getAiConfidence());
        dto.setIsBooked(ticket.isBooked());

        if (ticket.getPatient() != null) {
            dto.setPatientName(ticket.getPatient().getFirstName() + " " + ticket.getPatient().getLastName());
        }

        if (ticket.getAssignedDoctor() != null) {
            dto.setDoctorName("Dr. " + ticket.getAssignedDoctor().getFirstName() + " " + ticket.getAssignedDoctor().getLastName());
        }

        return dto;
    }

    private List<TicketDTO> convertToTicketDTOList(List<Ticket> tickets) {
        return tickets.stream().map(this::convertToTicketDTO).collect(Collectors.toList());
    }

    @GetMapping("/queue")
    public ResponseEntity<?> getDoctorQueue(Authentication auth) {
        UUID doctorId = getDoctorId(auth);
        List<Ticket> tickets = queueService.getDoctorQueue(doctorId);

        log.info("Doctor {} has {} tickets in queue", doctorId, tickets.size());

        List<TicketDTO> ticketDTOs = convertToTicketDTOList(tickets);

        return ResponseEntity.ok(Map.of(
                "tickets", ticketDTOs,
                "count", ticketDTOs.size()
        ));
    }

    @GetMapping("/department-queue")
    public ResponseEntity<?> getDepartmentQueue(
            @RequestParam UUID facilityId,
            @RequestParam UUID departmentId,
            Authentication auth) {

        User doctor = getDoctor(auth);
        boolean facilityMatches = doctor.getPrimaryFacility() != null
                && doctor.getPrimaryFacility().getId().equals(facilityId);
        boolean departmentMatches = doctor.getDepartments() != null
                && doctor.getDepartments().stream().anyMatch(d -> d.getId().equals(departmentId));

        if (!facilityMatches || !departmentMatches) {
            throw new AccessDeniedException("You do not have access to this facility/department");
        }

        List<Ticket> tickets = queueService.getDepartmentQueue(facilityId, departmentId);
        List<TicketDTO> ticketDTOs = convertToTicketDTOList(tickets);

        return ResponseEntity.ok(Map.of(
                "tickets", ticketDTOs,
                "count", ticketDTOs.size()
        ));
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> getQueueMetrics(
            @RequestParam UUID facilityId,
            @RequestParam UUID departmentId,
            Authentication auth) {
        log.info("Getting metrics for facility: {}, department: {}", facilityId, departmentId);

        Map<String, Object> metrics = queueService.getQueueMetrics(facilityId, departmentId);

        return ResponseEntity.ok(metrics);
    }

    // ===== START CONSULTATION =====
    @PostMapping("/consultation/start")
    public ResponseEntity<?> startConsultation(
            @RequestParam UUID ticketId,
            Authentication auth) {

        UUID doctorId = getDoctorId(auth);
        Ticket ticket = queueService.startConsultation(ticketId, doctorId);
        TicketDTO ticketDTO = convertToTicketDTO(ticket);

        return ResponseEntity.ok(Map.of(
                "message", "Consultation started",
                "ticket", ticketDTO
        ));
    }

    // ===== COMPLETE CONSULTATION =====
    @PostMapping("/consultation/complete")
    public ResponseEntity<?> completeConsultation(
            @RequestParam UUID ticketId,
            Authentication auth) {

        UUID doctorId = getDoctorId(auth);
        Ticket ticket = queueService.completeConsultation(ticketId, doctorId);
        TicketDTO ticketDTO = convertToTicketDTO(ticket);

        return ResponseEntity.ok(Map.of(
                "message", "Consultation completed",
                "ticket", ticketDTO
        ));
    }

    // ===== ORDER SINGLE LAB TEST =====
    @PostMapping("/lab/order")
    public ResponseEntity<?> orderLabTest(
            @RequestParam UUID ticketId,
            @RequestParam String serviceCode,
            Authentication auth) {

        UUID doctorId = getDoctorId(auth);

        ServicePricing pricing = servicePricingRepository.findByServiceCode(serviceCode)
                .filter(service -> "LAB".equalsIgnoreCase(service.getCategory()))
                .filter(ServicePricing::isActive)
                .orElseThrow(() -> new RuntimeException("Lab service not found or inactive: " + serviceCode));

        log.info("✅ Lab service validated: {} - Price: {}", pricing.getServiceName(), pricing.getBasePrice());

        Ticket ticket = queueService.orderLabTestWithServiceCode(ticketId, serviceCode, doctorId);
        TicketDTO ticketDTO = convertToTicketDTO(ticket);

        return ResponseEntity.ok(Map.of(
                "message", "Lab test ordered",
                "ticket", ticketDTO,
                "serviceCode", serviceCode,
                "serviceName", pricing.getServiceName(),
                "price", pricing.getBasePrice()
        ));
    }

    // ===== BATCH ORDER LAB TESTS =====
    @PostMapping("/lab/batch-order")
    public ResponseEntity<?> batchOrderLabTests(
            @RequestParam UUID ticketId,
            @RequestBody List<String> serviceCodes,
            Authentication auth) {

        UUID doctorId = getDoctorId(auth);
        log.info("Batch ordering {} lab tests for ticket: {}", serviceCodes.size(), ticketId);

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        User doctor = getDoctor(auth);
        if (ticket.getAssignedDoctor() == null || !ticket.getAssignedDoctor().getId().equals(doctor.getId())) {
            throw new AccessDeniedException("You are not the assigned doctor for this ticket");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        List<String> errors = new ArrayList<>();

        for (String serviceCode : serviceCodes) {
            try {
                ServicePricing pricing = servicePricingRepository.findByServiceCode(serviceCode)
                        .filter(service -> "LAB".equalsIgnoreCase(service.getCategory()))
                        .filter(ServicePricing::isActive)
                        .orElseThrow(() -> new RuntimeException("Lab service not found or inactive: " + serviceCode));

                queueService.orderLabTestWithServiceCode(ticketId, serviceCode, doctorId);

                results.add(Map.of(
                        "serviceCode", serviceCode,
                        "status", "success",
                        "serviceName", pricing.getServiceName()
                ));
                successCount++;
                log.info("✅ Lab ordered: {} ({})", pricing.getServiceName(), serviceCode);

            } catch (Exception e) {
                log.error("Failed to order lab: {}", serviceCode, e);
                results.add(Map.of(
                        "serviceCode", serviceCode,
                        "status", "failed",
                        "error", e.getMessage()
                ));
                errors.add(serviceCode + ": " + e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", String.format("Ordered %d of %d labs successfully", successCount, serviceCodes.size()));
        response.put("successCount", successCount);
        response.put("totalCount", serviceCodes.size());
        response.put("results", results);
        if (!errors.isEmpty()) {
            response.put("errors", errors);
        }

        auditService.logAction(
                "BATCH_LAB_ORDER",
                "TICKET",
                ticketId.toString(),
                doctor.getUsername(),
                null,
                null,
                Map.of(
                        "ticketNumber", ticket.getTicketNumber(),
                        "serviceCodes", serviceCodes,
                        "successCount", successCount,
                        "failedCount", serviceCodes.size() - successCount
                )
        );

        log.info("Batch lab order completed: {} of {} successful for ticket {}",
                successCount, serviceCodes.size(), ticket.getTicketNumber());

        return ResponseEntity.ok(response);
    }

    // ===== COMPLETE LAB TEST =====
    @PostMapping("/lab/complete")
    public ResponseEntity<?> completeLabTest(
            @RequestParam UUID ticketId,
            @RequestParam String result,
            Authentication auth) {

        UUID doctorId = getDoctorId(auth);
        Ticket ticket = queueService.completeLabTest(ticketId, result, doctorId);
        TicketDTO ticketDTO = convertToTicketDTO(ticket);

        return ResponseEntity.ok(Map.of(
                "message", "Lab test completed",
                "ticket", ticketDTO
        ));
    }

    @GetMapping("/lab-services")
    public ResponseEntity<?> getLabServices(Authentication auth) {
        try {
            User doctor = getDoctor(auth);
            UUID facilityId = doctor.getPrimaryFacility() != null ?
                    doctor.getPrimaryFacility().getId() : null;

            List<ServicePricing> labServices;
            if (facilityId != null) {
                labServices = servicePricingRepository.findByCategoryAndFacility("LAB", facilityId).stream()
                        .filter(ServicePricing::isActive)
                        .collect(Collectors.toList());
            } else {
                labServices = servicePricingRepository.findByCategory("LAB").stream()
                        .filter(ServicePricing::isActive)
                        .collect(Collectors.toList());
            }

            if (labServices.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "labServices", List.of(),
                        "count", 0,
                        "message", "No lab services configured."
                ));
            }

            List<Map<String, Object>> response = labServices.stream().map(service -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", service.getId());
                map.put("serviceCode", service.getServiceCode());
                map.put("serviceName", service.getServiceName());
                map.put("category", service.getCategory());
                map.put("basePrice", service.getBasePrice());
                map.put("mutuellePrice", service.getMutuellePrice());
                map.put("rssbPrice", service.getRssbPrice());
                map.put("mmiPrice", service.getMmiPrice());
                map.put("description", service.getDescription());
                map.put("active", service.isActive());
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "labServices", response,
                    "count", response.size()
            ));
        } catch (Exception e) {
            log.error("Failed to get lab services: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/debug/consultation/{ticketId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> debugConsultation(@PathVariable UUID ticketId) {
        try {
            log.info("🔍 Debugging consultation for ticket: {}", ticketId);

            Consultation consultation = consultationRepository.findByTicketId(ticketId)
                    .orElseThrow(() -> new RuntimeException("Consultation not found"));

            Map<String, Object> response = new HashMap<>();
            response.put("ticketId", ticketId);
            response.put("consultationId", consultation.getId());
            response.put("labOrdersRaw", consultation.getLabOrders());
            response.put("labOrdersLength", consultation.getLabOrders() != null ? consultation.getLabOrders().length() : 0);
            response.put("consultationCreatedAt", consultation.getCreatedAt());
            response.put("consultationCompletedAt", consultation.getCompletedAt());

            List<Billing> billings = billingRepository.findByTicketId(ticketId);
            if (!billings.isEmpty()) {
                Billing billing = billings.get(0);
                response.put("billingExists", true);
                response.put("invoiceNumber", billing.getInvoiceNumber());
                response.put("billingItems", billing.getItems());
                response.put("billingTotal", billing.getTotalAmount());
                response.put("billingPatientAmount", billing.getPatientAmount());
                response.put("billingInsuranceType", billing.getInsuranceType());
            } else {
                response.put("billingExists", false);
            }

            Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
            if (ticket != null) {
                response.put("ticketNumber", ticket.getTicketNumber());
                response.put("ticketStatus", ticket.getStatus().name());
                response.put("ticketCreatedAt", ticket.getCreatedAt());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Debug error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}