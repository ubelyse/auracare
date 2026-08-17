package com.mvura.service;

import com.mvura.model.Appointment;
import com.mvura.model.AppointmentStatus;
import com.mvura.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentCleanupService {

    private final AppointmentRepository appointmentRepository;

    /**
     * Run on startup to clean up any expired appointments from before the app started
     */
    @PostConstruct
    public void cleanUpOnStartup() {
        log.info("🔄 Running initial appointment cleanup on startup...");
        markNoShowPastAppointments();
    }

    /**
     * Run every 5 minutes - AUTOMATICALLY checks for expired appointments
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    @Transactional
    public void markNoShowPastAppointments() {
        try {
            LocalDateTime now = LocalDateTime.now();

            // Find expired appointments
            List<Appointment> expiredAppointments = appointmentRepository.findExpiredAppointments(now);

            if (expiredAppointments.isEmpty()) {
                return; // Nothing to do
            }

            int count = 0;
            for (Appointment appointment : expiredAppointments) {
                // Mark as NO_SHOW automatically
                appointment.setStatus(AppointmentStatus.NO_SHOW);
                appointmentRepository.save(appointment);
                count++;

                log.info("✅ AUTO: Marked appointment {} as NO_SHOW (window closed at {})",
                        appointment.getId(), appointment.getCheckInCloses());
            }

            log.info("✅ AUTO: Marked {} expired appointments as NO_SHOW", count);

        } catch (Exception e) {
            log.error("❌ Error in auto-cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual trigger for testing or admin use
     */
    @Transactional
    public int manualCleanup() {
        LocalDateTime now = LocalDateTime.now();
        List<Appointment> expiredAppointments = appointmentRepository.findExpiredAppointments(now);

        for (Appointment appointment : expiredAppointments) {
            appointment.setStatus(AppointmentStatus.NO_SHOW);
            appointmentRepository.save(appointment);
            log.info("✅ Manual: Marked appointment {} as NO_SHOW", appointment.getId());
        }

        log.info("✅ Manual: Marked {} expired appointments as NO_SHOW", expiredAppointments.size());
        return expiredAppointments.size();
    }
}