package com.mvura.repository;

import com.mvura.model.Priority;  // ← ADD THIS IMPORT
import com.mvura.model.Ticket;
import com.mvura.model.TicketStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    // ===== ACTIVE TICKET BY STATUS (NOT DISCHARGED OR CANCELLED) =====
    @Query("SELECT t FROM Ticket t WHERE t.patient.id = :patientId AND t.status NOT IN ('DISCHARGED', 'CANCELLED') ORDER BY t.createdAt DESC")
    Optional<Ticket> findLatestActiveTicketByPatient(@Param("patientId") UUID patientId);

    @Query("SELECT COUNT(t) > 0 FROM Ticket t WHERE t.patient.id = :patientId AND t.status NOT IN ('DISCHARGED', 'CANCELLED')")
    boolean hasActiveTicket(@Param("patientId") UUID patientId);

    @Query("SELECT t FROM Ticket t WHERE t.patient.id = :patientId AND t.status NOT IN ('DISCHARGED', 'CANCELLED') ORDER BY t.createdAt DESC")
    List<Ticket> findAllActiveTicketsForPatient(@Param("patientId") UUID patientId);

    // ===== GET ALL TICKETS FOR PATIENT (FOR DEBUGGING) =====
    @Query("SELECT t FROM Ticket t WHERE t.patient.id = :patientId ORDER BY t.createdAt DESC")
    List<Ticket> findAllByPatientId(@Param("patientId") UUID patientId);

    @Query("SELECT t FROM Ticket t WHERE t.patient.id = :patientId AND t.active = true AND t.status NOT IN ('DISCHARGED', 'CANCELLED')")
    Optional<Ticket> findActiveTicketByPatient(@Param("patientId") UUID patientId);

    // ===== QUEUE QUERIES =====
    @Query("SELECT t FROM Ticket t WHERE t.facility.id = :facilityId AND t.department.id = :departmentId AND t.active = true AND t.status NOT IN ('DISCHARGED', 'CANCELLED') ORDER BY t.priority DESC, t.createdAt ASC")
    List<Ticket> findActiveTicketsByFacilityAndDepartment(
            @Param("facilityId") UUID facilityId,
            @Param("departmentId") UUID departmentId
    );

    // ===== QUEUE QUERIES WITH PAGINATION =====
    @Query("SELECT t FROM Ticket t WHERE t.facility.id = :facilityId AND t.department.id = :departmentId AND t.active = true AND t.status NOT IN ('DISCHARGED', 'CANCELLED') ORDER BY t.priority DESC, t.createdAt ASC")
    List<Ticket> findActiveTicketsByFacilityAndDepartmentWithPagination(
            @Param("facilityId") UUID facilityId,
            @Param("departmentId") UUID departmentId,
            Pageable pageable
    );

    @Query("SELECT t FROM Ticket t WHERE t.facility.id = :facilityId AND t.active = true AND t.status NOT IN ('DISCHARGED', 'CANCELLED')")
    List<Ticket> findActiveTicketsByFacility(@Param("facilityId") UUID facilityId);

    // ===== COUNT QUERIES =====
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.facility.id = :facilityId AND t.department.id = :departmentId AND t.active = true AND t.status NOT IN ('DISCHARGED', 'CANCELLED')")
    int countActiveTickets(@Param("facilityId") UUID facilityId, @Param("departmentId") UUID departmentId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.facility.id = :facilityId AND t.active = true AND t.status NOT IN ('DISCHARGED', 'CANCELLED')")
    int countActiveTicketsByFacility(@Param("facilityId") UUID facilityId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.department.id = :departmentId AND t.active = true AND t.status NOT IN ('DISCHARGED', 'CANCELLED')")
    int countActiveTicketsByDepartment(@Param("departmentId") UUID departmentId);

    // ===== COUNT BY PRIORITY =====
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.facility.id = :facilityId AND t.department.id = :departmentId AND t.active = true AND t.priority = :priority AND t.status NOT IN ('DISCHARGED', 'CANCELLED')")
    long countActiveTicketsWithPriority(
            @Param("facilityId") UUID facilityId,
            @Param("departmentId") UUID departmentId,
            @Param("priority") Priority priority
    );

    // ===== DOCTOR QUERIES =====
    @Query("SELECT t FROM Ticket t WHERE t.assignedDoctor.id = :doctorId AND t.active = true AND t.status IN ('TRIAGED', 'IN_CONSULTATION', 'LAB_PENDING', 'LAB_COMPLETED')")
    List<Ticket> findTicketsForDoctor(@Param("doctorId") UUID doctorId);

    @Query("SELECT t FROM Ticket t WHERE t.assignedDoctor.id = :doctorId AND t.status = 'IN_CONSULTATION'")
    List<Ticket> findActiveConsultationsForDoctor(@Param("doctorId") UUID doctorId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.assignedDoctor.id = :doctorId AND t.active = true AND t.status NOT IN ('DISCHARGED', 'CANCELLED')")
    int countActiveTicketsForDoctor(@Param("doctorId") UUID doctorId);

    // ===== METRICS QUERIES =====
    @Query("SELECT AVG(t.estimatedWaitMinutes) FROM Ticket t WHERE t.facility.id = :facilityId AND t.active = true AND t.status NOT IN ('DISCHARGED', 'CANCELLED')")
    Double getAverageWaitTimeByFacility(@Param("facilityId") UUID facilityId);

    @Query("SELECT t.priority as priority, COUNT(t) as count FROM Ticket t WHERE t.facility.id = :facilityId AND t.active = true AND t.status NOT IN ('DISCHARGED', 'CANCELLED') GROUP BY t.priority")
    List<Object[]> getPriorityDistributionByFacility(@Param("facilityId") UUID facilityId);

    // ===== EMERGENCY MODE QUERIES =====
    @Query("SELECT t FROM Ticket t WHERE t.facility.id = :facilityId AND t.department.id = :departmentId " +
            "AND t.emergencyModeActive = true AND t.emergencyModeEndedAt > CURRENT_TIMESTAMP")
    List<Ticket> findActiveEmergencyTickets(@Param("facilityId") UUID facilityId, @Param("departmentId") UUID departmentId);

    @Query("SELECT t FROM Ticket t WHERE t.emergencyModeActive = true AND t.emergencyModeEndedAt < CURRENT_TIMESTAMP")
    List<Ticket> findExpiredEmergencyTickets();

    @Query("SELECT COUNT(t) > 0 FROM Ticket t WHERE t.facility.id = :facilityId AND t.department.id = :departmentId AND t.isBooked = true AND t.appointmentTime BETWEEN :now AND :soon AND t.status NOT IN ('DISCHARGED', 'CANCELLED')")
    boolean hasUpcomingBookedTicket(
            @Param("facilityId") UUID facilityId,
            @Param("departmentId") UUID departmentId,
            @Param("now") LocalDateTime now,
            @Param("soon") LocalDateTime soon
    );

    // ===== ACTIVE QUEUE TICKETS (Excludes completed and discharged) =====
    @Query("SELECT t FROM Ticket t WHERE t.facility.id = :facilityId AND t.department.id = :departmentId AND t.active = true AND t.status NOT IN ('DISCHARGED', 'CANCELLED', 'CONSULTATION_DONE') ORDER BY t.priority DESC, t.createdAt ASC")
    List<Ticket> findActiveQueueTicketsByFacilityAndDepartment(
            @Param("facilityId") UUID facilityId,
            @Param("departmentId") UUID departmentId
    );

    // ===== COUNT ACTIVE QUEUE TICKETS (Excludes CONSULTATION_DONE from count) =====
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.facility.id = :facilityId AND t.department.id = :departmentId AND t.active = true AND t.status NOT IN ('DISCHARGED', 'CANCELLED', 'CONSULTATION_DONE')")
    int countActiveQueueTickets(
            @Param("facilityId") UUID facilityId,
            @Param("departmentId") UUID departmentId
    );

    // ===== GET TICKETS THAT NEED POSITIONS (Waiting patients only) =====
    @Query("SELECT t FROM Ticket t WHERE t.facility.id = :facilityId AND t.department.id = :departmentId AND t.active = true AND t.status IN ('TRIAGED', 'LAB_PENDING', 'LAB_COMPLETED') ORDER BY t.priority DESC, t.createdAt ASC")
    List<Ticket> findWaitingTicketsByFacilityAndDepartment(
            @Param("facilityId") UUID facilityId,
            @Param("departmentId") UUID departmentId
    );

    // ===== GET ALL QUEUE TICKETS FOR DISPLAY (Includes IN_CONSULTATION and CONSULTATION_DONE) =====
    @Query("SELECT t FROM Ticket t WHERE t.facility.id = :facilityId AND t.department.id = :departmentId AND t.active = true AND t.status NOT IN ('DISCHARGED', 'CANCELLED') ORDER BY CASE WHEN t.status = 'CONSULTATION_DONE' THEN 1 WHEN t.status = 'IN_CONSULTATION' THEN 2 ELSE 0 END, t.priority DESC, t.createdAt ASC")
    List<Ticket> findAllQueueTicketsForDisplay(
            @Param("facilityId") UUID facilityId,
            @Param("departmentId") UUID departmentId
    );

}