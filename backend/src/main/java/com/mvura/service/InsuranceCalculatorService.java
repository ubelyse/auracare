package com.mvura.service;

import com.mvura.model.InsuranceType;
import com.mvura.model.ServicePricing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Slf4j
public class InsuranceCalculatorService {

    /**
     * Calculate patient's payment based on insurance type
     */
    public BigDecimal calculatePatientPayment(ServicePricing pricing, InsuranceType insuranceType) {
        switch (insuranceType) {
            case MUTUELLE:
                return pricing.getBasePrice().multiply(pricing.getMutuelleCoPayPercent())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            case RSSB:
                return pricing.getBasePrice().multiply(pricing.getRssbCoPayPercent())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            case MMI:
                return pricing.getBasePrice().multiply(pricing.getMmiCoPayPercent())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            case UNINSURED:
            default:
                return pricing.getBasePrice(); // 100%
        }
    }

    /**
     * Calculate insurance payment
     */
    public BigDecimal calculateInsurancePayment(ServicePricing pricing, InsuranceType insuranceType) {
        BigDecimal patientPayment = calculatePatientPayment(pricing, insuranceType);
        return pricing.getBasePrice().subtract(patientPayment);
    }

    /**
     * Get effective co-pay percentage for display
     */
    public BigDecimal getEffectiveCoPayPercent(ServicePricing pricing, InsuranceType insuranceType) {
        switch (insuranceType) {
            case MUTUELLE:
                return pricing.getMutuelleCoPayPercent();
            case RSSB:
                return pricing.getRssbCoPayPercent();
            case MMI:
                return pricing.getMmiCoPayPercent();
            case UNINSURED:
            default:
                return BigDecimal.valueOf(100);
        }
    }

    /**
     * Get coverage description
     */
    public String getCoverageDescription(ServicePricing pricing, InsuranceType insuranceType) {
        switch (insuranceType) {
            case MUTUELLE:
                return "Patient pays " + pricing.getMutuelleCoPayPercent() + "%, Mutuelle covers "
                        + BigDecimal.valueOf(100).subtract(pricing.getMutuelleCoPayPercent()) + "%";
            case RSSB:
                return "Patient pays " + pricing.getRssbCoPayPercent() + "%, RSSB covers "
                        + BigDecimal.valueOf(100).subtract(pricing.getRssbCoPayPercent()) + "%";
            case MMI:
                return "Patient pays " + pricing.getMmiCoPayPercent() + "%, MMI covers "
                        + BigDecimal.valueOf(100).subtract(pricing.getMmiCoPayPercent()) + "%";
            case UNINSURED:
            default:
                return "Patient pays 100%, no insurance coverage";
        }
    }
}