package com.mvura.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Billing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @ManyToOne
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    // ===== INVOICE =====
    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    // ===== SERVICE INFORMATION =====
    @Column(name = "service_code", nullable = false)
    private String serviceCode;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "service_category")
    private String serviceCategory;

    // ===== FINANCIAL FIELDS =====
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;      // Full price

    @Column(name = "patient_amount", nullable = false)
    private BigDecimal patientAmount;    // What patient owes (co-pay)

    @Column(name = "paid_amount")
    private BigDecimal paidAmount;       // How much patient has actually paid

    // ===== INSURANCE =====
    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_type")
    private InsuranceType insuranceType;

    @Column(name = "insurance_amount")
    private BigDecimal insuranceAmount;  // What insurance pays (total - patient)

    @Column(name = "co_pay_percentage")  // ✅ ADDED
    private BigDecimal coPayPercentage;  // 10%, 15%, 100% (the percentage used)

    // ===== PAYMENT =====
    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "payment_reference")
    private String paymentReference;

    // ===== STATUS =====
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private BillingStatus status;

    // ===== ITEMS =====
    @Column(name = "items", columnDefinition = "TEXT")
    private String items;

    // ===== TIMESTAMPS =====
    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== LIFE CYCLE CALLBACKS =====
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (status == null) {
            status = BillingStatus.PENDING;
        }
        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }
        if (insuranceAmount == null && insuranceType != null && insuranceType != InsuranceType.UNINSURED) {
            insuranceAmount = totalAmount.subtract(patientAmount);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        // Auto-update status when fully paid
        if (paidAmount != null && patientAmount != null) {
            if (paidAmount.compareTo(patientAmount) >= 0) {
                status = BillingStatus.PAID;
                if (paidAt == null) {
                    paidAt = LocalDateTime.now();
                }
            }
        }
    }

    // ===== HELPER METHODS =====
    public BigDecimal getRemainingAmount() {
        if (patientAmount == null || paidAmount == null) {
            return BigDecimal.ZERO;
        }
        return patientAmount.subtract(paidAmount);
    }

    public boolean isFullyPaid() {
        return status == BillingStatus.PAID;
    }

    public boolean hasInsurance() {
        return insuranceType != null && insuranceType != InsuranceType.UNINSURED;
    }

    public boolean isOverdue() {
        return dueDate != null &&
                LocalDateTime.now().isAfter(dueDate) &&
                status != BillingStatus.PAID &&
                status != BillingStatus.CANCELLED;
    }
}