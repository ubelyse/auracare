package com.mvura.service;

import com.mvura.dto.FacilityDTO;
import com.mvura.model.Department;
import com.mvura.model.Facility;
import com.mvura.repository.DepartmentRepository;
import com.mvura.repository.FacilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final DepartmentRepository departmentRepository;

    public List<Facility> getAllFacilities() {
        return facilityRepository.findAll();
    }

    // ===== NEW: Get facilities as DTOs to avoid recursion =====
    public List<FacilityDTO> getAllFacilitiesDTO() {
        List<Facility> facilities = facilityRepository.findAll();
        log.info("📋 Converting {} facilities to DTOs", facilities.size());
        return facilities.stream().map(facility -> {
            FacilityDTO dto = new FacilityDTO();
            dto.setId(facility.getId());
            dto.setName(facility.getName());
            dto.setCode(facility.getCode());
            dto.setAddress(facility.getAddress());
            dto.setPhone(facility.getPhone());
            dto.setEmail(facility.getEmail());
            dto.setActive(facility.isActive());
            return dto;
        }).collect(Collectors.toList());
    }

    public List<Department> getDepartmentsByFacility(UUID facilityId) {
        return departmentRepository.findActiveByFacility(facilityId);
    }
}