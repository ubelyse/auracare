package com.mvura.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Slf4j
public class PaymentService {

    public PaymentResult processPayment(String invoiceNumber, BigDecimal amount, String paymentMethod) {
        log.info("Processing payment: {}, Amount: {}, Method: {}", invoiceNumber, amount, paymentMethod);

        // Simulate payment processing
        // In production, integrate with payment gateway (Mobile Money, Bank API, etc.)
        try {
            // Simulate processing delay
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate random success (90% success rate for testing)
        boolean success = Math.random() < 0.9;

        if (success) {
            return PaymentResult.builder()
                    .success(true)
                    .transactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8))
                    .message("Payment processed successfully")
                    .build();
        } else {
            return PaymentResult.builder()
                    .success(false)
                    .message("Payment processing failed. Please try again.")
                    .build();
        }
    }
}

