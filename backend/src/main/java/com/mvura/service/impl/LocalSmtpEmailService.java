package com.mvura.service.impl;

import com.mvura.model.Appointment;
import com.mvura.model.User;
import com.mvura.service.EmailServiceStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Profile("local")
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalSmtpEmailService implements EmailServiceStrategy {

    private final JavaMailSender mailSender;

    @Value("${app.email.from:noreply@mvura.com}")
    private String fromEmail;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");

    @Async
    public void sendHtmlEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("Local SMTP email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send local SMTP email to {}: {}", to, e.getMessage(), e);
        }
    }

    // Keep all your original builder methods and email triggers here (sendVerificationEmail, sendPasswordResetEmail, etc.)
    // They will call sendHtmlEmail(...) locally over port 587 just like before.
    @Override public void sendVerificationEmail(User user, String token) { /* ... */ }
    @Override public void sendPasswordResetEmail(User user, String token) { /* ... */ }
    @Override public void sendWelcomeEmail(User user) { /* ... */ }
    @Override public void sendAppointmentReminder(Appointment appt) { /* ... */ }
    @Override public void sendCheckInReminder(Appointment appt) { /* ... */ }
    @Override public void sendFollowUpReminder(User patient, String doctor, LocalDateTime date) { /* ... */ }
    @Override public void sendSuspiciousLoginAlert(User user, String device, String ip, String loc) { /* ... */ }
    @Override public void sendBulkEmail(List<String> recipients, String subject, String body) { /* ... */ }
}