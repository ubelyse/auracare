package com.mvura.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class TicketDTO {
    private UUID id;
    private String ticketNumber;
    private String status;
    private String priority;
    private Integer queuePosition;
    private Integer estimatedWaitMinutes;
    private UUID facilityId;
    private String facilityName;
    private UUID departmentId;
    private String departmentName;
    private String departmentCode;

    // ===== PATIENT-FRIENDLY MESSAGES =====
    private String message;
    private Boolean isFirstInLine;
    private Boolean isNearFront;
    private Boolean hasLongWait;

    // ===== ADD THESE FIELDS FOR DOCTOR VIEW =====
    private String symptoms;           // Original symptoms from patient
    private String sanitizedSymptoms;  // PHI-scrubbed version
    private Integer age;               // Patient age
    private Integer triageScore;       // Triage score (0-100)
    private String triageMethod;       // AI, RULE_BASED, or DEFAULT
    private Double aiConfidence;       // AI confidence score (0.0-1.0)
    private String patientName;        // Patient full name
    private String doctorName;         // Assigned doctor name (if any)
    private Boolean isBooked;          // Whether this is a booked appointment
    // ===================================
}