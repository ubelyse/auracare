package com.mvura.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class VoidBillRequest {
    @NotNull(message = "billingId is required")
    private UUID billingId;

    @NotBlank(message = "reason is required")
    private String reason;
}
