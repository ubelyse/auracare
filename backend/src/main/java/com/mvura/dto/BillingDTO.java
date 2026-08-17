package com.mvura.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class BillingDTO {
    private UUID id;
    private String invoiceNumber;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal patientAmount;
    private BigDecimal insuranceAmount;
    private UUID patientId;
    private String patientName;
    private UUID facilityId;
    private String facilityName;
    private UUID ticketId;
    private String paymentMethod;
    private String transactionId;

    // ===== TIMESTAMPS =====
    private LocalDateTime createdAt;
    private LocalDateTime issuedAt;  // ← ADD THIS
    private LocalDateTime paidAt;

    // ===== INSURANCE =====
    private String insuranceType;    // ← ADD THIS
}