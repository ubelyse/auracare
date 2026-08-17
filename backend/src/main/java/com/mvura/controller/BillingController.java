package com.mvura.controller;

import com.mvura.dto.BillingDTO;
import com.mvura.dto.ProcessPaymentRequest;
import com.mvura.dto.SimulatePaymentRequest;
import com.mvura.dto.VoidBillRequest;
import com.mvura.model.Billing;
import com.mvura.model.User;
import com.mvura.model.UserRole;
import com.mvura.repository.UserRepository;
import com.mvura.service.BillingService;
import com.mvura.service.PaymentResult;
import com.mvura.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingController {

    private final BillingService billingService;
    private final PaymentService paymentService;
    private final UserRepository userRepository;

    private static final Set<UserRole> STAFF_ROLES = Set.of(
            UserRole.DOCTOR, UserRole.STAFF, UserRole.FACILITY_ADMIN, UserRole.DISTRICT_ADMIN
    );

    private User getAuthenticatedUser(Authentication auth) {
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private void assertOwnsOrIsStaff(Billing billing, User requester) {
        boolean isOwner = billing.getPatient() != null
                && billing.getPatient().getId().equals(requester.getId());
        boolean isStaff = STAFF_ROLES.contains(requester.getRole());

        if (!isOwner && !isStaff) {
            log.warn("Access denied: user {} attempted to access billing record {} belonging to another patient",
                    requester.getUsername(), billing.getId());
            throw new AccessDeniedException("You do not have permission to access this billing record");
        }
    }

    // ===== GENERATE BILL - NOW INCLUDES CONSULTATION + LAB TESTS =====
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('DOCTOR', 'STAFF', 'FACILITY_ADMIN', 'DISTRICT_ADMIN')")
    public ResponseEntity<?> generateBill(
            @RequestParam UUID ticketId,
            @RequestParam(defaultValue = "CONSULTATION") String serviceCode) {

        log.info("Generating bill for ticket: {}, serviceCode: {}", ticketId, serviceCode);

        try {
            Billing billing = billingService.generateBill(ticketId, serviceCode);
            BillingDTO dto = billingService.convertToDTO(billing);

            return ResponseEntity.ok(Map.of(
                    "message", "Bill generated successfully",
                    "billing", dto,
                    "invoiceNumber", billing.getInvoiceNumber(),
                    "patientAmount", billing.getPatientAmount(),
                    "insuranceAmount", billing.getInsuranceAmount(),
                    "totalAmount", billing.getTotalAmount()
            ));
        } catch (Exception e) {
            log.error("Failed to generate bill: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ===== NEW: Preview bill without saving to database =====
    @GetMapping("/preview")
    @PreAuthorize("hasAnyRole('DOCTOR', 'STAFF', 'FACILITY_ADMIN')")
    public ResponseEntity<?> previewBill(
            @RequestParam UUID ticketId,
            @RequestParam(defaultValue = "CONSULTATION") String serviceCode,
            Authentication auth) {

        log.info("📋 Previewing bill for ticket: {}, serviceCode: {}", ticketId, serviceCode);

        try {
            // This generates a preview WITHOUT saving to database
            Map<String, Object> preview = billingService.previewBill(ticketId, serviceCode);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            log.error("❌ Preview error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/{billingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getBill(@PathVariable UUID billingId, Authentication auth) {
        User requester = getAuthenticatedUser(auth);
        Billing billing = billingService.getBill(billingId);
        assertOwnsOrIsStaff(billing, requester);
        BillingDTO dto = billingService.convertToDTO(billing);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/patient")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'STAFF')")
    public ResponseEntity<?> getPatientBills(Authentication auth) {
        UUID patientId = getAuthenticatedUser(auth).getId();
        List<Billing> billings = billingService.getPatientBills(patientId);
        List<BillingDTO> dtos = billingService.convertToDTOList(billings);
        return ResponseEntity.ok(Map.of(
                "bills", dtos,
                "count", dtos.size()
        ));
    }

    @GetMapping("/facility/{facilityId}")
    @PreAuthorize("hasAnyRole('FACILITY_ADMIN', 'DISTRICT_ADMIN')")
    public ResponseEntity<?> getFacilityBills(@PathVariable UUID facilityId) {
        List<Billing> billings = billingService.getFacilityBills(facilityId);
        List<BillingDTO> dtos = billingService.convertToDTOList(billings);
        return ResponseEntity.ok(Map.of(
                "bills", dtos,
                "count", dtos.size()
        ));
    }

    @GetMapping("/patient/pending")
    @PreAuthorize("hasAnyRole('PATIENT', 'STAFF')")
    public ResponseEntity<?> getPatientPendingBills(Authentication auth) {
        UUID patientId = getAuthenticatedUser(auth).getId();
        List<Billing> billings = billingService.getPatientPendingBills(patientId);
        List<BillingDTO> dtos = billingService.convertToDTOList(billings);
        return ResponseEntity.ok(Map.of(
                "bills", dtos,
                "count", dtos.size()
        ));
    }

    @PostMapping("/payment")
    @PreAuthorize("hasAnyRole('STAFF', 'FACILITY_ADMIN', 'DISTRICT_ADMIN')")
    public ResponseEntity<?> processPayment(@Valid @RequestBody ProcessPaymentRequest request) {
        Billing billing = billingService.processPayment(
                request.getBillingId(), request.getPaymentMethod(), request.getTransactionId());
        BillingDTO dto = billingService.convertToDTO(billing);
        return ResponseEntity.ok(Map.of(
                "message", "Payment processed successfully",
                "billing", dto,
                "invoiceNumber", billing.getInvoiceNumber(),
                "status", billing.getStatus()
        ));
    }

    @PostMapping("/payment/simulate")
    @PreAuthorize("hasAnyRole('PATIENT', 'STAFF', 'FACILITY_ADMIN', 'DISTRICT_ADMIN')")
    public ResponseEntity<?> simulatePayment(
            @Valid @RequestBody SimulatePaymentRequest request, Authentication auth) {

        User requester = getAuthenticatedUser(auth);
        Billing billing = billingService.getBill(request.getBillingId());
        assertOwnsOrIsStaff(billing, requester);

        PaymentResult result = paymentService.processPayment(
                billing.getInvoiceNumber(),
                billing.getPatientAmount(),
                request.getPaymentMethod()
        );

        if (result.isSuccess()) {
            billing = billingService.processPayment(billing.getId(), request.getPaymentMethod(), result.getTransactionId());
            BillingDTO dto = billingService.convertToDTO(billing);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment processed successfully",
                    "transactionId", result.getTransactionId(),
                    "billing", dto
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", result.getMessage()
            ));
        }
    }

    @PostMapping("/void")
    @PreAuthorize("hasRole('DISTRICT_ADMIN')")
    public ResponseEntity<?> voidBill(@Valid @RequestBody VoidBillRequest request) {
        Billing billing = billingService.voidBill(request.getBillingId(), request.getReason());
        BillingDTO dto = billingService.convertToDTO(billing);
        return ResponseEntity.ok(Map.of(
                "message", "Bill voided successfully",
                "billing", dto
        ));
    }

    @GetMapping("/summary/facility/{facilityId}")
    @PreAuthorize("hasAnyRole('FACILITY_ADMIN', 'DISTRICT_ADMIN')")
    public ResponseEntity<?> getFacilitySummary(@PathVariable UUID facilityId) {
        log.info("Getting financial summary for facility: {}", facilityId);
        return ResponseEntity.ok(billingService.getFacilityFinancialSummary(facilityId));
    }

    @GetMapping("/insurance/claims")
    @PreAuthorize("hasRole('DISTRICT_ADMIN')")
    public ResponseEntity<?> getInsuranceClaims() {
        log.info("Getting insurance claims summary");
        return ResponseEntity.ok(billingService.getInsuranceClaimsSummary());
    }

    // ===== AGING REPORT ENDPOINTS =====

    @GetMapping("/aging-report/{facilityId}")
    @PreAuthorize("hasAnyRole('FACILITY_ADMIN', 'DISTRICT_ADMIN')")
    public ResponseEntity<?> getAgingReport(@PathVariable UUID facilityId) {
        log.info("Getting aging report for facility: {}", facilityId);
        Map<String, Object> report = billingService.getAgingReportFormatted(facilityId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/aging-report/{facilityId}/raw")
    @PreAuthorize("hasAnyRole('FACILITY_ADMIN', 'DISTRICT_ADMIN')")
    public ResponseEntity<?> getAgingReportRaw(@PathVariable UUID facilityId) {
        log.info("Getting raw aging report data for facility: {}", facilityId);
        List<Object[]> report = billingService.getAgingReport(facilityId);
        return ResponseEntity.ok(Map.of(
                "facilityId", facilityId,
                "data", report,
                "generatedAt", java.time.LocalDateTime.now()
        ));
    }

    // ===== GET BILL BY TICKET =====
    @GetMapping("/ticket/{ticketId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'STAFF', 'PATIENT', 'FACILITY_ADMIN', 'DISTRICT_ADMIN')")
    public ResponseEntity<?> getBillByTicket(@PathVariable UUID ticketId, Authentication auth) {
        User requester = getAuthenticatedUser(auth);
        log.info("Getting bill for ticket: {}", ticketId);

        try {
            // This would need a new method in BillingService
            // For now, we'll just return a message
            return ResponseEntity.ok(Map.of(
                    "message", "Please use the bill ID to fetch bill details"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}