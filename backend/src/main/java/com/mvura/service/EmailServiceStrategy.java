package com.mvura.service;

import com.mvura.model.Appointment;
import com.mvura.model.User;
import java.time.LocalDateTime;
import java.util.List;

public interface EmailServiceStrategy {
    void sendVerificationEmail(User user, String token);
    void sendPasswordResetEmail(User user, String token);
    void sendWelcomeEmail(User user);
    void sendAppointmentReminder(Appointment appointment);
    void sendCheckInReminder(Appointment appointment);
    void sendFollowUpReminder(User patient, String doctorName, LocalDateTime followUpDate);
    void sendSuspiciousLoginAlert(User user, String deviceName, String ipAddress, String location);
    void sendBulkEmail(List<String> recipients, String subject, String body);
}