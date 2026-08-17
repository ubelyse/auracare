package com.mvura.service;

import com.mvura.model.Priority;
import com.mvura.model.Ticket;
import com.mvura.model.TicketStatus;
import com.mvura.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueuePositionService {

    private final TicketRepository ticketRepository;
    private final SseService sseService;
    private final AuditService auditService;

    // Position #1 wait time in minutes
    private static final int POSITION_ONE_WAIT_TIME = 5;
    // Buffer per patient for transitions (handoff, documentation)
    private static final int BUFFER_PER_PATIENT = 3;

    /**
     * Recalculate positions and wait times for all waiting tickets
     * Called when queue changes (consultation starts/ends, lab results added, etc.)
     */
    @Transactional
    public void recalculateQueue(UUID facilityId, UUID departmentId) {
        log.info("🔄 Recalculating queue for facility: {}, department: {}", facilityId, departmentId);

        // Get all waiting tickets (TRIAGED, LAB_PENDING, LAB_COMPLETED)
        List<Ticket> waitingTickets = ticketRepository.findWaitingTicketsByFacilityAndDepartment(
                facilityId, departmentId
        );

        if (waitingTickets.isEmpty()) {
            log.info("No waiting tickets in queue for facility: {}, department: {}", facilityId, departmentId);
            return;
        }

        log.info("Found {} waiting tickets to recalculate", waitingTickets.size());

        // Recalculate positions
        int position = 1;
        for (Ticket ticket : waitingTickets) {
            int newPosition = position++;
            int newWaitTime = calculateWaitTime(newPosition, ticket.getPriority());

            // Only update if changed
            if (ticket.getQueuePosition() != newPosition ||
                    ticket.getEstimatedWaitMinutes() != newWaitTime) {

                log.debug("Ticket {}: Position {}→{}, Wait {}→{} min",
                        ticket.getTicketNumber(),
                        ticket.getQueuePosition(),
                        newPosition,
                        ticket.getEstimatedWaitMinutes(),
                        newWaitTime);

                ticket.setQueuePosition(newPosition);
                ticket.setEstimatedWaitMinutes(newWaitTime);
            }
        }

        // Batch save updates
        List<Ticket> savedTickets = ticketRepository.saveAll(waitingTickets);

        // Send SSE updates to all affected patients
        for (Ticket ticket : savedTickets) {
            sseService.sendTicketUpdate(ticket);
        }

        log.info("✅ Updated positions for {} tickets in facility: {}, department: {}",
                savedTickets.size(), facilityId, departmentId);

        // Audit log
        auditService.logSecurityEvent(
                "QUEUE_RECALCULATED",
                "system",
                null,
                null,
                "Facility: " + facilityId + ", Department: " + departmentId +
                        ", Tickets updated: " + savedTickets.size()
        );
    }

    /**
     * Calculate wait time based on position and priority
     */
    private int calculateWaitTime(int position, Priority priority) {
        // Position #1: Next in line
        if (position == 1) {
            return POSITION_ONE_WAIT_TIME;
        }

        // Get minutes per patient based on priority
        int minutesPerPatient = switch (priority) {
            case EMERGENCY -> 5;
            case HIGH -> 10;
            case MEDIUM -> 15;
            case LOW -> 20;
            default -> 15;
        };

        int patientsAhead = position - 1;
        int waitTime = (patientsAhead * minutesPerPatient) + (patientsAhead * BUFFER_PER_PATIENT);

        // Minimum wait for position #2
        if (position == 2 && waitTime < 10) {
            waitTime = 10;
        }

        // Cap at reasonable maximum
        if (waitTime > 180) {
            waitTime = 180;
        }

        return waitTime;
    }

    /**
     * Remove a ticket from the queue (when consultation starts or completes)
     */
    @Transactional
    public void removeTicketFromQueue(Ticket ticket) {
        if (ticket.getQueuePosition() != null && ticket.getQueuePosition() > 0) {
            log.info("Removing ticket {} from queue (position: {})",
                    ticket.getTicketNumber(), ticket.getQueuePosition());

            ticket.setQueuePosition(null);
            ticket.setEstimatedWaitMinutes(null);
            ticketRepository.save(ticket);

            // Recalculate remaining queue
            recalculateQueue(
                    ticket.getFacility().getId(),
                    ticket.getDepartment().getId()
            );
        }
    }

    /**
     * Reset a ticket's position to null (for IN_CONSULTATION or CONSULTATION_DONE)
     */
    @Transactional
    public void clearTicketPosition(Ticket ticket) {
        if (ticket.getQueuePosition() != null) {
            log.info("Clearing position for ticket {} (currently position {})",
                    ticket.getTicketNumber(), ticket.getQueuePosition());

            ticket.setQueuePosition(null);
            ticket.setEstimatedWaitMinutes(null);
            ticketRepository.save(ticket);
        }
    }
}