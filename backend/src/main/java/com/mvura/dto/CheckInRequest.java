package com.mvura.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CheckInRequest {
    private UUID patientId;
    private UUID facilityId;
    private UUID departmentId;
    @NotNull(message = "Doctor is required")
    private UUID doctorId;
    private String symptoms;
    private String insuranceType;
    private Boolean isPregnant;
    private String healthChanges;
    private Boolean hasRecentSurgery;
    private String recentSurgeryDetails;
    private Boolean hasNewAllergies;
    private String newAllergiesDetails;
}