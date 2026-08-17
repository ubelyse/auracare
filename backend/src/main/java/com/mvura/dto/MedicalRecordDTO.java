package com.mvura.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MedicalRecordDTO {
    private UUID id;
    private String recordType;
    private String summary;
    private String details;
    private String metadata;
    private String doctorName;
    private UUID doctorId;
    private LocalDateTime recordDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}