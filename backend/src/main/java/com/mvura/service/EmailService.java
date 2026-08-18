package com.mvura.service;

import com.mvura.model.Appointment;
import com.mvura.model.User;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${resend.api.key:}")
    private String resendApiKey;

    @Value("${app.email.from:noreply@mvura.com}")
    private String fromEmail;

    @Value("${app.api-url:http://localhost:8080}")
    private String apiUrl;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private Resend resend;

    @PostConstruct
    public void init() {
        if (resendApiKey != null && !resendApiKey.trim().isEmpty()) {
            this.resend = new Resend(resendApiKey.trim());
            log.info("Resend HTTP API initialized for email service.");
        } else {
            log.warn("Resend API key not found. Falling back to JavaMailSender (SMTP).");
        }
    }

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");

    // ==================== ORIGINAL EXISTING METHODS ====================

    @Async
    public void sendVerificationEmail(User user, String token) {
        try {
            String verificationLink = frontendUrl + "/verify-email?token=" + token;
            String emailBody = buildVerificationEmailBody(user, verificationLink);

            sendHtmlEmail(user.getEmail(), "MVURA - Verify Your Email Address", emailBody);
            log.info("Verification email successfully sent to: {}", user.getEmail());

        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error occurred while sending verification email to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    @Async
    public void sendPasswordResetEmail(User user, String token) {
        try {
            String resetLink = frontendUrl + "/reset-password?token=" + token;
            String emailBody = buildPasswordResetEmailBody(user, resetLink);

            sendHtmlEmail(user.getEmail(), "AuraCare - Password Reset Request", emailBody);
            log.info("Password reset email sent to: {}", user.getEmail());

        } catch (MessagingException e) {
            log.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error occurred while sending password reset email to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    @Async
    public void sendWelcomeEmail(User user) {
        try {
            String loginLink = frontendUrl + "/login";
            String emailBody = buildWelcomeEmailBody(user, loginLink);

            sendHtmlEmail(user.getEmail(), "Welcome to AuraCare Health Platform!", emailBody);
            log.info("Welcome email sent to: {}", user.getEmail());

        } catch (MessagingException e) {
            log.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error occurred while sending welcome email to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    // ==================== 1. APPOINTMENT REMINDER ====================

    @Async
    public void sendAppointmentReminder(Appointment appointment) {
        try {
            String emailBody = buildAppointmentReminderBody(appointment);

            sendHtmlEmail(
                    appointment.getPatient().getEmail(),
                    "Auracare - Appointment Reminder",
                    emailBody
            );
            log.info("Appointment reminder sent to: {}", appointment.getPatient().getEmail());

        } catch (MessagingException e) {
            log.error("Failed to send appointment reminder to {}: {}",
                    appointment.getPatient().getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending appointment reminder to {}: {}",
                    appointment.getPatient().getEmail(), e.getMessage(), e);
        }
    }

    @Async
    public void sendAppointmentReminders(List<Appointment> appointments) {
        for (Appointment appointment : appointments) {
            sendAppointmentReminder(appointment);
        }
        log.info("Sent {} appointment reminders", appointments.size());
    }

    // ==================== 2. CHECK-IN REMINDER ====================

    @Async
    public void sendCheckInReminder(Appointment appointment) {
        try {
            String emailBody = buildCheckInReminderBody(appointment);

            sendHtmlEmail(
                    appointment.getPatient().getEmail(),
                    "Auracare - Time to Check In!",
                    emailBody
            );
            log.info("Check-in reminder sent to: {}", appointment.getPatient().getEmail());

        } catch (MessagingException e) {
            log.error("Failed to send check-in reminder to {}: {}",
                    appointment.getPatient().getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending check-in reminder to {}: {}",
                    appointment.getPatient().getEmail(), e.getMessage(), e);
        }
    }

    @Async
    public void sendCheckInReminders(List<Appointment> appointments) {
        for (Appointment appointment : appointments) {
            sendCheckInReminder(appointment);
        }
        log.info("Sent {} check-in reminders", appointments.size());
    }

    // ==================== 3. FOLLOW-UP REMINDER ====================

    @Async
    public void sendFollowUpReminder(User patient, String doctorName, LocalDateTime followUpDate) {
        try {
            String emailBody = buildFollowUpReminderBody(patient, doctorName, followUpDate);

            sendHtmlEmail(
                    patient.getEmail(),
                    "Auracare - Follow-up Appointment Reminder",
                    emailBody
            );
            log.info("Follow-up reminder sent to: {}", patient.getEmail());

        } catch (MessagingException e) {
            log.error("Failed to send follow-up reminder to {}: {}", patient.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending follow-up reminder to {}: {}", patient.getEmail(), e.getMessage(), e);
        }
    }

    @Async
    public void sendFollowUpReminders(List<FollowUpInfo> followUps) {
        for (FollowUpInfo info : followUps) {
            sendFollowUpReminder(info.getPatient(), info.getDoctorName(), info.getFollowUpDate());
        }
        log.info("Sent {} follow-up reminders", followUps.size());
    }

    // ==================== 4. SUSPICIOUS LOGIN ALERT ====================

    @Async
    public void sendSuspiciousLoginAlert(User user, String deviceName, String ipAddress, String location) {
        try {
            String emailBody = buildSuspiciousLoginAlertBody(user, deviceName, ipAddress, location);

            sendHtmlEmail(
                    user.getEmail(),
                    "Auracare - Security Alert: New Login Detected",
                    emailBody
            );
            log.info("Suspicious login alert sent to: {}", user.getEmail());

        } catch (MessagingException e) {
            log.error("Failed to send suspicious login alert to {}: {}", user.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending suspicious login alert to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    // ==================== 5. BULK EMAIL SUPPORT ====================

    @Async
    public void sendBulkEmail(List<String> recipients, String subject, String body) {
        if (recipients == null || recipients.isEmpty()) {
            log.warn("No recipients provided for bulk email");
            return;
        }

        try {
            if (resend != null) {
                for (String recipient : recipients) {
                    CreateEmailOptions params = CreateEmailOptions.builder()
                            .from(fromEmail)
                            .to(recipient)
                            .subject(subject)
                            .html(body)
                            .build();
                    resend.emails().send(params);
                }
                log.info("Bulk email sent via Resend API to {} recipients", recipients.size());
            } else {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromEmail);
                helper.setTo(recipients.get(0));

                if (recipients.size() > 1) {
                    helper.setBcc(recipients.stream().skip(1).toArray(String[]::new));
                }

                helper.setSubject(subject);
                helper.setText(body, true);

                mailSender.send(message);
                log.info("Bulk email sent via SMTP to {} recipients", recipients.size());
            }

        } catch (Exception e) {
            log.error("Failed to send bulk email: {}", e.getMessage(), e);
        }
    }

    @Async
    public void sendBulkEmailWithRecipients(List<User> recipients, String subject, String body) {
        List<String> emails = recipients.stream()
                .map(User::getEmail)
                .filter(email -> email != null && !email.isEmpty())
                .toList();

        sendBulkEmail(emails, subject, body);
    }

    // ==================== PUBLIC HELPER METHODS ====================

    public void sendHtmlEmail(String to, String subject, String body) throws MessagingException {
        if (resend != null) {
            try {
                CreateEmailOptions params = CreateEmailOptions.builder()
                        .from(fromEmail)
                        .to(to)
                        .subject(subject)
                        .html(body)
                        .build();
                resend.emails().send(params);
                log.info("Email successfully sent via Resend API to: {}", to);
                return;
            } catch (Exception e) {
                log.error("Resend API failed, falling back to SMTP for {}: {}", to, e.getMessage());
            }
        }

        // Fallback or local dev SMTP implementation
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, true);
        mailSender.send(message);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private String getFullName(User user) {
        if (user == null) return "User";
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? "User" : fullName;
    }

    private String formatDate(LocalDateTime date) {
        return date != null ? date.format(DATE_FORMATTER) : "Date not set";
    }

    // ==================== EMAIL BUILDERS ====================

    private String buildVerificationEmailBody(User user, String verificationLink) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #22c55e; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }
                    .content { background: #f9fafb; padding: 30px; border-radius: 0 0 5px 5px; }
                    .button { display: inline-block; padding: 12px 24px; background: #22c55e; color: white; text-decoration: none; border-radius: 5px; font-weight: bold; }
                    .footer { margin-top: 20px; font-size: 12px; color: #6b7280; text-align: center; }
                    .code { background: #e5e7eb; padding: 10px; border-radius: 5px; font-family: monospace; word-break: break-all; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Welcome to Auracare!</h1>
                </div>
                <div class="content">
                    <h2>Hello %s,</h2>
                    <p>Thank you for registering with Aura care Health Platform.</p>
                    <p>Please verify your email address by clicking the button below:</p>
                    <p style="text-align: center;">
                        <a href="%s" class="button">Verify Email</a>
                    </p>
                    <p>Or copy and paste this link into your browser:</p>
                    <p class="code">%s</p>
                    <p><strong>This link will expire in 24 hours.</strong></p>
                    <p>If you did not create an account, please ignore this email.</p>
                    <br>
                    <p>Best regards,</p>
                    <p><strong>Aura Health Team</strong></p>
                </div>
                <div class="footer">
                    <p>&copy; 2026 Aura Health Platform. All rights reserved.</p>
                    <p>This is an automated message, please do not reply.</p>
                </div>
            </body>
            </html>
            """,
                getFullName(user),
                verificationLink,
                verificationLink
        );
    }

    private String buildPasswordResetEmailBody(User user, String resetLink) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #ef4444; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }
                    .content { background: #f9fafb; padding: 30px; border-radius: 0 0 5px 5px; }
                    .button { display: inline-block; padding: 12px 24px; background: #ef4444; color: white; text-decoration: none; border-radius: 5px; font-weight: bold; }
                    .footer { margin-top: 20px; font-size: 12px; color: #6b7280; text-align: center; }
                    .code { background: #e5e7eb; padding: 10px; border-radius: 5px; font-family: monospace; word-break: break-all; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Password Reset Request</h1>
                </div>
                <div class="content">
                    <h2>Hello %s,</h2>
                    <p>We received a request to reset your password for your Auracare account.</p>
                    <p>Click the button below to reset your password:</p>
                    <p style="text-align: center;">
                        <a href="%s" class="button">Reset Password</a>
                    </p>
                    <p>Or copy and paste this link into your browser:</p>
                    <p class="code">%s</p>
                    <p><strong>This link will expire in 1 hour.</strong></p>
                    <p>If you did not request a password reset, please ignore this email.</p>
                    <br>
                    <p>Best regards,</p>
                    <p><strong>Aura Health Team</strong></p>
                </div>
                <div class="footer">
                    <p>&copy; 2026 Aura Health Platform. All rights reserved.</p>
                    <p>This is an automated message, please do not reply.</p>
                </div>
            </body>
            </html>
            """,
                getFullName(user),
                resetLink,
                resetLink
        );
    }

    private String buildWelcomeEmailBody(User user, String loginLink) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #22c55e; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }
                    .content { background: #f9fafb; padding: 30px; border-radius: 0 0 5px 5px; }
                    .footer { margin-top: 20px; font-size: 12px; color: #6b7280; text-align: center; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Welcome to Auracare!</h1>
                </div>
                <div class="content">
                    <h2>Hello %s,</h2>
                    <p>Your email has been verified and your account is now active!</p>
                    <p>You can now:</p>
                    <ul>
                        <li>Check in for medical services</li>
                        <li>View your queue position in real-time</li>
                        <li>Access your medical history</li>
                        <li>View and pay bills</li>
                    </ul>
                    <p>To get started, log in to your account:</p>
                    <p style="text-align: center;">
                        <a href="%s" style="display: inline-block; padding: 12px 24px; background: #22c55e; color: white; text-decoration: none; border-radius: 5px; font-weight: bold;">
                            Go to Aura
                        </a>
                    </p>
                    <br>
                    <p>Best regards,</p>
                    <p><strong>Aura Health Team</strong></p>
                </div>
                <div class="footer">
                    <p>&copy; 2026 Aura Health Platform. All rights reserved.</p>
                </div>
            </body>
            </html>
            """,
                getFullName(user),
                loginLink
        );
    }

    private String buildAppointmentReminderBody(Appointment appointment) {
        User patient = appointment.getPatient();
        String doctorName = appointment.getDoctor() != null ?
                "Dr. " + getFullName(appointment.getDoctor()) :
                "Available Doctor";

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #3b82f6; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }
                    .content { background: #f9fafb; padding: 30px; border-radius: 0 0 5px 5px; }
                    .details { background: #e5e7eb; padding: 15px; border-radius: 5px; margin: 15px 0; }
                    .footer { margin-top: 20px; font-size: 12px; color: #6b7280; text-align: center; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Appointment Reminder</h1>
                </div>
                <div class="content">
                    <h2>Hello %s,</h2>
                    <p>This is a reminder for your upcoming appointment:</p>
                    <div class="details">
                        <p><strong>Date:</strong> %s</p>
                        <p><strong>Facility:</strong> %s</p>
                        <p><strong>Department:</strong> %s</p>
                        <p><strong>Doctor:</strong> %s</p>
                        <p><strong>Check-in Window:</strong> %s - %s</p>
                    </div>
                    <p>Please arrive on time for your appointment.</p>
                    <br>
                    <p>Best regards,</p>
                    <p><strong>Aura Health Team</strong></p>
                </div>
                <div class="footer">
                    <p>&copy; 2026 Aura Health Platform. All rights reserved.</p>
                </div>
            </body>
            </html>
            """,
                getFullName(patient),
                formatDate(appointment.getAppointmentDateTime()),
                appointment.getFacility().getName(),
                appointment.getDepartment().getName(),
                doctorName,
                formatDate(appointment.getCheckInOpens()),
                formatDate(appointment.getCheckInCloses())
        );
    }

    private String buildCheckInReminderBody(Appointment appointment) {
        User patient = appointment.getPatient();

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #f59e0b; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }
                    .content { background: #f9fafb; padding: 30px; border-radius: 0 0 5px 5px; }
                    .button { display: inline-block; padding: 12px 24px; background: #f59e0b; color: white; text-decoration: none; border-radius: 5px; font-weight: bold; }
                    .footer { margin-top: 20px; font-size: 12px; color: #6b7280; text-align: center; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Time to Check In!</h1>
                </div>
                <div class="content">
                    <h2>Hello %s,</h2>
                    <p>Your appointment is in 30 minutes. Please check in now.</p>
                    <p style="text-align: center;">
                        <a href="%s/check-in/%s" class="button">Check In Now</a>
                    </p>
                    <p><strong>Facility:</strong> %s</p>
                    <p><strong>Department:</strong> %s</p>
                    <p><strong>Doctor:</strong> %s</p>
                    <br>
                    <p>Best regards,</p>
                    <p><strong>Aura Health Team</strong></p>
                </div>
                <div class="footer">
                    <p>&copy; 2026 Aura Health Platform. All rights reserved.</p>
                </div>
            </body>
            </html>
            """,
                getFullName(patient),
                frontendUrl,
                appointment.getId(),
                appointment.getFacility().getName(),
                appointment.getDepartment().getName(),
                appointment.getDoctor() != null ? "Dr. " + getFullName(appointment.getDoctor()) : "Available Doctor"
        );
    }

    private String buildFollowUpReminderBody(User patient, String doctorName, LocalDateTime followUpDate) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #8b5cf6; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }
                    .content { background: #f9fafb; padding: 30px; border-radius: 0 0 5px 5px; }
                    .footer { margin-top: 20px; font-size: 12px; color: #6b7280; text-align: center; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Follow-up Reminder</h1>
                </div>
                <div class="content">
                    <h2>Hello %s,</h2>
                    <p>This is a reminder for your scheduled follow-up appointment:</p>
                    <p><strong>Date:</strong> %s</p>
                    <p><strong>Doctor:</strong> %s</p>
                    <p>Please remember to bring any relevant medical records or test results.</p>
                    <br>
                    <p>Best regards,</p>
                    <p><strong>Aura Health Team</strong></p>
                </div>
                <div class="footer">
                    <p>&copy; 2026 Aura Health Platform. All rights reserved.</p>
                </div>
            </body>
            </html>
            """,
                getFullName(patient),
                formatDate(followUpDate),
                doctorName
        );
    }

    private String buildSuspiciousLoginAlertBody(User user, String deviceName, String ipAddress, String location) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #ef4444; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }
                    .content { background: #f9fafb; padding: 30px; border-radius: 0 0 5px 5px; }
                    .details { background: #e5e7eb; padding: 15px; border-radius: 5px; margin: 15px 0; }
                    .button { display: inline-block; padding: 12px 24px; background: #ef4444; color: white; text-decoration: none; border-radius: 5px; font-weight: bold; }
                    .footer { margin-top: 20px; font-size: 12px; color: #6b7280; text-align: center; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Security Alert</h1>
                </div>
                <div class="content">
                    <h2>Hello %s,</h2>
                    <p>We detected a new login to your Aura account from an unrecognized device.</p>
                    <div class="details">
                        <p><strong>Device:</strong> %s</p>
                        <p><strong>IP Address:</strong> %s</p>
                        <p><strong>Location:</strong> %s</p>
                        <p><strong>Time:</strong> %s</p>
                    </div>
                    <p>If this was you, you can ignore this message.</p>
                    <p>If this was not you, please secure your account immediately:</p>
                    <p style="text-align: center;">
                        <a href="%s/security" class="button">Secure Your Account</a>
                    </p>
                    <br>
                    <p>Best regards,</p>
                    <p><strong>Aura Security Team</strong></p>
                </div>
                <div class="footer">
                    <p>&copy; 2026 Aura Health Platform. All rights reserved.</p>
                </div>
            </body>
            </html>
            """,
                getFullName(user),
                deviceName != null ? deviceName : "Unknown Device",
                ipAddress != null ? ipAddress : "Unknown IP",
                location != null ? location : "Unknown Location",
                formatDate(LocalDateTime.now()),
                frontendUrl
        );
    }

    // ==================== INNER CLASS ====================

    @lombok.Data
    @lombok.Builder
    public static class FollowUpInfo {
        private User patient;
        private String doctorName;
        private LocalDateTime followUpDate;
    }
}