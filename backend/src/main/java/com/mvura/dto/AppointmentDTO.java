package com.mvura.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AppointmentDTO {
    private UUID id;
    private UUID patientId;
    private String patientName;
    private UUID facilityId;
    private String facilityName;
    private UUID departmentId;
    private String departmentName;
    private UUID doctorId;
    private String doctorName;
    private LocalDateTime appointmentDateTime;
    private LocalDateTime checkInOpens;
    private LocalDateTime checkInCloses;
    private String status;
}