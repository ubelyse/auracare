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
@Table(name = "medical_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;  // ✅ No @JsonIgnore here

    @Column(name = "record_type")
    private String recordType;

    @Column(name = "record_date")
    private LocalDateTime recordDate;

    @Column(name = "summary")
    @Convert(converter = EncryptedStringConverter.class)
    private String summary;

    @Column(name = "details")
    @Convert(converter = EncryptedStringConverter.class)
    private String details;

    @Column(name = "metadata")
    private String metadata;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        recordDate = LocalDateTime.now();
    }
}