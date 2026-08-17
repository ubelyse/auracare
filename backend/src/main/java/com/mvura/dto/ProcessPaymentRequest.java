package com.mvura.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ProcessPaymentRequest {
    @NotNull(message = "billingId is required")
    private UUID billingId;

    @NotBlank(message = "paymentMethod is required")
    private String paymentMethod;

    @NotBlank(message = "transactionId is required")
    private String transactionId;
}
