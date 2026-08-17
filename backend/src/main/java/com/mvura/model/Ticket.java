package com.mvura.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String ticketNumber;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @ManyToOne
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @ManyToOne
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Column(columnDefinition = "TEXT")
    private String symptoms;

    @Column(columnDefinition = "TEXT")
    private String sanitizedSymptoms;

    private Integer age;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private Boolean isPregnant;

    private Double temperature;

    private Integer heartRate;

    private Integer bloodPressureSystolic;

    private Integer bloodPressureDiastolic;

    private String insuranceType;

    @Column(name = "triage_score")
    private Integer triageScore;

    @Column(name = "triage_method")
    private String triageMethod;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "estimated_wait_minutes")
    private Integer estimatedWaitMinutes;

    @Column(name = "queue_position")
    private Integer queuePosition;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "triaged_at")
    private LocalDateTime triagedAt;

    @Column(name = "consultation_started_at")
    private LocalDateTime consultationStartedAt;

    @Column(name = "consultation_completed_at")
    private LocalDateTime consultationCompletedAt;

    @ManyToOne
    @JoinColumn(name = "assigned_doctor_id")
    private User assignedDoctor;

    // ===== ALL FIELDS WITH DEFAULT VALUES NEED @Builder.Default =====
    @Builder.Default
    @Column(name = "active")
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "transfer_from_facility_id")
    private UUID transferFromFacilityId;

    @Column(name = "transfer_from_department_id")
    private UUID transferFromDepartmentId;

    @Column(name = "transfer_reason")
    private String transferReason;

    @Column(name = "transferred_at")
    private LocalDateTime transferredAt;

    @Builder.Default
    @Column(name = "is_booked")
    private boolean isBooked = false;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "appointment_time")
    private LocalDateTime appointmentTime;

    @Column(name = "check_in_opens")
    private LocalDateTime checkInOpens;

    @Column(name = "check_in_closes")
    private LocalDateTime checkInCloses;

    @Builder.Default
    @Column(name = "emergency_mode_active")
    private boolean emergencyModeActive = false;

    @Column(name = "emergency_mode_started_at")
    private LocalDateTime emergencyModeStartedAt;

    @Column(name = "emergency_mode_ended_at")
    private LocalDateTime emergencyModeEndedAt;

    @Column(name = "emergency_option")
    private String emergencyOption;

    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt;

    @Column(name = "chronic_conditions")
    private String chronicConditions;

    @Column(name = "health_changes", columnDefinition = "TEXT")
    private String healthChanges;

    @Column(name = "has_recent_surgery")
    private Boolean hasRecentSurgery;

    @Column(name = "recent_surgery_details", columnDefinition = "TEXT")
    private String recentSurgeryDetails;

    @Column(name = "has_allergies")
    private Boolean hasAllergies;

    @Column(name = "allergies_description")
    private String allergiesDescription;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "height")
    private Double height;

    @Column(name = "bmi")
    private Double bmi;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        checkedInAt = LocalDateTime.now();
        status = TicketStatus.CHECKED_IN;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastUpdatedAt = LocalDateTime.now();
    }
}