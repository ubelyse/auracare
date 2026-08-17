package com.mvura.repository;

import com.mvura.model.Appointment;
import com.mvura.model.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    // ===== YOUR EXISTING METHODS =====
    List<Appointment> findByPatientId(UUID patientId);
    List<Appointment> findByPatientIdAndStatus(UUID patientId, AppointmentStatus status);

    @Query("SELECT a FROM Appointment a WHERE a.facility.id = :facilityId AND a.department.id = :departmentId AND a.appointmentDateTime BETWEEN :start AND :end AND a.status = 'SCHEDULED'")
    List<Appointment> findScheduledAppointmentsBetween(
            @Param("facilityId") UUID facilityId,
            @Param("departmentId") UUID departmentId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT COUNT(a) > 0 FROM Appointment a WHERE a.facility.id = :facilityId AND a.department.id = :departmentId AND a.appointmentDateTime BETWEEN :now AND :soon AND a.status = 'SCHEDULED'")
    boolean hasUpcomingAppointment(
            @Param("facilityId") UUID facilityId,
            @Param("departmentId") UUID departmentId,
            @Param("now") LocalDateTime now,
            @Param("soon") LocalDateTime soon
    );

    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId AND a.status = 'SCHEDULED' ORDER BY a.appointmentDateTime ASC")
    List<Appointment> findUpcomingAppointmentsForPatient(@Param("patientId") UUID patientId);

    Optional<Appointment> findByTicketId(UUID ticketId);

    @Query("SELECT a FROM Appointment a WHERE a.facility.id = :facilityId AND a.department.id = :departmentId AND a.status = 'SCHEDULED' ORDER BY a.appointmentDateTime ASC")
    List<Appointment> findScheduledAppointmentsByFacilityAndDepartment(
            @Param("facilityId") UUID facilityId,
            @Param("departmentId") UUID departmentId
    );

    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId AND (a.appointmentDateTime < CURRENT_TIMESTAMP OR a.status != 'SCHEDULED') ORDER BY a.appointmentDateTime DESC")
    List<Appointment> findAppointmentHistoryForPatient(@Param("patientId") UUID patientId);

    @Query("SELECT a FROM Appointment a WHERE a.appointmentDateTime BETWEEN :start AND :end AND a.status = :status")
    List<Appointment> findByAppointmentDateTimeBetweenAndStatus(@Param("start") LocalDateTime start,
                                                                @Param("end") LocalDateTime end,
                                                                @Param("status") AppointmentStatus status);

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDateTime BETWEEN :start AND :end AND a.status = 'SCHEDULED'")
    List<Appointment> findByDoctorIdAndAppointmentDateTimeBetween(@Param("doctorId") UUID doctorId,
                                                                  @Param("start") LocalDateTime start,
                                                                  @Param("end") LocalDateTime end);

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND DATE(a.appointmentDateTime) = :date AND a.status = 'SCHEDULED'")
    List<Appointment> findAvailableSlotsForDoctor(@Param("doctorId") UUID doctorId,
                                                  @Param("date") LocalDate date);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.patient.id = :patientId AND a.appointmentDateTime BETWEEN :start AND :end AND a.status = 'SCHEDULED'")
    long countByPatientIdAndAppointmentDateTimeBetween(@Param("patientId") UUID patientId,
                                                       @Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.patient.id = :patientId")
    long countByPatientId(@Param("patientId") UUID patientId);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.patient.id = :patientId AND a.status = :status")
    long countByPatientIdAndStatus(@Param("patientId") UUID patientId,
                                   @Param("status") AppointmentStatus status);

    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId AND a.status = 'SCHEDULED'")
    Page<Appointment> findUpcomingAppointmentsForPatient(@Param("patientId") UUID patientId, Pageable pageable);

    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId AND (a.appointmentDateTime < CURRENT_TIMESTAMP OR a.status != 'SCHEDULED') ORDER BY a.appointmentDateTime DESC")
    Page<Appointment> findAppointmentHistoryForPatient(@Param("patientId") UUID patientId, Pageable pageable);

    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId AND a.status = :status ORDER BY a.appointmentDateTime DESC")
    Page<Appointment> findHistoryByPatientAndStatus(@Param("patientId") UUID patientId,
                                                    @Param("status") String status,
                                                    Pageable pageable);

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND a.status = :status AND a.appointmentDateTime > :now")
    Page<Appointment> findByDoctorIdAndStatusAndAppointmentDateTimeAfter(@Param("doctorId") UUID doctorId,
                                                                         @Param("status") AppointmentStatus status,
                                                                         @Param("now") LocalDateTime now,
                                                                         Pageable pageable);

    @Query("SELECT a FROM Appointment a WHERE a.facility.id = :facilityId AND a.appointmentDateTime BETWEEN :start AND :end")
    Page<Appointment> findByFacilityIdAndAppointmentDateTimeBetween(@Param("facilityId") UUID facilityId,
                                                                    @Param("start") LocalDateTime start,
                                                                    @Param("end") LocalDateTime end,
                                                                    Pageable pageable);

    @Query("SELECT a FROM Appointment a WHERE a.facility.id = :facilityId AND a.appointmentDateTime BETWEEN :start AND :end")
    List<Appointment> findByFacilityIdAndDateBetween(@Param("facilityId") UUID facilityId,
                                                     @Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.facility.id = :facilityId AND a.appointmentDateTime BETWEEN :start AND :end")
    long countByFacilityIdAndDateBetween(@Param("facilityId") UUID facilityId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.facility.id = :facilityId AND a.appointmentDateTime BETWEEN :start AND :end AND a.status = :status")
    long countByFacilityIdAndDateBetweenAndStatus(@Param("facilityId") UUID facilityId,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end,
                                                  @Param("status") AppointmentStatus status);

    // ===== ✅ ADD THESE NEW METHODS FOR AUTO-CLEANUP =====

    /**
     * Find appointments that have expired check-in windows
     * - Status is SCHEDULED
     * - Check-in window has closed (checkInCloses < now)
     * - No ticket was created (ticketId IS NULL)
     */
    @Query("SELECT a FROM Appointment a " +
            "WHERE a.status = 'SCHEDULED' " +
            "AND a.checkInCloses < :now " +
            "AND a.ticketId IS NULL")
    List<Appointment> findExpiredAppointments(@Param("now") LocalDateTime now);

    /**
     * Check if a check-in window is expired for a specific appointment
     */
    @Query("SELECT COUNT(a) > 0 FROM Appointment a " +
            "WHERE a.id = :appointmentId " +
            "AND a.status = 'SCHEDULED' " +
            "AND a.checkInCloses < :now " +
            "AND a.ticketId IS NULL")
    boolean isExpiredAppointment(@Param("appointmentId") UUID appointmentId,
                                 @Param("now") LocalDateTime now);

    /**
     * Mark an appointment as NO_SHOW
     */
    @Modifying
    @Transactional
    @Query("UPDATE Appointment a SET a.status = 'NO_SHOW' WHERE a.id = :appointmentId AND a.status = 'SCHEDULED'")
    int markAsNoShow(@Param("appointmentId") UUID appointmentId);
}