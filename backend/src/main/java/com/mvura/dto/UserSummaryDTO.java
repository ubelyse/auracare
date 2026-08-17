package com.mvura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDTO {
    private UUID id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String role;
    private boolean isActive;
    private boolean emailVerified;
    private String facilityName;
    private UUID facilityId;
    private UUID departmentId;
    private String departmentName;
    private LocalDateTime createdAt;
}