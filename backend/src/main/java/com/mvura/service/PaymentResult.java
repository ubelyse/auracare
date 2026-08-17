package com.mvura.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentResult {
    private boolean success;
    private String transactionId;
    private String message;
    private String paymentMethod;
    private String status; // SUCCESS, FAILED, PENDING
}