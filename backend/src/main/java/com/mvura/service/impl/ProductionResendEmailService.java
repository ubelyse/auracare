package com.mvura.service.impl;

import com.mvura.model.Appointment;
import com.mvura.model.User;
import com.mvura.service.EmailServiceStrategy;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Profile("prod")
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionResendEmailService implements EmailServiceStrategy {

    @Value("${resend.api.key}")
    private String resendApiKey;

    @Value("${app.email.from:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private Resend resend;

    @PostConstruct
    public void init() {
        this.resend = new Resend(resendApiKey);
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(to)
                    .subject(subject)
                    .html(htmlBody)
                    .build();
            resend.emails().send(params);
            log.info("Production Resend HTTP email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send Resend HTTP email to {}: {}", to, e.getMessage(), e);
        }
    }

    @Override
    @Async
    public void sendVerificationEmail(User user, String token) {
        String verificationLink = frontendUrl + "/verify-email?token=" + token;
        String html = "<p>Please verify your email by clicking <a href=\"" + verificationLink + "\">here</a>.</p>";
        sendHtmlEmail(user.getEmail(), "Verify Your Email", html);
    }

    // Implement other interface methods similarly calling sendHtmlEmail(...)
    @Override public void sendPasswordResetEmail(User user, String token) {}
    @Override public void sendWelcomeEmail(User user) {}
    @Override public void sendAppointmentReminder(Appointment appt) {}
    @Override public void sendCheckInReminder(Appointment appt) {}
    @Override public void sendFollowUpReminder(User patient, String doctor, LocalDateTime date) {}
    @Override public void sendSuspiciousLoginAlert(User user, String device, String ip, String loc) {}
    @Override public void sendBulkEmail(List<String> recipients, String subject, String body) {}
}