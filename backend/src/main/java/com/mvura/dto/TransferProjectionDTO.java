package com.mvura.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferProjectionDTO {
    private int projectedPosition;
    private int projectedWaitMinutes;
    private String projectedDoctorName;
    private boolean isAvailable;
    private String message;
}