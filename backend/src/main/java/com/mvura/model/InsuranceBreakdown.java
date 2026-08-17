package com.mvura.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class InsuranceBreakdown {
    private BigDecimal basePrice;

    // Mutuelle
    private BigDecimal mutuellePatientPays;
    private BigDecimal mutuelleInsurancePays;

    // RSSB
    private BigDecimal rssbPatientPays;
    private BigDecimal rssbInsurancePays;

    // MMI
    private BigDecimal mmiPatientPays;
    private BigDecimal mmiInsurancePays;

    // Private
    private BigDecimal privatePatientPays;
    private BigDecimal privateInsurancePays;

    // Uninsured
    private BigDecimal uninsuredPatientPays;
    private BigDecimal uninsuredInsurancePays;
}