package com.mvura.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "insurance_providers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code; // MUTUELLE, RSSB, MMI

    @Column(nullable = false)
    private String name; // "Mutuelle de Santé", "RSSB/RAMA", etc.

    @Column(nullable = true)
    private String description;

    // ===== ✅ ADD THIS FIELD =====
    @Column(name = "patient_co_pay_percentage", nullable = false)
    @Builder.Default
    private BigDecimal patientCoPayPercentage = new BigDecimal("10.00");

    @Builder.Default
    @Column
    private boolean active = true;

    @Column
    private String contactEmail;

    @Column
    private String contactPhone;

    @Column
    private String website;

    @Column(columnDefinition = "TEXT")
    private String coverageDetails; // What is covered

    @Column(columnDefinition = "TEXT")
    private String excludedServices; // What is NOT covered
}