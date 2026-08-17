package com.mvura.event;

import com.mvura.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener // Fires ONLY after DB transaction commits successfully
    public void handleUserRegistered(UserRegisteredEvent event) {
        try {
            emailService.sendVerificationEmail(event.getUser(), event.getVerificationToken());
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", event.getUser().getEmail(), e.getMessage());
        }
    }
}