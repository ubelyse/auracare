package com.mvura.controller;

import com.mvura.dto.AdminCreateUserRequest;
import com.mvura.dto.DepartmentDTO;
import com.mvura.dto.UserSummaryDTO;
import com.mvura.model.Department;
import com.mvura.model.Facility;
import com.mvura.model.InsuranceProvider;
import com.mvura.model.ServicePricing;
import com.mvura.model.User;
import com.mvura.service.AdminService;
import com.mvura.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('DISTRICT_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminService adminService;
    private final AuthService authService;

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ==================== FACILITY MANAGEMENT ====================

    @PostMapping("/facilities")
    public ResponseEntity<?> createFacility(@RequestBody Facility facility,
                                            Authentication auth, HttpServletRequest request) {
        Facility created = adminService.createFacility(facility, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Facility created successfully",
                "facility", created
        ));
    }

    @PutMapping("/facilities/{facilityId}")
    public ResponseEntity<?> updateFacility(
            @PathVariable UUID facilityId,
            @RequestBody Facility facility,
            Authentication auth, HttpServletRequest request) {
        Facility updated = adminService.updateFacility(facilityId, facility, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Facility updated successfully",
                "facility", updated
        ));
    }

    @DeleteMapping("/facilities/{facilityId}")
    public ResponseEntity<?> deleteFacility(@PathVariable UUID facilityId,
                                            Authentication auth, HttpServletRequest request) {
        adminService.deleteFacility(facilityId, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Facility deactivated successfully"
        ));
    }

    @GetMapping("/facilities")
    public ResponseEntity<?> getAllFacilities() {
        List<Facility> facilities = adminService.getAllFacilities();
        return ResponseEntity.ok(Map.of(
                "facilities", facilities,
                "count", facilities.size()
        ));
    }

    @GetMapping("/facilities/{facilityId}")
    public ResponseEntity<?> getFacility(@PathVariable UUID facilityId) {
        Facility facility = adminService.getFacilityById(facilityId);
        return ResponseEntity.ok(facility);
    }

    // ==================== DEPARTMENT MANAGEMENT ====================

    @PostMapping("/departments")
    public ResponseEntity<?> createDepartment(@RequestBody Department department,
                                              Authentication auth, HttpServletRequest request) {
        Department created = adminService.createDepartment(department, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Department created successfully",
                "department", created
        ));
    }

    @PutMapping("/departments/{departmentId}")
    public ResponseEntity<?> updateDepartment(
            @PathVariable UUID departmentId,
            @RequestBody Department department,
            Authentication auth, HttpServletRequest request) {
        Department updated = adminService.updateDepartment(departmentId, department, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Department updated successfully",
                "department", updated
        ));
    }

    @GetMapping("/facilities/{facilityId}/departments")
    public ResponseEntity<?> getDepartmentsByFacility(@PathVariable UUID facilityId) {
        List<DepartmentDTO> departments = adminService.getDepartmentsByFacility(facilityId);
        return ResponseEntity.ok(Map.of(
                "departments", departments,
                "count", departments.size()
        ));
    }

    // ==================== STAFF MANAGEMENT ====================

    @PostMapping("/staff/assign")
    public ResponseEntity<?> assignStaff(
            @RequestParam UUID userId,
            @RequestParam UUID facilityId,
            @RequestParam String role,
            @RequestParam(defaultValue = "false") boolean isPrimary,
            Authentication auth, HttpServletRequest request) {
        User user = adminService.assignStaffToFacility(
                userId, facilityId, role, isPrimary, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Staff assigned successfully",
                "user", user
        ));
    }

    @PostMapping("/staff/remove")
    public ResponseEntity<?> removeStaff(
            @RequestParam UUID userId,
            @RequestParam UUID facilityId,
            Authentication auth, HttpServletRequest request) {
        User user = adminService.removeStaffFromFacility(userId, facilityId, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Staff removed successfully",
                "user", user
        ));
    }

    @GetMapping("/facilities/{facilityId}/staff")
    public ResponseEntity<?> getStaffByFacility(@PathVariable UUID facilityId) {
        List<User> staff = adminService.getStaffByFacility(facilityId);
        return ResponseEntity.ok(Map.of(
                "staff", staff,
                "count", staff.size()
        ));
    }

    @PostMapping("/doctors/department/assign")
    public ResponseEntity<?> assignDoctorToDepartment(
            @RequestParam UUID doctorId,
            @RequestParam UUID departmentId,
            Authentication auth, HttpServletRequest request) {
        User doctor = adminService.assignDoctorToDepartment(doctorId, departmentId, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Doctor assigned to department successfully",
                "doctor", doctor
        ));
    }

    @PostMapping("/doctors/department/remove")
    public ResponseEntity<?> removeDoctorFromDepartment(
            @RequestParam UUID doctorId,
            @RequestParam UUID departmentId,
            Authentication auth, HttpServletRequest request) {
        User doctor = adminService.removeDoctorFromDepartment(doctorId, departmentId, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Doctor removed from department successfully",
                "doctor", doctor
        ));
    }

    @GetMapping("/departments/{departmentId}/doctors")
    public ResponseEntity<?> getDoctorsByDepartment(@PathVariable UUID departmentId) {
        List<User> doctors = adminService.getDoctorsByDepartment(departmentId);
        return ResponseEntity.ok(Map.of(
                "doctors", doctors,
                "count", doctors.size()
        ));
    }

    @PutMapping("/doctors/{doctorId}/primary-department")
    public ResponseEntity<?> setPrimaryDepartment(
            @PathVariable UUID doctorId,
            @RequestParam UUID departmentId,
            Authentication auth, HttpServletRequest request) {

        User doctor = adminService.setPrimaryDepartment(doctorId, departmentId, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Primary department updated successfully",
                "doctor", Map.of(
                        "id", doctor.getId(),
                        "username", doctor.getUsername(),
                        "departmentId", doctor.getPrimaryDepartment() != null ?
                                doctor.getPrimaryDepartment().getId() : null,
                        "departmentName", doctor.getPrimaryDepartment() != null ?
                                doctor.getPrimaryDepartment().getName() : null
                )
        ));
    }

    // ==================== TELEMETRY ====================

    @GetMapping("/telemetry")
    public ResponseEntity<?> getTelemetry() {
        Map<String, Object> telemetry = adminService.getMultiFacilityTelemetry();
        return ResponseEntity.ok(telemetry);
    }

    @GetMapping("/telemetry/facility/{facilityId}")
    public ResponseEntity<?> getFacilityTelemetry(@PathVariable UUID facilityId) {
        Facility facility = adminService.getFacilityById(facilityId);
        Map<String, Object> metrics = adminService.getFacilityMetrics(facility);
        return ResponseEntity.ok(metrics);
    }

    // ==================== FINANCIAL MANAGEMENT ====================

    @GetMapping("/financial/summary/{facilityId}")
    public ResponseEntity<?> getFacilitySummary(@PathVariable UUID facilityId, Authentication auth) {
        log.info("📊 Getting financial summary for facility: {}", facilityId);
        Map<String, Object> summary = adminService.getFacilityFinancialSummary(facilityId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/financial/claims")
    public ResponseEntity<?> getInsuranceClaims(Authentication auth) {
        log.info("📋 Getting insurance claims summary");
        List<Map<String, Object>> claims = adminService.getInsuranceClaimsSummary();
        return ResponseEntity.ok(claims);
    }

    @GetMapping("/reports")
    public ResponseEntity<?> generateReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) UUID facilityId,
            Authentication auth) {

        log.info("📊 Generating report - facilityId: {}, start: {}, end: {}", facilityId, startDate, endDate);
        Map<String, Object> report = adminService.generateFinancialReport(startDate, endDate, facilityId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/revenue")
    public ResponseEntity<?> getRevenueAnalysis(
            @RequestParam(required = false) UUID facilityId,
            @RequestParam(required = false) String period,
            Authentication auth) {

        log.info("📈 Getting revenue analysis - facilityId: {}, period: {}", facilityId, period);
        Map<String, Object> analysis = adminService.getRevenueAnalysis(facilityId, period);
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/claims")
    public ResponseEntity<?> getClaims(
            @RequestParam(required = false) String insuranceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID facilityId,
            Authentication auth) {

        log.info("📋 Getting claims - insuranceType: {}, status: {}, facilityId: {}", insuranceType, status, facilityId);
        Map<String, Object> claims = adminService.getClaims(insuranceType, status, facilityId);
        return ResponseEntity.ok(claims);
    }

    // ==================== USER MANAGEMENT ====================

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        List<UserSummaryDTO> users = adminService.getAllUserSummaries();
        return ResponseEntity.ok(Map.of(
                "users", users,
                "count", users.size()
        ));
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUserByAdmin(@Valid @RequestBody AdminCreateUserRequest createRequest,
                                               Authentication auth, HttpServletRequest request) {
        User user = adminService.createUserWithRole(createRequest, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "User created successfully. Verification email dispatched.",
                "user", user
        ));
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<?> updateUserRole(
            @PathVariable UUID userId,
            @RequestParam String role,
            Authentication auth, HttpServletRequest request) {
        log.info("Updating user role: userId={}, role={}", userId, role);
        User user = adminService.updateUserRole(userId, role, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "User role updated successfully",
                "user", user
        ));
    }

    @PostMapping("/users/{userId}/toggle-active")
    public ResponseEntity<?> toggleUserActive(@PathVariable UUID userId,
                                              Authentication auth, HttpServletRequest request) {
        User user = adminService.toggleUserActive(userId, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "User active status toggled",
                "user", user
        ));
    }

    // ==================== INSURANCE PROVIDER MANAGEMENT ====================

    @GetMapping("/insurance-providers")
    @PreAuthorize("hasAnyRole('DISTRICT_ADMIN', 'FACILITY_ADMIN')")
    public ResponseEntity<?> getAllInsuranceProviders() {
        return ResponseEntity.ok(adminService.getAllInsuranceProviders());
    }

    @GetMapping("/insurance-providers/{providerId}")
    @PreAuthorize("hasAnyRole('DISTRICT_ADMIN', 'FACILITY_ADMIN')")
    public ResponseEntity<?> getInsuranceProvider(@PathVariable UUID providerId) {
        return ResponseEntity.ok(adminService.getInsuranceProvider(providerId));
    }

    @PostMapping("/insurance-providers")
    @PreAuthorize("hasRole('DISTRICT_ADMIN')")
    public ResponseEntity<?> createInsuranceProvider(@RequestBody InsuranceProvider provider,
                                                     Authentication auth, HttpServletRequest request) {
        InsuranceProvider created = adminService.createInsuranceProvider(provider, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Insurance provider created successfully",
                "provider", created
        ));
    }

    @PutMapping("/insurance-providers/{providerId}")
    @PreAuthorize("hasRole('DISTRICT_ADMIN')")
    public ResponseEntity<?> updateInsuranceProvider(
            @PathVariable UUID providerId,
            @RequestBody InsuranceProvider provider,
            Authentication auth, HttpServletRequest request) {
        InsuranceProvider updated = adminService.updateInsuranceProvider(
                providerId, provider, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Insurance provider updated successfully",
                "provider", updated
        ));
    }

    @DeleteMapping("/insurance-providers/{providerId}")
    @PreAuthorize("hasRole('DISTRICT_ADMIN')")
    public ResponseEntity<?> deleteInsuranceProvider(@PathVariable UUID providerId,
                                                     Authentication auth, HttpServletRequest request) {
        adminService.deleteInsuranceProvider(providerId, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Insurance provider deactivated successfully"
        ));
    }

    // ==================== SERVICE PRICING MANAGEMENT ====================

    @GetMapping("/service-pricing")
    @PreAuthorize("hasAnyRole('DISTRICT_ADMIN', 'FACILITY_ADMIN')")
    public ResponseEntity<?> getAllServicePricing() {
        return ResponseEntity.ok(adminService.getAllServicePricing());
    }

    @GetMapping("/service-pricing/category/{category}")
    @PreAuthorize("hasAnyRole('DISTRICT_ADMIN', 'FACILITY_ADMIN')")
    public ResponseEntity<?> getServicePricingByCategory(@PathVariable String category) {
        return ResponseEntity.ok(adminService.getServicePricingByCategory(category));
    }

    @GetMapping("/service-pricing/facility/{facilityId}")
    @PreAuthorize("hasAnyRole('DISTRICT_ADMIN', 'FACILITY_ADMIN')")
    public ResponseEntity<?> getServicePricingByFacility(@PathVariable UUID facilityId) {
        return ResponseEntity.ok(adminService.getServicePricingByFacility(facilityId));
    }

    @GetMapping("/service-pricing/{pricingId}")
    @PreAuthorize("hasAnyRole('DISTRICT_ADMIN', 'FACILITY_ADMIN')")
    public ResponseEntity<?> getServicePricing(@PathVariable UUID pricingId) {
        return ResponseEntity.ok(adminService.getServicePricing(pricingId));
    }

    @PostMapping("/service-pricing")
    @PreAuthorize("hasRole('DISTRICT_ADMIN')")
    public ResponseEntity<?> createServicePricing(@RequestBody ServicePricing pricing,
                                                  Authentication auth, HttpServletRequest request) {
        ServicePricing created = adminService.createServicePricing(pricing, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Service pricing created successfully",
                "pricing", created
        ));
    }

    @PutMapping("/service-pricing/{pricingId}")
    @PreAuthorize("hasRole('DISTRICT_ADMIN')")
    public ResponseEntity<?> updateServicePricing(
            @PathVariable UUID pricingId,
            @RequestBody ServicePricing pricing,
            Authentication auth, HttpServletRequest request) {
        ServicePricing updated = adminService.updateServicePricing(
                pricingId, pricing, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Service pricing updated successfully",
                "pricing", updated
        ));
    }

    @DeleteMapping("/service-pricing/{pricingId}")
    @PreAuthorize("hasRole('DISTRICT_ADMIN')")
    public ResponseEntity<?> deleteServicePricing(@PathVariable UUID pricingId,
                                                  Authentication auth, HttpServletRequest request) {
        adminService.deleteServicePricing(pricingId, auth.getName(), getClientIp(request));
        return ResponseEntity.ok(Map.of(
                "message", "Service pricing deactivated successfully"
        ));
    }

    // ==================== AUDIT LOGS ====================

    @GetMapping("/audit")
    public ResponseEntity<?> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication auth) {

        log.info("📜 Getting audit logs - action: {}, entityType: {}, startDate: {}, endDate: {}",
                action, entityType, startDate, endDate);

        Map<String, Object> result = adminService.getAuditLogs(action, entityType, startDate, endDate);
        return ResponseEntity.ok(result);
    }
}