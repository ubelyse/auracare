package com.mvura.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class DepartmentDTO {
    private UUID id;
    private String name;
    private String code;
    private String description;
    private boolean active;
    private UUID facilityId;
    private String facilityName;
}