package com.mvura.repository;

import com.mvura.model.Billing;
import com.mvura.model.BillingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingRepository extends JpaRepository<Billing, UUID> {

    // ===== BASIC FINDERS =====

    Optional<Billing> findByInvoiceNumber(String invoiceNumber);

    @Query("SELECT b FROM Billing b WHERE b.patient.id = :patientId")
    List<Billing> findByPatientId(@Param("patientId") UUID patientId);

    List<Billing> findByFacilityId(UUID facilityId);

    List<Billing> findByPatientIdAndStatus(UUID patientId, BillingStatus status);

    List<Billing> findByFacilityIdAndStatus(UUID facilityId, BillingStatus status);

    List<Billing> findByTicketId(UUID ticketId);

    // ===== DATE RANGE QUERIES =====

    @Query("SELECT b FROM Billing b WHERE b.facility.id = :facilityId AND b.issuedAt BETWEEN :start AND :end")
    List<Billing> findByFacilityAndDateRange(@Param("facilityId") UUID facilityId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    @Query("SELECT b FROM Billing b WHERE b.facility.id = :facilityId AND b.status = :status AND b.issuedAt BETWEEN :start AND :end")
    List<Billing> findByFacilityStatusAndDateRange(@Param("facilityId") UUID facilityId,
                                                   @Param("status") BillingStatus status,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    @Query("SELECT b FROM Billing b WHERE b.patient.id = :patientId AND b.issuedAt BETWEEN :start AND :end")
    List<Billing> findByPatientAndDateRange(@Param("patientId") UUID patientId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    // ===== REVENUE & FINANCIAL REPORTS =====

    @Query("SELECT SUM(b.totalAmount) FROM Billing b WHERE b.facility.id = :facilityId AND b.status = 'PAID' AND b.paidAt BETWEEN :start AND :end")
    BigDecimal sumPaidAmountByFacilityAndDateRange(@Param("facilityId") UUID facilityId,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    @Query("SELECT SUM(b.paidAmount) FROM Billing b WHERE b.facility.id = :facilityId AND b.status = 'PAID' AND b.paidAt BETWEEN :start AND :end")
    BigDecimal sumPatientPaymentsByFacilityAndDateRange(@Param("facilityId") UUID facilityId,
                                                        @Param("start") LocalDateTime start,
                                                        @Param("end") LocalDateTime end);

    @Query("SELECT SUM(b.insuranceAmount) FROM Billing b WHERE b.facility.id = :facilityId AND b.status = 'PAID' AND b.paidAt BETWEEN :start AND :end")
    BigDecimal sumInsurancePaymentsByFacilityAndDateRange(@Param("facilityId") UUID facilityId,
                                                          @Param("start") LocalDateTime start,
                                                          @Param("end") LocalDateTime end);

    @Query("SELECT SUM(b.patientAmount - b.paidAmount) FROM Billing b WHERE b.facility.id = :facilityId AND b.status = 'PENDING'")
    BigDecimal sumOutstandingAmount(@Param("facilityId") UUID facilityId);

    @Query("SELECT b.serviceCode, SUM(b.totalAmount) FROM Billing b WHERE b.facility.id = :facilityId AND b.status = 'PAID' AND b.paidAt BETWEEN :start AND :end GROUP BY b.serviceCode")
    List<Object[]> getRevenueByService(@Param("facilityId") UUID facilityId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    @Query("SELECT b.insuranceType, SUM(b.totalAmount) FROM Billing b WHERE b.facility.id = :facilityId AND b.status = 'PAID' AND b.paidAt BETWEEN :start AND :end GROUP BY b.insuranceType")
    List<Object[]> getRevenueByInsuranceType(@Param("facilityId") UUID facilityId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    @Query("SELECT AVG(b.totalAmount) FROM Billing b WHERE b.facility.id = :facilityId")
    BigDecimal getAverageBillAmount(@Param("facilityId") UUID facilityId);

    // ===== INSURANCE CLAIMS =====

    @Query("SELECT b.insuranceType, SUM(b.insuranceAmount) FROM Billing b WHERE b.status = 'PAID' GROUP BY b.insuranceType")
    List<Object[]> getInsuranceClaimsSummary();

    @Query("SELECT b.insuranceType, SUM(b.insuranceAmount) FROM Billing b WHERE b.facility.id = :facilityId AND b.status = 'PAID' GROUP BY b.insuranceType")
    List<Object[]> getInsuranceClaimsSummaryByFacility(@Param("facilityId") UUID facilityId);

    @Query("SELECT b FROM Billing b WHERE b.insuranceType != 'UNINSURED' AND b.status = 'PENDING' AND b.patientAmount > 0")
    List<Billing> findPendingInsuranceClaims();

    // ===== COUNT & STATUS QUERIES =====

    @Query("SELECT COUNT(b) FROM Billing b WHERE b.facility.id = :facilityId AND b.status = :status")
    long countByFacilityIdAndStatus(@Param("facilityId") UUID facilityId,
                                    @Param("status") BillingStatus status);

    @Query("SELECT COUNT(b) FROM Billing b WHERE b.facility.id = :facilityId")
    long countByFacilityId(@Param("facilityId") UUID facilityId);

    @Query("SELECT COUNT(b) FROM Billing b WHERE b.status = 'PENDING' AND b.dueDate < :now")
    long countOverdueBills(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(b) FROM Billing b WHERE b.facility.id = :facilityId AND b.status = 'PENDING' AND b.dueDate < :now")
    long countOverdueBillsByFacility(@Param("facilityId") UUID facilityId,
                                     @Param("now") LocalDateTime now);

    // ===== NEW METHODS ADDED FOR ADMIN FINANCIAL DASHBOARD =====

    /**
     * Count pending bills that are overdue (due date before current time)
     */
    @Query("SELECT COUNT(b) FROM Billing b WHERE b.facility.id = :facilityId AND b.status = :status AND b.dueDate < :date")
    long countByFacilityIdAndStatusAndDueDateBefore(@Param("facilityId") UUID facilityId,
                                                    @Param("status") BillingStatus status,
                                                    @Param("date") LocalDateTime date);

    /**
     * Get all bills within a date range (for reports)
     */
    @Query("SELECT b FROM Billing b WHERE b.issuedAt BETWEEN :start AND :end")
    List<Billing> findByIssuedAtBetween(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    // ===== PAYMENT METHOD STATISTICS =====

    @Query("SELECT b.paymentMethod, COUNT(b) FROM Billing b WHERE b.facility.id = :facilityId GROUP BY b.paymentMethod")
    List<Object[]> countByPaymentMethod(@Param("facilityId") UUID facilityId);

    @Query("SELECT b.status, COUNT(b) FROM Billing b WHERE b.facility.id = :facilityId GROUP BY b.status")
    List<Object[]> getStatusDistribution(@Param("facilityId") UUID facilityId);

    // ===== PATIENT BILLING =====

    @Query("SELECT SUM(b.patientAmount - b.paidAmount) FROM Billing b WHERE b.patient.id = :patientId AND b.status = 'PENDING'")
    BigDecimal getPatientOutstanding(@Param("patientId") UUID patientId);

    @Query("SELECT SUM(b.paidAmount) FROM Billing b WHERE b.patient.id = :patientId AND b.status = 'PAID'")
    BigDecimal getPatientTotalPaid(@Param("patientId") UUID patientId);

    @Query("SELECT b FROM Billing b WHERE b.patient.id = :patientId AND b.issuedAt > :since ORDER BY b.issuedAt DESC")
    List<Billing> getPatientRecentBills(@Param("patientId") UUID patientId,
                                        @Param("since") LocalDateTime since);

    // ===== COLLECTIONS & AGING =====

    /**
     * Get aging report for a facility using native SQL
     * Returns: [aging_category, count, total_outstanding]
     */
    @Query(value = "SELECT " +
            "CASE " +
            "  WHEN b.due_date > CURRENT_TIMESTAMP THEN 'CURRENT' " +
            "  WHEN b.due_date > CURRENT_TIMESTAMP - INTERVAL '30 days' THEN '30_DAYS' " +
            "  WHEN b.due_date > CURRENT_TIMESTAMP - INTERVAL '60 days' THEN '60_DAYS' " +
            "  WHEN b.due_date > CURRENT_TIMESTAMP - INTERVAL '90 days' THEN '90_DAYS' " +
            "  ELSE 'OVER_90_DAYS' " +
            "END AS aging_category, " +
            "COUNT(b.id), " +
            "COALESCE(SUM(b.patient_amount - b.paid_amount), 0) AS total_outstanding " +
            "FROM billing b " +
            "WHERE b.facility_id = :facilityId " +
            "  AND b.status = 'PENDING' " +
            "GROUP BY aging_category " +
            "ORDER BY aging_category",
            nativeQuery = true)
    List<Object[]> getAgingReport(@Param("facilityId") UUID facilityId);

    @Query("SELECT b FROM Billing b WHERE b.facility.id = :facilityId AND b.status = 'PENDING' AND b.dueDate < :date")
    List<Billing> findBillsDueBefore(@Param("facilityId") UUID facilityId,
                                     @Param("date") LocalDateTime date);

    @Query("SELECT b FROM Billing b WHERE b.facility.id = :facilityId AND b.status = 'PENDING' AND b.dueDate BETWEEN :now AND :soon")
    List<Billing> findBillsDueSoon(@Param("facilityId") UUID facilityId,
                                   @Param("now") LocalDateTime now,
                                   @Param("soon") LocalDateTime soon);
}