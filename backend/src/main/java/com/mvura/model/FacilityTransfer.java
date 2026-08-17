package com.mvura.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "facility_transfers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilityTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;  // ✅ No @JsonIgnore here

    @ManyToOne
    @JoinColumn(name = "from_facility_id", nullable = false)
    private Facility fromFacility;  // ✅ No @JsonIgnore here

    @ManyToOne
    @JoinColumn(name = "to_facility_id", nullable = false)
    private Facility toFacility;  // ✅ No @JsonIgnore here

    @ManyToOne
    @JoinColumn(name = "from_department_id", nullable = false)
    private Department fromDepartment;  // ✅ No @JsonIgnore here

    @ManyToOne
    @JoinColumn(name = "to_department_id", nullable = false)
    private Department toDepartment;  // ✅ No @JsonIgnore here

    @Column(name = "transfer_reason")
    private String transferReason;

    @Column(name = "transfer_type")
    @Enumerated(EnumType.STRING)
    private TransferType transferType;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private TransferStatus status;

    @Column(name = "initiated_by")
    private UUID initiatedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        status = TransferStatus.PENDING;
    }
}