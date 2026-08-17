package com.mvura.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class FacilityTransferDTO {
    private UUID id;
    private String status;
    private String reason;
    private String transferType;
    private UUID ticketId;
    private String ticketNumber;
    private UUID fromFacilityId;
    private String fromFacilityName;
    private UUID toFacilityId;
    private String toFacilityName;
    private UUID requestedBy;
    private String requestedByName;
    private UUID approvedBy;
    private String approvedByName;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
}