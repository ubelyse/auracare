package com.mvura.model;

import com.mvura.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consultations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private User doctor;

    @Column(name = "diagnosis", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String diagnosis;

    @Column(name = "notes", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String notes;

    @Column(name = "prescription", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String prescription;

    @Column(name = "lab_orders", columnDefinition = "TEXT")  // ← CHANGE TO TEXT
    @Convert(converter = EncryptedStringConverter.class)
    private String labOrders;

    @Column(name = "lab_results", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String labResults;

    @Column(name = "symptoms", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String symptoms;

    @Column(name = "diagnosis_code")
    private String diagnosisCode;

    @Column(name = "follow_up_date")
    private LocalDateTime followUpDate;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}