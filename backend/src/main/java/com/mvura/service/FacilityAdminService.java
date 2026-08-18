package com.mvura.service;

import com.mvura.model.*;
import com.mvura.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"unused", "DuplicatedCode"})
public class FacilityAdminService {

    private final FacilityRepository facilityRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final FacilityTransferRepository transferRepository;
    private final AuditService auditService;
    private final EmergencyService emergencyService;

    // ==================== EXISTING METHODS ====================

    @Transactional
    public Facility createFacility(Facility facility) {
        if (facilityRepository.existsByCode(facility.getCode())) {
            throw new RuntimeException("Facility code already exists");
        }

        Facility saved = facilityRepository.save(facility);

        try {
            auditService.logAction(
                    "FACILITY_CREATED",
                    "FACILITY",
                    saved.getId().toString(),
                    "system",
                    null,
                    null,
                    Map.of("name", saved.getName(), "code", saved.getCode())
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Facility created: {} ({})", saved.getName(), saved.getCode());
        return saved;
    }

    @Transactional
    public Facility updateFacility(UUID facilityId, Facility updateData) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        facility.setName(updateData.getName());
        facility.setAddress(updateData.getAddress());
        facility.setPhone(updateData.getPhone());
        facility.setEmail(updateData.getEmail());
        facility.setActive(updateData.isActive());

        Facility saved = facilityRepository.save(facility);

        try {
            auditService.logAction(
                    "FACILITY_UPDATED",
                    "FACILITY",
                    facilityId.toString(),
                    "system",
                    null,
                    null,
                    Map.of("name", saved.getName())
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        return saved;
    }

    public Facility getFacility(UUID facilityId) {
        return facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found with ID: " + facilityId));
    }

    @Transactional
    public Department createDepartment(Department department) {
        Department saved = departmentRepository.save(department);

        try {
            auditService.logAction(
                    "DEPARTMENT_CREATED",
                    "DEPARTMENT",
                    saved.getId().toString(),
                    "system",
                    null,
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

        return saved;
    }

    @Transactional
    public User assignStaffToFacility(UUID userId, UUID facilityId, boolean isPrimary) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        if (user.getFacilities() == null) {
            user.setFacilities(new HashSet<>());
        }
        user.getFacilities().add(facility);

        if (isPrimary) {
            user.setPrimaryFacility(facility);
        }

        User saved = userRepository.save(user);

        try {
            auditService.logAction(
                    "STAFF_ASSIGNED",
                    "USER",
                    userId.toString(),
                    user.getUsername(),
                    null,
                    null,
                    Map.of(
                            "facilityId", facilityId,
                            "facilityName", facility.getName(),
                            "isPrimary", isPrimary
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Staff {} assigned to facility: {}", user.getUsername(), facility.getName());
        return saved;
    }

    @Transactional
    public User removeStaffFromFacility(UUID userId, UUID facilityId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        if (user.getFacilities() != null) {
            user.getFacilities().removeIf(f -> f.getId().equals(facilityId));
        }

        if (user.getPrimaryFacility() != null &&
                user.getPrimaryFacility().getId().equals(facilityId)) {
            user.setPrimaryFacility(user.getFacilities().isEmpty() ? null :
                    user.getFacilities().iterator().next());
        }

        User saved = userRepository.save(user);

        try {
            auditService.logAction(
                    "STAFF_REMOVED",
                    "USER",
                    userId.toString(),
                    user.getUsername(),
                    null,
                    null,
                    Map.of(
                            "facilityId", facilityId,
                            "facilityName", facility.getName()
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        return saved;
    }

    @Transactional
    public FacilityTransfer initiateTransfer(UUID ticketId, UUID toFacilityId,
                                             String reason, TransferType type,
                                             UUID actingUserId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        Facility fromFacility = ticket.getFacility();
        Facility toFacility = facilityRepository.findById(toFacilityId)
                .orElseThrow(() -> new RuntimeException("Target facility not found"));

        Department fromDepartment = ticket.getDepartment();
        Department toDepartment = departmentRepository
                .findByFacilityIdAndCode(toFacilityId, fromDepartment.getCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Target facility has no matching " + fromDepartment.getCode() + " department"
                ));

        boolean hasAvailableDoctors = departmentRepository.findAvailableDoctorsByDepartment(toDepartment.getId())
                .stream()
                .anyMatch(User::isActive);

        if (!hasAvailableDoctors && type != TransferType.EMERGENCY) {
            throw new IllegalStateException(
                    "Target department has no available doctors. For emergency transfers, use EMERGENCY type."
            );
        }

        FacilityTransfer transfer = FacilityTransfer.builder()
                .ticket(ticket)
                .fromFacility(fromFacility)
                .toFacility(toFacility)
                .fromDepartment(fromDepartment)
                .toDepartment(toDepartment)
                .transferReason(reason)
                .transferType(type)
                .status(TransferStatus.PENDING)
                .initiatedBy(actingUserId)
                .build();

        FacilityTransfer saved = transferRepository.save(transfer);

        try {
            auditService.logAction(
                    "TRANSFER_INITIATED",
                    "FACILITY_TRANSFER",
                    saved.getId().toString(),
                    ticket.getPatient() != null ? ticket.getPatient().getUsername() : "unknown",
                    null,
                    null,
                    Map.of(
                            "ticketId", ticketId,
                            "fromFacility", fromFacility.getName(),
                            "toFacility", toFacility.getName(),
                            "reason", reason,
                            "type", type.name(),
                            "hasAvailableDoctor", hasAvailableDoctors
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Transfer initiated for ticket {} from {} to {} (Doctor available: {})",
                ticket.getTicketNumber(), fromFacility.getName(), toFacility.getName(), hasAvailableDoctors);

        return saved;
    }

    @Transactional
    public FacilityTransfer approveTransfer(UUID transferId, UUID actingUserId) {
        FacilityTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found"));

        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException("Transfer is not in pending state");
        }

        boolean hasAvailableDoctors = departmentRepository.findAvailableDoctorsByDepartment(
                transfer.getToDepartment().getId()
        ).stream().anyMatch(User::isActive);

        if (!hasAvailableDoctors && transfer.getTransferType() != TransferType.EMERGENCY) {
            throw new IllegalStateException(
                    "Target department no longer has available doctors. Please try another facility or use emergency transfer."
            );
        }

        transfer.setStatus(TransferStatus.APPROVED);
        transfer.setApprovedBy(actingUserId);
        transfer.setApprovedAt(LocalDateTime.now());

        if (transfer.getTransferType() == TransferType.EMERGENCY) {
            autoAssignDoctorForTransfer(transfer);
        }

        executeTransfer(transfer);

        FacilityTransfer saved = transferRepository.save(transfer);

        try {
            auditService.logAction(
                    "TRANSFER_APPROVED",
                    "FACILITY_TRANSFER",
                    transferId.toString(),
                    "system",
                    null,
                    null,
                    Map.of(
                            "transferId", transferId,
                            "approvedBy", actingUserId,
                            "hasAvailableDoctor", hasAvailableDoctors
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        return saved;
    }

    private void autoAssignDoctorForTransfer(FacilityTransfer transfer) {
        List<User> availableDoctors = departmentRepository.findAvailableDoctorsByDepartment(
                transfer.getToDepartment().getId()
        );

        if (!availableDoctors.isEmpty()) {
            User leastBusyDoctor = availableDoctors.stream()
                    .min(Comparator.comparingInt(doc -> ticketRepository.countActiveTicketsForDoctor(doc.getId())))
                    .orElse(availableDoctors.getFirst());

            Ticket ticket = transfer.getTicket();
            ticket.setAssignedDoctor(leastBusyDoctor);
            ticketRepository.save(ticket);

            log.info("Auto-assigned doctor {} to emergency transfer {}",
                    leastBusyDoctor.getUsername(), transfer.getId());
        }
    }

    @Transactional
    public void executeTransfer(FacilityTransfer transfer) {
        Ticket ticket = transfer.getTicket();

        ticket.setFacility(transfer.getToFacility());
        ticket.setDepartment(transfer.getToDepartment());
        ticket.setTransferFromFacilityId(transfer.getFromFacility().getId());
        ticket.setTransferFromDepartmentId(transfer.getFromDepartment().getId());
        ticket.setTransferredAt(LocalDateTime.now());
        ticket.setTransferReason(transfer.getTransferReason());

        int position = ticketRepository.countActiveTickets(
                transfer.getToFacility().getId(),
                transfer.getToDepartment().getId()
        );
        ticket.setQueuePosition(position);

        ticketRepository.save(ticket);

        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setCompletedAt(LocalDateTime.now());

        try {
            auditService.logAction(
                    "TRANSFER_EXECUTED",
                    "TICKET",
                    ticket.getId().toString(),
                    ticket.getPatient() != null ? ticket.getPatient().getUsername() : "unknown",
                    null,
                    null,
                    Map.of(
                            "ticketNumber", ticket.getTicketNumber(),
                            "fromFacility", transfer.getFromFacility().getName(),
                            "toFacility", transfer.getToFacility().getName(),
                            "priority", ticket.getPriority()
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Transfer executed for ticket {} from {} to {}",
                ticket.getTicketNumber(),
                transfer.getFromFacility().getName(),
                transfer.getToFacility().getName());
    }

    public List<FacilityTransfer> getPendingTransfers() {
        return transferRepository.findByStatus(TransferStatus.PENDING);
    }

    public List<FacilityTransfer> getTransferHistory(UUID ticketId) {
        return transferRepository.findByTicketId(ticketId);
    }

    // ==================== BULK STAFF ASSIGNMENT ====================

    @Transactional
    public BulkAssignmentResult bulkAssignStaffToFacility(List<UUID> userIds, UUID facilityId,
                                                          boolean isPrimary, UUID actingUserId) {
        BulkAssignmentResult result = BulkAssignmentResult.builder()
                .successful(new ArrayList<>())
                .failed(new ArrayList<>())
                .build();

        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("User IDs list cannot be empty");
        }

        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        for (UUID userId : userIds) {
            try {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found: " + userId));

                if (user.getFacilities() != null &&
                        user.getFacilities().stream().anyMatch(f -> f.getId().equals(facilityId))) {
                    result.getFailed().add(BulkAssignmentError.builder()
                            .userId(userId)
                            .username(user.getUsername())
                            .reason("Already assigned to this facility")
                            .build());
                    continue;
                }

                if (user.getFacilities() == null) {
                    user.setFacilities(new HashSet<>());
                }
                user.getFacilities().add(facility);

                if (isPrimary) {
                    user.setPrimaryFacility(facility);
                }

                User saved = userRepository.save(user);

                result.getSuccessful().add(BulkAssignmentSuccess.builder()
                        .userId(saved.getId())
                        .username(saved.getUsername())
                        .email(saved.getEmail())
                        .role(saved.getRole().name())
                        .build());

                log.info("Bulk assigned staff {} to facility {}", saved.getUsername(), facility.getName());

            } catch (Exception e) {
                result.getFailed().add(BulkAssignmentError.builder()
                        .userId(userId)
                        .reason(e.getMessage())
                        .build());
                log.warn("Failed to assign user {}: {}", userId, e.getMessage());
            }
        }

        try {
            auditService.logAction(
                    "BULK_STAFF_ASSIGNMENT",
                    "FACILITY",
                    facilityId.toString(),
                    "system",
                    null,
                    null,
                    Map.of(
                            "facilityName", facility.getName(),
                            "totalAttempted", userIds.size(),
                            "successful", result.getSuccessful().size(),
                            "failed", result.getFailed().size(),
                            "isPrimary", isPrimary
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Bulk assignment completed: {} successful, {} failed out of {} attempts",
                result.getSuccessful().size(), result.getFailed().size(), userIds.size());

        return result;
    }

    @Transactional
    public BulkAssignmentResult bulkAssignStaffToDepartment(List<UUID> userIds, UUID departmentId,
                                                            UUID actingUserId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        BulkAssignmentResult result = BulkAssignmentResult.builder()
                .successful(new ArrayList<>())
                .failed(new ArrayList<>())
                .build();

        for (UUID userId : userIds) {
            try {
                User user = userRepository.findByIdWithDepartments(userId)
                        .orElseThrow(() -> new RuntimeException("User not found: " + userId));

                if (user.getRole() != UserRole.DOCTOR) {
                    result.getFailed().add(BulkAssignmentError.builder()
                            .userId(userId)
                            .username(user.getUsername())
                            .reason("User is not a doctor")
                            .build());
                    continue;
                }

                Facility facility = department.getFacility();
                if (facility != null && (user.getFacilities() == null ||
                        user.getFacilities().stream().noneMatch(f -> f.getId().equals(facility.getId())))) {
                    if (user.getFacilities() == null) {
                        user.setFacilities(new HashSet<>());
                    }
                    user.getFacilities().add(facility);
                }

                if (user.getDepartments() == null) {
                    user.setDepartments(new HashSet<>());
                }
                user.getDepartments().add(department);

                if (user.getPrimaryDepartment() == null) {
                    user.setPrimaryDepartment(department);
                }

                User saved = userRepository.save(user);

                result.getSuccessful().add(BulkAssignmentSuccess.builder()
                        .userId(saved.getId())
                        .username(saved.getUsername())
                        .email(saved.getEmail())
                        .role(saved.getRole().name())
                        .departmentName(department.getName())
                        .build());

                log.info("Bulk assigned doctor {} to department {}",
                        saved.getUsername(), department.getName());

            } catch (Exception e) {
                result.getFailed().add(BulkAssignmentError.builder()
                        .userId(userId)
                        .reason(e.getMessage())
                        .build());
                log.warn("Failed to assign user {}: {}", userId, e.getMessage());
            }
        }

        try {
            auditService.logAction(
                    "BULK_DOCTOR_DEPARTMENT_ASSIGNMENT",
                    "DEPARTMENT",
                    departmentId.toString(),
                    "system",
                    null,
                    null,
                    Map.of(
                            "departmentName", department.getName(),
                            "totalAttempted", userIds.size(),
                            "successful", result.getSuccessful().size(),
                            "failed", result.getFailed().size()
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to log audit: {}", e.getMessage());
        }

        log.info("Bulk department assignment completed: {} successful, {} failed",
                result.getSuccessful().size(), result.getFailed().size());

        return result;
    }

    @Transactional
    public BulkAssignmentResult bulkRemoveStaffFromFacility(List<UUID> userIds, UUID facilityId) {
        BulkAssignmentResult result = BulkAssignmentResult.builder()
                .successful(new ArrayList<>())
                .failed(new ArrayList<>())
                .build();

        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        for (UUID userId : userIds) {
            try {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found: " + userId));

                if (user.getFacilities() != null) {
                    user.getFacilities().removeIf(f -> f.getId().equals(facilityId));
                }

                if (user.getPrimaryFacility() != null &&
                        user.getPrimaryFacility().getId().equals(facilityId)) {
                    user.setPrimaryFacility(user.getFacilities().isEmpty() ? null :
                            user.getFacilities().iterator().next());
                }

                User saved = userRepository.save(user);

                result.getSuccessful().add(BulkAssignmentSuccess.builder()
                        .userId(saved.getId())
                        .username(saved.getUsername())
                        .email(saved.getEmail())
                        .role(saved.getRole().name())
                        .build());

                log.info("Bulk removed staff {} from facility {}",
                        saved.getUsername(), facility.getName());

            } catch (Exception e) {
                result.getFailed().add(BulkAssignmentError.builder()
                        .userId(userId)
                        .reason(e.getMessage())
                        .build());
                log.warn("Failed to remove user {}: {}", userId, e.getMessage());
            }
        }

        return result;
    }

    // ==================== SMART DEPARTMENT ANALYTICS ====================

    public DepartmentAnalytics getDepartmentAnalytics(UUID departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        List<Ticket> activeTickets = ticketRepository.findActiveTicketsByFacilityAndDepartment(
                department.getFacility().getId(), departmentId
        );

        List<User> doctors = departmentRepository.findAvailableDoctorsByDepartment(departmentId);
        long doctorCount = doctors.size();
        long activeDoctorCount = doctors.stream().filter(User::isActive).count();

        int activePatients = activeTickets.size();

        double doctorToPatientRatio = doctorCount > 0 ? (double) activePatients / doctorCount : 0;
        String loadStatus = calculateLoadStatus(doctorToPatientRatio, activePatients);

        double avgWaitTime = activeTickets.stream()
                .filter(t -> t.getEstimatedWaitMinutes() != null)
                .mapToInt(Ticket::getEstimatedWaitMinutes)
                .average()
                .orElse(0.0);

        Map<String, Long> priorityDistribution = activeTickets.stream()
                .filter(t -> t.getPriority() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getPriority().name(),
                        Collectors.counting()
                ));

        // 🔥 FIXED: Use checkFacilityAvailability() instead of getEmergencyStatus()
        boolean isEmergencyMode = false;
        try {
            EmergencyService.FacilityAvailabilityDto availability =
                    emergencyService.checkFacilityAvailability(
                            department.getFacility().getId(),
                            department.getCode()
                    );
            isEmergencyMode = availability != null && !availability.isAvailable();
        } catch (Exception e) {
            log.warn("Could not check emergency status for department {}: {}", departmentId, e.getMessage());
        }

        List<String> recommendations = generateRecommendations(
                department,
                doctorCount,
                activeDoctorCount,
                activePatients,
                doctorToPatientRatio,
                avgWaitTime,
                isEmergencyMode
        );

        List<DoctorLoad> doctorLoads = doctors.stream()
                .map(doc -> {
                    int patientCount = ticketRepository.countActiveTicketsForDoctor(doc.getId());
                    return DoctorLoad.builder()
                            .doctorId(doc.getId())
                            .doctorName(doc.getFirstName() + " " + doc.getLastName())
                            .patientCount(patientCount)
                            .isActive(doc.isActive())
                            .status(patientCount > 10 ? "OVERLOADED" :
                                    patientCount > 5 ? "BUSY" : "AVAILABLE")
                            .build();
                })
                .sorted(Comparator.comparingInt(DoctorLoad::getPatientCount))
                .collect(Collectors.toList());

        return DepartmentAnalytics.builder()
                .departmentId(departmentId)
                .departmentName(department.getName())
                .facilityName(department.getFacility().getName())
                .totalDoctors(doctorCount)
                .activeDoctors(activeDoctorCount)
                .inactiveDoctors(doctorCount - activeDoctorCount)
                .activePatients(activePatients)
                .doctorToPatientRatio(doctorToPatientRatio)
                .loadStatus(loadStatus)
                .averageWaitMinutes(Math.round(avgWaitTime))
                .priorityDistribution(priorityDistribution)
                .isEmergencyMode(isEmergencyMode)
                .doctorLoads(doctorLoads)
                .recommendations(recommendations)
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    private String calculateLoadStatus(double ratio, int patientCount) {
        if (ratio <= 0) return "NO_DOCTORS";
        if (ratio <= 3) return "HEALTHY";
        if (ratio <= 5) return "MODERATE";
        if (ratio <= 8) return "BUSY";
        return "OVERLOADED";
    }

    private List<String> generateRecommendations(Department department,
                                                 long totalDoctors,
                                                 long activeDoctors,
                                                 int activePatients,
                                                 double ratio,
                                                 double avgWaitTime,
                                                 boolean isEmergencyMode) {
        List<String> recommendations = new ArrayList<>();

        if (isEmergencyMode) {
            recommendations.add("🚨 EMERGENCY MODE ACTIVE - All available doctors should focus on emergency cases");
        }

        if (totalDoctors == 0) {
            recommendations.add("❌ No doctors assigned to this department. Please assign doctors immediately.");
            return recommendations;
        }

        if (activeDoctors < totalDoctors) {
            recommendations.add("⚠️ " + (totalDoctors - activeDoctors) + " doctor(s) are currently inactive or on leave");
        }

        if (ratio > 8) {
            recommendations.add("🔴 CRITICAL: " + Math.round(ratio) + " patients per doctor. Consider:");
            recommendations.add("  • Transferring patients to other departments");
            recommendations.add("  • Requesting additional staff");
            recommendations.add("  • Activating emergency mode if needed");
        } else if (ratio > 5) {
            recommendations.add("🟡 High patient load: " + Math.round(ratio) + " patients per doctor");
            recommendations.add("  • Consider transferring some patients");
            recommendations.add("  • Check if other departments can assist");
        } else if (ratio > 3) {
            recommendations.add("🟢 Moderate patient load. Department is running smoothly.");
        } else {
            recommendations.add("✅ Good patient-to-doctor ratio. Department is under capacity.");
        }

        if (avgWaitTime > 30) {
            recommendations.add("⏱️ Average wait time is " + Math.round(avgWaitTime) + " minutes - patients are waiting too long");
            recommendations.add("  • Consider prioritizing high-priority patients");
        }

        if (ratio > 5 && activePatients > 10) {
            List<Department> otherDepartments = departmentRepository.findActiveByFacility(
                    department.getFacility().getId()
            );

            for (Department dept : otherDepartments) {
                if (dept.getId().equals(department.getId())) continue;

                long deptDoctors = departmentRepository.countDoctorsByDepartment(dept.getId());
                int deptPatients = ticketRepository.countActiveTicketsByDepartment(dept.getId());

                if (deptDoctors > 0 && (double) deptPatients / deptDoctors < 3) {
                    recommendations.add("💡 " + dept.getName() + " has capacity (" +
                            deptPatients + " patients, " + deptDoctors + " doctors)");
                    recommendations.add("  • Consider transferring patients to " + dept.getName());
                }
            }
        }

        return recommendations;
    }

    public List<DepartmentAnalytics> getAllDepartmentAnalytics(UUID facilityId) {
        List<Department> departments = departmentRepository.findActiveByFacility(facilityId);

        return departments.stream()
                .map(dept -> getDepartmentAnalytics(dept.getId()))
                .sorted((a, b) -> a.getLoadStatus().compareToIgnoreCase(b.getLoadStatus()))
                .collect(Collectors.toList());
    }

    public FacilityAnalyticsSummary getFacilityAnalyticsSummary(UUID facilityId) {
        List<DepartmentAnalytics> departmentAnalytics = getAllDepartmentAnalytics(facilityId);

        long totalDoctors = departmentAnalytics.stream()
                .mapToLong(DepartmentAnalytics::getTotalDoctors)
                .sum();

        long totalPatients = departmentAnalytics.stream()
                .mapToInt(DepartmentAnalytics::getActivePatients)
                .sum();

        long healthyDepartments = departmentAnalytics.stream()
                .filter(d -> "HEALTHY".equals(d.getLoadStatus()))
                .count();

        long overloadedDepartments = departmentAnalytics.stream()
                .filter(d -> "OVERLOADED".equals(d.getLoadStatus()))
                .count();

        long noDoctorDepartments = departmentAnalytics.stream()
                .filter(d -> "NO_DOCTORS".equals(d.getLoadStatus()))
                .count();

        return FacilityAnalyticsSummary.builder()
                .facilityId(facilityId)
                .totalDepartments(departmentAnalytics.size())
                .totalDoctors(totalDoctors)
                .totalPatients(totalPatients)
                .healthyDepartments(healthyDepartments)
                .overloadedDepartments(overloadedDepartments)
                .noDoctorDepartments(noDoctorDepartments)
                .departmentAnalytics(departmentAnalytics)
                .overallStatus(totalDoctors > 0 && totalPatients < totalDoctors * 5 ? "HEALTHY" : "UNDER_STAFFED")
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    // ==================== TELEMETRY & METRICS ====================

    public Map<String, Object> getMultiFacilityTelemetry() {
        List<Facility> facilities = facilityRepository.findAll();
        Map<String, Object> telemetry = new HashMap<>();

        List<Map<String, Object>> facilityMetrics = facilities.stream()
                .map(this::getFacilityMetrics)
                .collect(Collectors.toList());

        telemetry.put("facilities", facilityMetrics);
        telemetry.put("totalPatients", facilityMetrics.stream()
                .mapToInt(m -> (int) m.getOrDefault("activePatients", 0))
                .sum());
        telemetry.put("totalStaff", facilityMetrics.stream()
                .mapToInt(m -> (int) m.getOrDefault("staffCount", 0))
                .sum());
        telemetry.put("updatedAt", LocalDateTime.now());

        return telemetry;
    }

    public Map<String, Object> getFacilityMetrics(Facility facility) {
        int activePatients = 0;
        try {
            activePatients = ticketRepository.countActiveTicketsByFacility(facility.getId());
        } catch (Exception e) {
            log.warn("Could not get active tickets: {}", e.getMessage());
        }

        long staffCount = 0;
        try {
            staffCount = userRepository.countActiveByFacilityAndRole(
                    facility.getId(),
                    "DOCTOR"
            );
        } catch (Exception e) {
            log.warn("Could not get staff count: {}", e.getMessage());
        }

        List<Ticket> activeTickets = ticketRepository.findActiveTicketsByFacility(facility.getId());

        double avgWaitTime = activeTickets.stream()
                .filter(t -> t.getEstimatedWaitMinutes() != null)
                .mapToInt(Ticket::getEstimatedWaitMinutes)
                .average()
                .orElse(0.0);

        List<Map<String, Object>> deptMetrics = new ArrayList<>();
        try {
            List<Department> departments = departmentRepository.findActiveByFacility(facility.getId());
            deptMetrics = departments.stream()
                    .map(dept -> {
                        Map<String, Object> metrics = new HashMap<>();
                        metrics.put("name", dept.getName());
                        metrics.put("code", dept.getCode());
                        try {
                            metrics.put("patients", ticketRepository.countActiveTickets(
                                    facility.getId(), dept.getId()
                            ));
                        } catch (Exception e) {
                            metrics.put("patients", 0);
                        }
                        metrics.put("active", dept.isActive());
                        return metrics;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Could not get departments: {}", e.getMessage());
        }

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("id", facility.getId());
        metrics.put("name", facility.getName());
        metrics.put("code", facility.getCode());
        metrics.put("activePatients", activePatients);
        metrics.put("staffCount", staffCount);
        metrics.put("avgWaitMinutes", Math.round(avgWaitTime));
        metrics.put("departments", deptMetrics);
        metrics.put("isActive", facility.isActive());

        return metrics;
    }

    // ==================== INNER CLASSES ====================

    @lombok.Data
    @lombok.Builder
    public static class BulkAssignmentResult {
        private List<BulkAssignmentSuccess> successful;
        private List<BulkAssignmentError> failed;
    }

    @lombok.Data
    @lombok.Builder
    public static class BulkAssignmentSuccess {
        private UUID userId;
        private String username;
        private String email;
        private String role;
        private String departmentName;
    }

    @lombok.Data
    @lombok.Builder
    public static class BulkAssignmentError {
        private UUID userId;
        private String username;
        private String reason;
    }

    @lombok.Data
    @lombok.Builder
    public static class DepartmentAnalytics {
        private UUID departmentId;
        private String departmentName;
        private String facilityName;
        private long totalDoctors;
        private long activeDoctors;
        private long inactiveDoctors;
        private int activePatients;
        private double doctorToPatientRatio;
        private String loadStatus;
        private long averageWaitMinutes;
        private Map<String, Long> priorityDistribution;
        private boolean isEmergencyMode;
        private List<DoctorLoad> doctorLoads;
        private List<String> recommendations;
        private LocalDateTime analyzedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class DoctorLoad {
        private UUID doctorId;
        private String doctorName;
        private int patientCount;
        private boolean isActive;
        private String status;
    }

    @lombok.Data
    @lombok.Builder
    public static class FacilityAnalyticsSummary {
        private UUID facilityId;
        private long totalDepartments;
        private long totalDoctors;
        private long totalPatients;
        private long healthyDepartments;
        private long overloadedDepartments;
        private long noDoctorDepartments;
        private List<DepartmentAnalytics> departmentAnalytics;
        private String overallStatus;
        private LocalDateTime analyzedAt;
    }
}