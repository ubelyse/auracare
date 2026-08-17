package com.mvura.controller;

import com.mvura.service.PaymentResult;
import com.mvura.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/mobile-money")
    public ResponseEntity<?> processMobileMoney(
            @RequestParam String phoneNumber,
            @RequestParam String invoiceNumber,
            @RequestParam BigDecimal amount,
            @RequestParam String network) {

        // In production, integrate with Mobile Money API (MTN, Airtel, etc.)
        PaymentResult result = paymentService.processPayment(
                invoiceNumber,
                amount,
                "MOBILE_MONEY_" + network
        );

        return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "transactionId", result.getTransactionId()
        ));
    }

    @PostMapping("/card")
    public ResponseEntity<?> processCardPayment(
            @RequestParam String invoiceNumber,
            @RequestParam BigDecimal amount,
            @RequestParam String cardToken) {

        // In production, integrate with card payment gateway
        PaymentResult result = paymentService.processPayment(
                invoiceNumber,
                amount,
                "CARD"
        );

        return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "transactionId", result.getTransactionId()
        ));
    }
}