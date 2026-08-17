package com.mvura.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "service_pricing")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicePricing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String serviceCode;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private BigDecimal basePrice;

    // ===== INSURANCE CO-PAY PERCENTAGES =====
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal mutuelleCoPayPercent = new BigDecimal("10.00");  // Patient pays 10%

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal rssbCoPayPercent = new BigDecimal("15.00");      // Patient pays 15%

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal mmiCoPayPercent = new BigDecimal("15.00");       // Patient pays 15%

    // ❌ REMOVED: privateCoPayPercent

    // ===== CALCULATED PATIENT PAYMENTS =====
    @Column(name = "mutuelle_price", nullable = false)
    @Builder.Default
    private BigDecimal mutuellePrice = BigDecimal.ZERO;

    @Column(name = "rssb_price", nullable = false)
    @Builder.Default
    private BigDecimal rssbPrice = BigDecimal.ZERO;

    @Column(name = "mmi_price", nullable = false)
    @Builder.Default
    private BigDecimal mmiPrice = BigDecimal.ZERO;

    @Column
    private String description;

    @Builder.Default
    @Column
    private boolean active = true;

    @Column(name = "facility_id")
    private UUID facilityId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void calculatePrices() {
        if (basePrice != null) {
            this.mutuellePrice = basePrice.multiply(mutuelleCoPayPercent)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            this.rssbPrice = basePrice.multiply(rssbCoPayPercent)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            this.mmiPrice = basePrice.multiply(mmiCoPayPercent)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
    }
}