package com.mvura.service;

import com.mvura.dto.AdminCreateUserRequest;
import com.mvura.dto.DepartmentDTO;
import com.mvura.dto.UserSummaryDTO;
import com.mvura.model.*;
import com.mvura.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final FacilityRepository facilityRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final EntityManager entityManager;
    private final AuditService auditService;
    private final InsuranceProviderRepository insuranceProviderRepository;
    private final ServicePricingRepository servicePricingRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final BillingRepository billingRepository;

    // ==================== FACILITY MANAGEMENT ====================

    @Transactional
    public Facility createFacility(Facility facility, String username, String ipAddress) {
        if (facilityRepository.existsByCode(facility.getCode())) {
            throw new RuntimeException("Facility code already exists");
        }

        Facility saved = facilityRepository.save(facility);

        try {
            Map<String, Object> details = new HashMap<>();
            details.put("name", saved.getName());
            details.put("code", saved.getCode());
            details.put("address", saved.getAddress());
            details.put("phone", saved.getPhone());
            details.put("email", saved.getEmail());
            auditService.logAction(
                    "FACILITY_CREATED",
                    "FACILITY",
                    saved.getId().toString(),
                    username,
                    ipAddress,
                    null,
                    details
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Facility created: {} ({})", saved.getName(), saved.getCode());
        return saved;
    }

    @Transactional
    public Facility updateFacility(UUID facilityId, Facility updateData, String username, String ipAddress) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        facility.setName(updateData.getName());
        facility.setAddress(updateData.getAddress());
        facility.setPhone(updateData.getPhone());
        facility.setEmail(updateData.getEmail());
        facility.setActive(updateData.isActive());

        Facility saved = facilityRepository.save(facility);

        try {
            Map<String, Object> details = new HashMap<>();
            details.put("name", saved.getName());
            details.put("code", saved.getCode());
            details.put("isActive", saved.isActive());
            auditService.logAction(
                    "FACILITY_UPDATED",
                    "FACILITY",
                    facilityId.toString(),
                    username,
                    ipAddress,
                    null,
                    details
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Facility updated: {}", saved.getName());
        return saved;
    }

    @Transactional
    public void deleteFacility(UUID facilityId, String username, String ipAddress) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        facility.setActive(false);
        facilityRepository.save(facility);

        try {
            auditService.logAction(
                    "FACILITY_DEACTIVATED",
                    "FACILITY",
                    facilityId.toString(),
                    username,
                    ipAddress,
                    null,
                    Map.of("name", facility.getName())
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Facility deactivated: {}", facility.getName());
    }

    public List<Facility> getAllFacilities() {
        return facilityRepository.findAll();
    }

    public Facility getFacilityById(UUID facilityId) {
        return facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found"));
    }

    // ==================== DEPARTMENT MANAGEMENT ====================

    @Transactional
    public Department createDepartment(Department department, String username, String ipAddress) {
        if (department.getFacility() == null && department.getFacilityId() != null) {
            Facility facility = facilityRepository.findById(department.getFacilityId())
                    .orElseThrow(() -> new RuntimeException("Facility not found"));
            department.setFacility(facility);
        }

        if (department.getFacility() == null) {
            throw new RuntimeException("Facility is required to create a department");
        }

        department.setActive(true);

        Department saved = departmentRepository.save(department);

        try {
            auditService.logAction(
                    "DEPARTMENT_CREATED",
                    "DEPARTMENT",
                    saved.getId().toString(),
                    username,
                    ipAddress,
                    null,
                    Map.of(
                            "facilityId", saved.getFacility().getId(),
                            "name", saved.getName(),
                            "code", saved.getCode()
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Department created: {} ({})", saved.getName(), saved.getCode());
        return saved;
    }

    @Transactional
    public Department updateDepartment(UUID departmentId, Department updateData, String username, String ipAddress) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        department.setName(updateData.getName());
        department.setDescription(updateData.getDescription());
        department.setActive(updateData.isActive());

        Department saved = departmentRepository.save(department);

        try {
            auditService.logAction(
                    "DEPARTMENT_UPDATED",
                    "DEPARTMENT",
                    departmentId.toString(),
                    username,
                    ipAddress,
                    null,
                    Map.of("name", saved.getName())
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        return saved;
    }

    public List<DepartmentDTO> getDepartmentsByFacility(UUID facilityId) {
        log.info("🔍 Getting departments for facility: {}", facilityId);

        List<Department> departments = departmentRepository.findActiveByFacility(facilityId);

        if (departments.isEmpty()) {
            departments = departmentRepository.findByFacilityId(facilityId);
        }

        return departments.stream().map(dept -> {
            DepartmentDTO dto = new DepartmentDTO();
            dto.setId(dept.getId());
            dto.setName(dept.getName());
            dto.setCode(dept.getCode());
            dto.setDescription(dept.getDescription());
            dto.setActive(dept.isActive());
            if (dept.getFacility() != null) {
                dto.setFacilityId(dept.getFacility().getId());
                dto.setFacilityName(dept.getFacility().getName());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    // ==================== STAFF MANAGEMENT ====================

    @Transactional
    public User assignStaffToFacility(UUID userId, UUID facilityId, String role, boolean isPrimary,
                                      String actorUsername, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        if (user.getFacilities() == null) {
            user.setFacilities(new HashSet<>());
        }
        user.getFacilities().add(facility);

        if (isPrimary || user.getPrimaryFacility() == null) {
            user.setPrimaryFacility(facility);
            log.info("Set primary facility for user {}: {}", user.getUsername(), facility.getName());
        }

        if (role != null && !role.isEmpty()) {
            try {
                UserRole newRole = UserRole.valueOf(role);
                if (newRole == UserRole.DOCTOR || newRole == UserRole.STAFF || newRole == UserRole.FACILITY_ADMIN) {
                    user.setRole(newRole);
                    log.info("Updated user {} role to {}", user.getUsername(), newRole);
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid role: {}", role);
            }
        }

        User saved = userRepository.save(user);

        try {
            Map<String, Object> details = new HashMap<>();
            details.put("facilityId", facilityId);
            details.put("facilityName", facility.getName());
            details.put("role", user.getRole().name());
            details.put("isPrimary", isPrimary);
            auditService.logAction(
                    "STAFF_ASSIGNED",
                    "USER",
                    userId.toString(),
                    actorUsername,
                    ipAddress,
                    null,
                    details
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Staff {} assigned to facility: {} with role: {}",
                user.getUsername(), facility.getName(), user.getRole().name());
        return saved;
    }

    @Transactional
    public User removeStaffFromFacility(UUID userId, UUID facilityId, String actorUsername, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        log.info("Removing user {} from facility: {}", user.getUsername(), facility.getName());

        boolean removed = false;
        if (user.getFacilities() != null) {
            removed = user.getFacilities().removeIf(f -> f.getId().equals(facilityId));
            log.info("Facility removed from user's facilities set: {}", removed);
        }

        if (user.getPrimaryFacility() != null && user.getPrimaryFacility().getId().equals(facilityId)) {
            if (user.getFacilities() != null && !user.getFacilities().isEmpty()) {
                Facility newPrimary = user.getFacilities().iterator().next();
                user.setPrimaryFacility(newPrimary);
                log.info("New primary facility set: {}", newPrimary.getName());
            } else {
                user.setPrimaryFacility(null);
                log.info("Primary facility cleared - user has no other facilities");
                user.setPrimaryDepartment(null);
                log.info("Primary department cleared - user has no facilities");
            }
        }

        if (user.getFacilities() == null || user.getFacilities().isEmpty()) {
            user.setPrimaryFacility(null);
            user.setPrimaryDepartment(null);
            log.info("User has no facilities left, primary_facility_id and primary_department_id set to NULL");
        }

        User saved = userRepository.save(user);

        if (saved.getPrimaryFacility() == null) {
            entityManager.createNativeQuery(
                    "UPDATE users SET facility_id = NULL WHERE id = :userId"
            ).setParameter("userId", userId).executeUpdate();
            log.info("Cleared legacy facility_id column for user: {}", user.getUsername());
        }

        entityManager.createNativeQuery(
                "UPDATE users SET primary_department_id = NULL WHERE id = :userId"
        ).setParameter("userId", userId).executeUpdate();
        log.info("Cleared primary_department_id for user: {}", user.getUsername());

        try {
            Map<String, Object> details = new HashMap<>();
            details.put("facilityName", facility.getName());
            details.put("facilityId", facility.getId());
            details.put("removed", removed);
            details.put("facilitiesRemaining", saved.getFacilities() != null ?
                    saved.getFacilities().size() : 0);
            details.put("primaryFacility", saved.getPrimaryFacility() != null ?
                    saved.getPrimaryFacility().getName() : "NONE");
            details.put("primaryDepartment", saved.getPrimaryDepartment() != null ?
                    saved.getPrimaryDepartment().getName() : "NONE");

            auditService.logAction(
                    "STAFF_REMOVED_FROM_FACILITY",
                    "USER",
                    userId.toString(),
                    actorUsername,
                    ipAddress,
                    null,
                    details
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("User {} removed from facility: {}", user.getUsername(), facility.getName());
        return saved;
    }

    @Transactional
    public User assignDoctorToDepartment(UUID doctorId, UUID departmentId, String username, String ipAddress) {
        User doctor = userRepository.findByIdWithDepartments(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        if (doctor.getRole() != UserRole.DOCTOR) {
            throw new RuntimeException("User is not a doctor");
        }

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        Facility facility = department.getFacility();
        if (facility != null) {
            if (doctor.getFacilities() == null) {
                doctor.setFacilities(new HashSet<>());
            }
            boolean alreadyHasFacility = doctor.getFacilities().stream()
                    .anyMatch(f -> f.getId().equals(facility.getId()));
            if (!alreadyHasFacility) {
                doctor.getFacilities().add(facility);
                log.info("Added facility {} to doctor {}", facility.getName(), doctor.getUsername());
            }

            if (doctor.getPrimaryFacility() == null) {
                doctor.setPrimaryFacility(facility);
                log.info("Auto-set primary facility for doctor {}: {}", doctor.getUsername(), facility.getName());
            }
        }

        if (doctor.getDepartments() == null) {
            doctor.setDepartments(new HashSet<>());
        }
        doctor.getDepartments().add(department);

        if (doctor.getPrimaryDepartment() == null) {
            doctor.setPrimaryDepartment(department);
            log.info("Auto-set primary department for doctor {}: {}",
                    doctor.getUsername(), department.getName());
        }

        User saved = userRepository.save(doctor);

        try {
            auditService.logAction(
                    "DOCTOR_ASSIGNED_TO_DEPARTMENT",
                    "DEPARTMENT",
                    departmentId.toString(),
                    username,
                    ipAddress,
                    null,
                    Map.of(
                            "doctorName", doctor.getFirstName() + " " + doctor.getLastName(),
                            "departmentName", department.getName()
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Doctor {} assigned to department: {}", doctor.getUsername(), department.getName());
        return saved;
    }

    @Transactional
    public User removeDoctorFromDepartment(UUID doctorId, UUID departmentId, String username, String ipAddress) {
        User doctor = userRepository.findByIdWithDepartments(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        boolean removed = false;
        if (doctor.getDepartments() != null) {
            removed = doctor.getDepartments().removeIf(d -> d.getId().equals(departmentId));
            log.info("Department removed from doctor: {}", removed);
        }

        if (doctor.getPrimaryDepartment() != null &&
                doctor.getPrimaryDepartment().getId().equals(departmentId)) {
            doctor.setPrimaryDepartment(null);
            log.info("Cleared primary department for doctor {}: {}",
                    doctor.getUsername(), department.getName());
        }

        User saved = userRepository.save(doctor);
        userRepository.flush();

        try {
            auditService.logAction(
                    "DOCTOR_REMOVED_FROM_DEPARTMENT",
                    "DEPARTMENT",
                    departmentId.toString(),
                    username,
                    ipAddress,
                    null,
                    Map.of(
                            "doctorName", doctor.getFirstName() + " " + doctor.getLastName(),
                            "departmentName", department.getName(),
                            "removed", removed
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Doctor {} removed from department: {}", doctor.getUsername(), department.getName());
        return saved;
    }

    @Transactional
    public User setPrimaryDepartment(UUID doctorId, UUID departmentId, String username, String ipAddress) {
        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        if (doctor.getRole() != UserRole.DOCTOR) {
            throw new RuntimeException("User is not a doctor");
        }

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        if (!doctor.hasDepartment(departmentId)) {
            if (doctor.getDepartments() == null) {
                doctor.setDepartments(new HashSet<>());
            }
            doctor.getDepartments().add(department);
        }

        doctor.setPrimaryDepartment(department);
        User saved = userRepository.save(doctor);

        try {
            auditService.logAction(
                    "PRIMARY_DEPARTMENT_UPDATED",
                    "USER",
                    doctorId.toString(),
                    username,
                    ipAddress,
                    null,
                    Map.of(
                            "doctorName", doctor.getFirstName() + " " + doctor.getLastName(),
                            "departmentName", department.getName(),
                            "departmentId", department.getId()
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Primary department set for doctor {}: {}", doctor.getUsername(), department.getName());
        return saved;
    }

    // 🔥 FIXED: Return UserSummaryDTO instead of full User
    public List<UserSummaryDTO> getStaffByFacility(UUID facilityId) {
        List<UserRole> roles = List.of(UserRole.DOCTOR, UserRole.STAFF, UserRole.FACILITY_ADMIN);
        List<User> staff = userRepository.findByFacilityIdAndRoleIn(facilityId, roles);
        return staff.stream().map(this::convertToUserSummaryDTO).collect(Collectors.toList());
    }

    // 🔥 FIXED: Return UserSummaryDTO instead of full User
    public List<UserSummaryDTO> getDoctorsByDepartment(UUID departmentId) {
        List<User> doctors = departmentRepository.findAvailableDoctorsByDepartment(departmentId);
        return doctors.stream().map(this::convertToUserSummaryDTO).collect(Collectors.toList());
    }

    // ==================== USER MANAGEMENT ====================

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // 🔥 NEW: Convert User to UserSummaryDTO
    private UserSummaryDTO convertToUserSummaryDTO(User user) {
        UserSummaryDTO dto = new UserSummaryDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole() != null ? user.getRole().name() : "UNKNOWN");
        dto.setActive(user.isActive());
        dto.setEmailVerified(user.isEmailVerified());
        dto.setCreatedAt(user.getCreatedAt());

        if (user.getPrimaryFacility() != null) {
            dto.setFacilityId(user.getPrimaryFacility().getId());
            dto.setFacilityName(user.getPrimaryFacility().getName());
        }

        if (user.getPrimaryDepartment() != null) {
            dto.setDepartmentId(user.getPrimaryDepartment().getId());
            dto.setDepartmentName(user.getPrimaryDepartment().getName());
        }

        return dto;
    }

    // 🔥 ADDED: Missing method called by AdminController
    public List<UserSummaryDTO> getAllUserSummaries() {
        List<User> users = userRepository.findAll();
        return users.stream().map(this::convertToUserSummaryDTO).collect(Collectors.toList());
    }

    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public User updateUserRole(UUID userId, String role, String actorUsername, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserRole newRole = UserRole.valueOf(role);
        user.setRole(newRole);
        User saved = userRepository.save(user);

        try {
            auditService.logAction(
                    "USER_ROLE_UPDATED",
                    "USER",
                    userId.toString(),
                    actorUsername,
                    ipAddress,
                    null,
                    Map.of("newRole", role, "username", saved.getUsername())
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        return saved;
    }

    @Transactional
    public User toggleUserActive(UUID userId, String actorUsername, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean newStatus = !user.isActive();
        user.setActive(newStatus);

        User saved = userRepository.save(user);

        try {
            auditService.logAction(
                    "USER_ACTIVE_TOGGLED",
                    "USER",
                    userId.toString(),
                    actorUsername,
                    ipAddress,
                    null,
                    Map.of(
                            "isActive", saved.isActive(),
                            "username", saved.getUsername(),
                            "userId", saved.getId()
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        return saved;
    }

    @Transactional
    public User createUserWithRole(AdminCreateUserRequest request, String actorUsername, String ipAddress) {
        if (userRepository.existsByUsername(request.getUsername()))
            throw new RuntimeException("Username already taken");
        if (userRepository.existsByEmail(request.getEmail()))
            throw new RuntimeException("Email already registered");

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(request.getRole())
                .active(true)
                .emailVerified(false)
                .build();

        if (request.getFacilityId() != null) {
            Facility facility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new RuntimeException("Facility not found"));
            user.setPrimaryFacility(facility);
            if (user.getFacilities() == null) {
                user.setFacilities(new HashSet<>());
            }
            user.getFacilities().add(facility);
        }

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));

            if (user.getDepartments() == null) {
                user.setDepartments(new HashSet<>());
            }
            user.getDepartments().add(department);
            user.setPrimaryDepartment(department);
        }

        User savedUser = userRepository.save(user);

        String tokenString = UUID.randomUUID().toString();

        VerificationToken tokenEntity = VerificationToken.builder()
                .user(savedUser)
                .token(tokenString)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();

        verificationTokenRepository.save(tokenEntity);

        try {
            emailService.sendVerificationEmail(savedUser, tokenString);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", savedUser.getEmail(), e.getMessage(), e);
        }

        try {
            Map<String, Object> details = new HashMap<>();
            details.put("role", savedUser.getRole().name());
            details.put("createdUsername", savedUser.getUsername());
            details.put("facilityId", request.getFacilityId() != null ? request.getFacilityId().toString() : "none");
            details.put("departmentId", request.getDepartmentId() != null ? request.getDepartmentId().toString() : "none");

            auditService.logAction(
                    "USER_CREATED_BY_ADMIN",
                    "USER",
                    savedUser.getId().toString(),
                    actorUsername,
                    ipAddress,
                    null,
                    details
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        return savedUser;
    }

    // ==================== INSURANCE PROVIDER MANAGEMENT ====================

    @Transactional
    public InsuranceProvider createInsuranceProvider(InsuranceProvider provider, String username, String ipAddress) {
        if (insuranceProviderRepository.findByCode(provider.getCode()).isPresent()) {
            throw new RuntimeException("Insurance provider with code '" + provider.getCode() + "' already exists");
        }

        if (provider.getPatientCoPayPercentage() == null) {
            provider.setPatientCoPayPercentage(BigDecimal.valueOf(10));
        }

        InsuranceProvider saved = insuranceProviderRepository.save(provider);

        auditService.logAction(
                "INSURANCE_PROVIDER_CREATED",
                "INSURANCE_PROVIDER",
                saved.getId().toString(),
                username,
                ipAddress,
                null,
                Map.of("code", saved.getCode(), "name", saved.getName())
        );

        return saved;
    }

    @Transactional
    public InsuranceProvider updateInsuranceProvider(UUID providerId, InsuranceProvider provider,
                                                     String username, String ipAddress) {
        InsuranceProvider existing = insuranceProviderRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Insurance provider not found"));

        existing.setName(provider.getName());
        existing.setDescription(provider.getDescription());
        existing.setPatientCoPayPercentage(provider.getPatientCoPayPercentage());
        existing.setActive(provider.isActive());
        existing.setContactEmail(provider.getContactEmail());
        existing.setContactPhone(provider.getContactPhone());
        existing.setWebsite(provider.getWebsite());
        existing.setCoverageDetails(provider.getCoverageDetails());
        existing.setExcludedServices(provider.getExcludedServices());

        InsuranceProvider saved = insuranceProviderRepository.save(existing);

        auditService.logAction(
                "INSURANCE_PROVIDER_UPDATED",
                "INSURANCE_PROVIDER",
                providerId.toString(),
                username,
                ipAddress,
                null,
                Map.of("code", saved.getCode(), "name", saved.getName())
        );

        return saved;
    }

    public List<InsuranceProvider> getAllInsuranceProviders() {
        return insuranceProviderRepository.findAll();
    }

    public InsuranceProvider getInsuranceProvider(UUID providerId) {
        return insuranceProviderRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Insurance provider not found"));
    }

    @Transactional
    public void deleteInsuranceProvider(UUID providerId, String username, String ipAddress) {
        InsuranceProvider provider = insuranceProviderRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Insurance provider not found"));
        provider.setActive(false);
        insuranceProviderRepository.save(provider);

        auditService.logAction(
                "INSURANCE_PROVIDER_DEACTIVATED",
                "INSURANCE_PROVIDER",
                providerId.toString(),
                username,
                ipAddress,
                null,
                Map.of("code", provider.getCode())
        );
    }

    // ==================== SERVICE PRICING MANAGEMENT ====================

    @Transactional
    public ServicePricing createServicePricing(ServicePricing pricing, String username, String ipAddress) {
        if (servicePricingRepository.findByServiceCode(pricing.getServiceCode()).isPresent()) {
            throw new RuntimeException("Service with code '" + pricing.getServiceCode() + "' already exists");
        }

        if (pricing.getMutuelleCoPayPercent() == null) {
            pricing.setMutuelleCoPayPercent(BigDecimal.valueOf(10));
        }
        if (pricing.getRssbCoPayPercent() == null) {
            pricing.setRssbCoPayPercent(BigDecimal.valueOf(15));
        }
        if (pricing.getMmiCoPayPercent() == null) {
            pricing.setMmiCoPayPercent(BigDecimal.valueOf(15));
        }

        ServicePricing saved = servicePricingRepository.save(pricing);

        auditService.logAction(
                "SERVICE_PRICING_CREATED",
                "SERVICE_PRICING",
                saved.getId().toString(),
                username,
                ipAddress,
                null,
                Map.of("serviceCode", saved.getServiceCode(), "serviceName", saved.getServiceName())
        );

        return saved;
    }

    @Transactional
    public ServicePricing updateServicePricing(UUID pricingId, ServicePricing pricing,
                                               String username, String ipAddress) {
        ServicePricing existing = servicePricingRepository.findById(pricingId)
                .orElseThrow(() -> new RuntimeException("Service pricing not found"));

        existing.setServiceName(pricing.getServiceName());
        existing.setCategory(pricing.getCategory());
        existing.setBasePrice(pricing.getBasePrice());
        existing.setMutuelleCoPayPercent(pricing.getMutuelleCoPayPercent());
        existing.setRssbCoPayPercent(pricing.getRssbCoPayPercent());
        existing.setMmiCoPayPercent(pricing.getMmiCoPayPercent());
        existing.setDescription(pricing.getDescription());
        existing.setActive(pricing.isActive());
        existing.setFacilityId(pricing.getFacilityId());

        ServicePricing saved = servicePricingRepository.save(existing);

        auditService.logAction(
                "SERVICE_PRICING_UPDATED",
                "SERVICE_PRICING",
                pricingId.toString(),
                username,
                ipAddress,
                null,
                Map.of("serviceCode", saved.getServiceCode())
        );

        return saved;
    }

    public List<ServicePricing> getAllServicePricing() {
        return servicePricingRepository.findAll();
    }

    public ServicePricing getServicePricing(UUID pricingId) {
        return servicePricingRepository.findById(pricingId)
                .orElseThrow(() -> new RuntimeException("Service pricing not found"));
    }

    public List<ServicePricing> getServicePricingByCategory(String category) {
        return servicePricingRepository.findByCategory(category);
    }

    public List<ServicePricing> getServicePricingByFacility(UUID facilityId) {
        return servicePricingRepository.findGlobalAndFacilityPricing(facilityId);
    }

    @Transactional
    public void deleteServicePricing(UUID pricingId, String username, String ipAddress) {
        ServicePricing pricing = servicePricingRepository.findById(pricingId)
                .orElseThrow(() -> new RuntimeException("Service pricing not found"));
        pricing.setActive(false);
        servicePricingRepository.save(pricing);

        auditService.logAction(
                "SERVICE_PRICING_DEACTIVATED",
                "SERVICE_PRICING",
                pricingId.toString(),
                username,
                ipAddress,
                null,
                Map.of("serviceCode", pricing.getServiceCode())
        );
    }

    // ==================== TELEMETRY & METRICS ====================

    public Map<String, Object> getMultiFacilityTelemetry() {
        Map<String, Object> telemetry = new HashMap<>();
        List<Map<String, Object>> facilityMetrics = new ArrayList<>();

        try {
            List<Facility> facilities = facilityRepository.findAll();

            int totalPatients = 0;
            int totalStaff = 0;
            int totalWaitSum = 0;
            int facilityCount = 0;

            for (Facility facility : facilities) {
                Map<String, Object> metrics = getFacilityMetrics(facility);
                facilityMetrics.add(metrics);

                totalPatients += ((Number) metrics.getOrDefault("activePatients", 0)).intValue();
                totalStaff += ((Number) metrics.getOrDefault("staffCount", 0)).intValue();
                totalWaitSum += ((Number) metrics.getOrDefault("avgWaitMinutes", 0)).intValue();
                facilityCount++;
            }

            telemetry.put("facilities", facilityMetrics);
            telemetry.put("totalPatients", totalPatients);
            telemetry.put("totalStaff", totalStaff);
            telemetry.put("averageWaitTime", facilityCount > 0 ? Math.round((double) totalWaitSum / facilityCount) : 0);
            telemetry.put("totalFacilities", facilities.size());
            telemetry.put("activeFacilities", facilities.stream().filter(Facility::isActive).count());
            telemetry.put("updatedAt", LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error getting telemetry: {}", e.getMessage(), e);
            telemetry.put("facilities", new ArrayList<>());
            telemetry.put("totalPatients", 0);
            telemetry.put("totalStaff", 0);
            telemetry.put("averageWaitTime", 0);
            telemetry.put("totalFacilities", 0);
            telemetry.put("activeFacilities", 0);
            telemetry.put("updatedAt", LocalDateTime.now());
            telemetry.put("error", e.getMessage());
        }

        return telemetry;
    }

    public Map<String, Object> getFacilityMetrics(Facility facility) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("id", facility.getId());
        metrics.put("name", facility.getName());
        metrics.put("code", facility.getCode());
        metrics.put("address", facility.getAddress());
        metrics.put("phone", facility.getPhone());
        metrics.put("email", facility.getEmail());
        metrics.put("isActive", facility.isActive());

        int activePatients = 0;
        long doctorCount = 0;
        long staffCount = 0;
        Double avgWait = 0.0;

        try {
            activePatients = ticketRepository.countActiveTicketsByFacility(facility.getId());
        } catch (Exception e) {
            log.warn("Could not get ticket count for facility {}: {}", facility.getName(), e.getMessage());
        }

        try {
            doctorCount = userRepository.countByFacilityIdAndRole(facility.getId(), UserRole.DOCTOR);
        } catch (Exception e) {
            log.warn("Could not get doctor count for facility {}: {}", facility.getName(), e.getMessage());
        }

        try {
            List<UserRole> staffRoles = List.of(UserRole.DOCTOR, UserRole.STAFF, UserRole.FACILITY_ADMIN);
            staffCount = userRepository.countByFacilityIdAndRoles(facility.getId(), staffRoles);
        } catch (Exception e) {
            log.warn("Could not get staff count for facility {}: {}", facility.getName(), e.getMessage());
        }

        try {
            avgWait = ticketRepository.getAverageWaitTimeByFacility(facility.getId());
        } catch (Exception e) {
            log.warn("Could not get wait time for facility {}: {}", facility.getName(), e.getMessage());
        }

        metrics.put("activePatients", activePatients);
        metrics.put("doctorCount", (int) doctorCount);
        metrics.put("staffCount", (int) staffCount);
        metrics.put("avgWaitMinutes", avgWait != null ? Math.round(avgWait) : 0);

        double ratio = doctorCount > 0 ? (double) activePatients / doctorCount : 0;
        metrics.put("doctorToPatientRatio", String.format("%.1f:1", ratio));

        List<Map<String, Object>> deptMetrics = new ArrayList<>();
        try {
            List<Department> departments = departmentRepository.findActiveByFacility(facility.getId());
            for (Department dept : departments) {
                Map<String, Object> deptMap = new HashMap<>();
                deptMap.put("id", dept.getId());
                deptMap.put("name", dept.getName());
                deptMap.put("code", dept.getCode());
                deptMap.put("description", dept.getDescription());
                deptMap.put("active", dept.isActive());

                int deptPatients = 0;
                long deptDoctors = 0;
                try {
                    deptPatients = ticketRepository.countActiveTicketsByDepartment(dept.getId());
                } catch (Exception e) {
                    log.warn("Could not get patient count for department {}: {}", dept.getName(), e.getMessage());
                }
                try {
                    deptDoctors = departmentRepository.countDoctorsByDepartment(dept.getId());
                } catch (Exception e) {
                    log.warn("Could not get doctor count for department {}: {}", dept.getName(), e.getMessage());
                }
                deptMap.put("patients", deptPatients);
                deptMap.put("doctorCount", (int) deptDoctors);

                deptMetrics.add(deptMap);
            }
        } catch (Exception e) {
            log.warn("Could not get departments for facility {}: {}", facility.getName(), e.getMessage());
        }
        metrics.put("departments", deptMetrics);

        Map<String, Long> priorityDistribution = new HashMap<>();
        try {
            List<Object[]> priorityResults = ticketRepository.getPriorityDistributionByFacility(facility.getId());
            for (Object[] result : priorityResults) {
                priorityDistribution.put(result[0].toString(), ((Number) result[1]).longValue());
            }
        } catch (Exception e) {
            log.warn("Could not get priority distribution for facility {}: {}", facility.getName(), e.getMessage());
        }
        metrics.put("priorityDistribution", priorityDistribution);

        return metrics;
    }

    // ==================== FINANCIAL METHODS ====================

    @Transactional(readOnly = true)
    public Map<String, Object> getFacilityFinancialSummary(UUID facilityId) {
        log.info("📊 Getting financial summary for facility: {}", facilityId);

        Map<String, Object> summary = new HashMap<>();

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime startOfDay = now.withHour(0).withMinute(0).withSecond(0);

            BigDecimal monthRevenue = billingRepository.sumPaidAmountByFacilityAndDateRange(facilityId, startOfMonth, now);
            BigDecimal todayRevenue = billingRepository.sumPaidAmountByFacilityAndDateRange(facilityId, startOfDay, now);
            long pendingBills = billingRepository.countByFacilityIdAndStatus(facilityId, BillingStatus.PENDING);
            long overdueBills = billingRepository.countOverdueBillsByFacility(facilityId, now);

            summary.put("facilityId", facilityId);
            summary.put("monthRevenue", monthRevenue != null ? monthRevenue : BigDecimal.ZERO);
            summary.put("todayRevenue", todayRevenue != null ? todayRevenue : BigDecimal.ZERO);
            summary.put("pendingBills", pendingBills);
            summary.put("overdueBills", overdueBills);
            summary.put("currency", "RWF");
        } catch (Exception e) {
            log.error("Failed to get financial summary: {}", e.getMessage());
            summary.put("error", "Unable to generate summary");
        }

        return summary;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getInsuranceClaimsSummary() {
        log.info("📋 Getting insurance claims summary");

        List<Map<String, Object>> claims = new ArrayList<>();

        try {
            List<Object[]> results = billingRepository.getInsuranceClaimsSummary();
            for (Object[] result : results) {
                Map<String, Object> claim = new HashMap<>();
                claim.put("insuranceType", result[0] != null ? result[0].toString() : "UNKNOWN");
                claim.put("totalAmount", result[1] != null ? result[1] : BigDecimal.ZERO);
                claims.add(claim);
            }
        } catch (Exception e) {
            log.error("Failed to get insurance claims summary: {}", e.getMessage());
        }

        return claims;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> generateFinancialReport(String startDate, String endDate, UUID facilityId) {
        log.info("📊 Generating financial report");

        Map<String, Object> report = new HashMap<>();

        try {
            LocalDateTime start = startDate != null ?
                    LocalDateTime.parse(startDate + "T00:00:00") :
                    LocalDateTime.now().minusDays(30);
            LocalDateTime end = endDate != null ?
                    LocalDateTime.parse(endDate + "T23:59:59") :
                    LocalDateTime.now();

            List<Billing> bills;
            if (facilityId != null) {
                bills = billingRepository.findByFacilityAndDateRange(facilityId, start, end);
            } else {
                bills = billingRepository.findByIssuedAtBetween(start, end);
            }

            report.put("period", Map.of(
                    "start", start.toString(),
                    "end", end.toString()
            ));
            report.put("totalBills", bills.size());
            report.put("totalRevenue", bills.stream()
                    .map(Billing::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            report.put("totalPaid", bills.stream()
                    .filter(b -> b.getStatus() == BillingStatus.PAID)
                    .map(Billing::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            report.put("totalPending", bills.stream()
                    .filter(b -> b.getStatus() == BillingStatus.PENDING)
                    .count());
            report.put("totalCancelled", bills.stream()
                    .filter(b -> b.getStatus() == BillingStatus.CANCELLED)
                    .count());

            Map<String, BigDecimal> byInsurance = bills.stream()
                    .collect(Collectors.groupingBy(
                            b -> b.getInsuranceType() != null ? b.getInsuranceType().name() : "UNKNOWN",
                            Collectors.reducing(BigDecimal.ZERO, Billing::getTotalAmount, BigDecimal::add)
                    ));
            report.put("byInsurance", byInsurance);

            report.put("bills", bills.stream().map(b -> Map.of(
                    "invoiceNumber", b.getInvoiceNumber(),
                    "totalAmount", b.getTotalAmount(),
                    "patientAmount", b.getPatientAmount(),
                    "status", b.getStatus().name(),
                    "issuedAt", b.getIssuedAt().toString(),
                    "insuranceType", b.getInsuranceType() != null ? b.getInsuranceType().name() : "UNKNOWN"
            )).collect(Collectors.toList()));

            report.put("currency", "RWF");

        } catch (Exception e) {
            log.error("Failed to generate report: {}", e.getMessage());
            report.put("error", "Unable to generate report");
        }

        return report;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRevenueAnalysis(UUID facilityId, String period) {
        log.info("📈 Getting revenue analysis");

        Map<String, Object> analysis = new HashMap<>();

        try {
            String periodType = period != null ? period : "month";
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startDate = switch (periodType) {
                case "day" -> now.minusDays(1);
                case "week" -> now.minusWeeks(1);
                case "month" -> now.minusMonths(1);
                case "year" -> now.minusYears(1);
                default -> now.minusMonths(1);
            };

            List<Billing> bills;
            if (facilityId != null) {
                bills = billingRepository.findByFacilityAndDateRange(facilityId, startDate, now);
            } else {
                bills = billingRepository.findByIssuedAtBetween(startDate, now);
            }

            Map<String, BigDecimal> dailyRevenue = bills.stream()
                    .collect(Collectors.groupingBy(
                            b -> b.getIssuedAt().format(DateTimeFormatter.ISO_LOCAL_DATE),
                            Collectors.reducing(BigDecimal.ZERO, Billing::getTotalAmount, BigDecimal::add)
                    ));

            Map<String, BigDecimal> dailyPaid = bills.stream()
                    .filter(b -> b.getStatus() == BillingStatus.PAID)
                    .collect(Collectors.groupingBy(
                            b -> b.getIssuedAt().format(DateTimeFormatter.ISO_LOCAL_DATE),
                            Collectors.reducing(BigDecimal.ZERO, Billing::getTotalAmount, BigDecimal::add)
                    ));

            String facilityName = "All Facilities";
            if (facilityId != null) {
                Facility facility = facilityRepository.findById(facilityId).orElse(null);
                if (facility != null) {
                    facilityName = facility.getName();
                }
            }

            analysis.put("period", periodType);
            analysis.put("facilityId", facilityId);
            analysis.put("facilityName", facilityName);
            analysis.put("totalRevenue", bills.stream()
                    .map(Billing::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            analysis.put("totalPaid", bills.stream()
                    .filter(b -> b.getStatus() == BillingStatus.PAID)
                    .map(Billing::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            analysis.put("pendingCount", bills.stream()
                    .filter(b -> b.getStatus() == BillingStatus.PENDING)
                    .count());
            analysis.put("dailyRevenue", dailyRevenue);
            analysis.put("dailyPaid", dailyPaid);
            analysis.put("currency", "RWF");

        } catch (Exception e) {
            log.error("Failed to get revenue analysis: {}", e.getMessage());
            analysis.put("error", "Unable to generate analysis");
        }

        return analysis;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getClaims(String insuranceType, String status, UUID facilityId) {
        log.info("📋 Getting claims");

        Map<String, Object> response = new HashMap<>();

        try {
            List<Billing> claims = billingRepository.findAll().stream()
                    .filter(b -> b.getInsuranceType() != null)
                    .filter(b -> insuranceType == null || b.getInsuranceType().name().equals(insuranceType))
                    .filter(b -> status == null || b.getStatus().name().equals(status))
                    .filter(b -> facilityId == null ||
                            (b.getFacility() != null && b.getFacility().getId().equals(facilityId)))
                    .collect(Collectors.toList());

            Map<String, Map<String, Object>> summary = claims.stream()
                    .collect(Collectors.groupingBy(
                            b -> b.getInsuranceType().name(),
                            Collectors.collectingAndThen(
                                    Collectors.toList(),
                                    list -> {
                                        Map<String, Object> data = new HashMap<>();
                                        data.put("count", list.size());
                                        data.put("totalAmount", list.stream()
                                                .map(Billing::getTotalAmount)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add));
                                        data.put("paidAmount", list.stream()
                                                .filter(b -> b.getStatus() == BillingStatus.PAID)
                                                .map(Billing::getTotalAmount)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add));
                                        data.put("pendingCount", list.stream()
                                                .filter(b -> b.getStatus() == BillingStatus.PENDING)
                                                .count());
                                        return data;
                                    }
                            )
                    ));

            response.put("summary", summary);
            response.put("totalCount", claims.size());
            response.put("currency", "RWF");

        } catch (Exception e) {
            log.error("Failed to get claims: {}", e.getMessage());
            response.put("error", "Unable to get claims");
        }

        return response;
    }

    // ==================== AUDIT LOGS ====================

    @Transactional(readOnly = true)
    public Map<String, Object> getAuditLogs(String action, String entityType, String startDate, String endDate) {
        log.info("📜 Fetching audit logs with filters - action: {}, entityType: {}", action, entityType);

        Map<String, Object> result = new HashMap<>();

        try {
            LocalDateTime start = startDate != null && !startDate.isEmpty() ?
                    LocalDateTime.parse(startDate + "T00:00:00") :
                    LocalDateTime.now().minusDays(30);
            LocalDateTime end = endDate != null && !endDate.isEmpty() ?
                    LocalDateTime.parse(endDate + "T23:59:59") :
                    LocalDateTime.now();

            List<AuditLog> logs = auditService.getAuditLogs(action, entityType, start, end);

            result.put("logs", logs);
            result.put("count", logs.size());
            result.put("filters", Map.of(
                    "action", action != null ? action : "ALL",
                    "entityType", entityType != null ? entityType : "ALL",
                    "startDate", start.toString(),
                    "endDate", end.toString()
            ));

        } catch (Exception e) {
            log.error("Failed to get audit logs: {}", e.getMessage(), e);
            result.put("error", "Unable to fetch audit logs");
            result.put("logs", new ArrayList<>());
            result.put("count", 0);
        }

        return result;
    }
}