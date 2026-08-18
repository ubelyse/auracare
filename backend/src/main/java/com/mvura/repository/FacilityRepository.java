package com.mvura.repository;

import com.mvura.model.Facility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityRepository extends JpaRepository<Facility, UUID> {

    // ✅ Already exists - these are covered by JpaRepository
    Optional<Facility> findById(UUID id);  // Already provided by JpaRepository
    List<Facility> findAll();               // Already provided by JpaRepository

    // ✅ Custom queries - these are good
    Optional<Facility> findByCode(String code);
    Optional<Facility> findByName(String name);
    boolean existsByCode(String code);
    boolean existsByName(String name);
}