package com.mvura.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class DepartmentWithDoctorsDTO {
    private UUID id;
    private String name;
    private String code;
    private String description;
    private boolean active;
    private List<DoctorDTO> availableDoctors;
}