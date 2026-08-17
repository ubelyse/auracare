package com.mvura.repository;

import com.mvura.model.ServicePricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServicePricingRepository extends JpaRepository<ServicePricing, UUID> {

    Optional<ServicePricing> findByServiceCode(String serviceCode);

    List<ServicePricing> findByCategory(String category);

    // FIXED: Changed @Param("facilityId") type from String to UUID
    @Query("SELECT s FROM ServicePricing s WHERE s.facilityId IS NULL OR s.facilityId = :facilityId")
    List<ServicePricing> findGlobalAndFacilityPricing(@Param("facilityId") UUID facilityId);

    // FIXED: Changed @Param("facilityId") type from String to UUID
    @Query("SELECT s FROM ServicePricing s WHERE s.category = :category AND (s.facilityId IS NULL OR s.facilityId = :facilityId)")
    List<ServicePricing> findByCategoryAndFacility(@Param("category") String category,
                                                   @Param("facilityId") UUID facilityId);
}