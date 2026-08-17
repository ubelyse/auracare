package com.mvura.repository;

import com.mvura.model.InsuranceProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InsuranceProviderRepository extends JpaRepository<InsuranceProvider, UUID> {

    Optional<InsuranceProvider> findByCode(String code);

    Optional<InsuranceProvider> findByName(String name);
}