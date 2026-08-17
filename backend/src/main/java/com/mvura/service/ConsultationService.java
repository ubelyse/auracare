package com.mvura.service;

import com.mvura.model.*;
import com.mvura.repository.ConsultationRepository;
import com.mvura.repository.MedicalRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final AuditService auditService;

    // ===== ORIGINAL EXISTING METHODS =====

    @Transactional
    public Consultation getOrCreateConsultation(Ticket ticket, User doctor) {
        return consultationRepository.findByTicketId(ticket.getId())
                .orElseGet(() -> consultationRepository.save(
                        Consultation.builder()
                                .ticket(ticket)
                                .doctor(doctor)
                                .symptoms(ticket.getSymptoms())
                                .startedAt(LocalDateTime.now())
                                .build()
                ));
    }

    // ===== FIXED: Record Lab Order with LabTestType =====
    @Transactional
    public void recordLabOrder(Ticket ticket, LabTestType testType) {
        Consultation consultation = consultationRepository.findByTicketId(ticket.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No consultation record found for ticket " + ticket.getTicketNumber()));

        // ===== FIX: Append to existing lab orders =====
        String existingLabOrders = consultation.getLabOrders();
        String updatedLabOrders;

        if (existingLabOrders == null || existingLabOrders.isEmpty()) {
            updatedLabOrders = testType.name();
        } else {
            updatedLabOrders = existingLabOrders + ", " + testType.name();
        }

        consultation.setLabOrders(updatedLabOrders);
        consultationRepository.save(consultation);
        log.info("Lab order recorded for ticket {}: {}", ticket.getTicketNumber(), testType);
    }

    // ===== FIXED: Record Lab Order with Service Code and Pricing =====
    @Transactional
    public void recordLabOrder(Ticket ticket, String serviceCode, ServicePricing pricing) {
        log.info("Recording lab order for ticket: {} with service code: {}", ticket.getTicketNumber(), serviceCode);

        Consultation consultation = consultationRepository.findByTicketId(ticket.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No consultation record found for ticket " + ticket.getTicketNumber()));

        // ===== FIX: Append to existing lab orders =====
        String newLabOrder = String.format("%s (%s)", pricing.getServiceName(), serviceCode);

        String existingLabOrders = consultation.getLabOrders();
        String updatedLabOrders;

        if (existingLabOrders == null || existingLabOrders.isEmpty()) {
            updatedLabOrders = newLabOrder;
        } else {
            updatedLabOrders = existingLabOrders + ", " + newLabOrder;
        }

        consultation.setLabOrders(updatedLabOrders);
        consultationRepository.save(consultation);

        // Add a note about the lab order
        String timestamp = LocalDateTime.now().toString();
        String note = String.format("[%s] Lab Order: %s (%s) - Price: %s RWF",
                timestamp, pricing.getServiceName(), serviceCode, pricing.getBasePrice());

        String existingNotes = consultation.getNotes();
        if (existingNotes == null || existingNotes.isEmpty()) {
            consultation.setNotes(note);
        } else {
            consultation.setNotes(existingNotes + "\n" + note);
        }

        consultationRepository.save(consultation);

        auditService.logAction(
                "LAB_ORDERED_WITH_SERVICE_CODE",
                "CONSULTATION",
                consultation.getId().toString(),
                null,
                null,
                null,
                Map.of(
                        "ticketNumber", ticket.getTicketNumber(),
                        "serviceCode", serviceCode,
                        "serviceName", pricing.getServiceName(),
                        "price", pricing.getBasePrice()
                )
        );

        log.info("Lab order recorded for ticket {}: {} ({})",
                ticket.getTicketNumber(), pricing.getServiceName(), serviceCode);
    }

    @Transactional
    public void recordLabResult(Ticket ticket, String result) {
        Consultation consultation = consultationRepository.findByTicketId(ticket.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No consultation record found for ticket " + ticket.getTicketNumber()));
        consultation.setLabResults(result);
        consultationRepository.save(consultation);
        log.info("Lab result recorded for ticket {}", ticket.getTicketNumber());
    }

    @Transactional
    public void recordDiagnosis(Ticket ticket, String diagnosis) {
        Consultation consultation = consultationRepository.findByTicketId(ticket.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No consultation record found for ticket " + ticket.getTicketNumber()));
        consultation.setDiagnosis(diagnosis);
        consultationRepository.save(consultation);
        log.info("Diagnosis recorded for ticket {}", ticket.getTicketNumber());
    }

    @Transactional
    public void recordPrescription(Ticket ticket, String prescription) {
        Consultation consultation = consultationRepository.findByTicketId(ticket.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No consultation record found for ticket " + ticket.getTicketNumber()));
        consultation.setPrescription(prescription);
        consultationRepository.save(consultation);
        log.info("Prescription recorded for ticket {}", ticket.getTicketNumber());
    }

    @Transactional
    public void completeConsultationRecord(Ticket ticket) {
        Consultation consultation = consultationRepository.findByTicketId(ticket.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No consultation record found for ticket " + ticket.getTicketNumber()));
        consultation.setCompletedAt(LocalDateTime.now());
        consultationRepository.save(consultation);

        // Build the medical record summary
        StringBuilder summary = new StringBuilder();
        summary.append("Consultation at ").append(ticket.getFacility().getName());
        if (consultation.getDiagnosis() != null) {
            summary.append(" - Diagnosis: ").append(consultation.getDiagnosis());
        }

        // Build details
        String details = buildDetails(consultation);

        // Build metadata
        String metadata = "{\"ticketId\":\"" + ticket.getId()
                + "\",\"consultationId\":\"" + consultation.getId() + "\"}";

        MedicalRecord record = MedicalRecord.builder()
                .patient(ticket.getPatient())
                .recordType("CONSULTATION")
                .summary(summary.toString())
                .details(details)
                .metadata(metadata)
                .build();

        medicalRecordRepository.save(record);
        log.info("Medical record created for ticket {}", ticket.getTicketNumber());
    }

    private String buildDetails(Consultation c) {
        StringBuilder sb = new StringBuilder();
        if (c.getSymptoms() != null) {
            sb.append("Symptoms: ").append(c.getSymptoms()).append(". ");
        }
        if (c.getLabOrders() != null) {
            sb.append("Lab ordered: ").append(c.getLabOrders()).append(". ");
        }
        if (c.getLabResults() != null) {
            sb.append("Lab result: ").append(c.getLabResults()).append(". ");
        }
        if (c.getDiagnosis() != null) {
            sb.append("Diagnosis: ").append(c.getDiagnosis()).append(". ");
        }
        if (c.getPrescription() != null) {
            sb.append("Prescription: ").append(c.getPrescription()).append(". ");
        }
        return sb.toString();
    }

    // ==================== 1. CONSULTATION NOTES ====================

    @Transactional
    public Consultation addConsultationNotes(UUID consultationId, String notes, User doctor) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("Consultation not found"));

        if (!consultation.getDoctor().getId().equals(doctor.getId())) {
            log.warn("Unauthorized note addition attempt by doctor {} on consultation {}",
                    doctor.getUsername(), consultationId);
            throw new SecurityException("Only the assigned doctor can add notes");
        }

        // Append notes with timestamp
        String timestamp = LocalDateTime.now().toString();
        String newNote = String.format("[%s] %s: %s", timestamp, doctor.getFirstName() + " " + doctor.getLastName(), notes);

        String existingNotes = consultation.getNotes();
        if (existingNotes == null || existingNotes.isEmpty()) {
            consultation.setNotes(newNote);
        } else {
            consultation.setNotes(existingNotes + "\n" + newNote);
        }

        Consultation saved = consultationRepository.save(consultation);

        auditService.logAction(
                "CONSULTATION_NOTE_ADDED",
                "CONSULTATION",
                consultationId.toString(),
                doctor.getUsername(),
                null,
                null,
                Map.of(
                        "note", notes,
                        "timestamp", timestamp
                )
        );

        log.info("Consultation notes added by doctor {} for consultation {}",
                doctor.getUsername(), consultationId);
        return saved;
    }

    @Transactional
    public Consultation addConsultationNoteByTicket(UUID ticketId, String notes, User doctor) {
        Consultation consultation = consultationRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new RuntimeException("Consultation not found for ticket: " + ticketId));

        return addConsultationNotes(consultation.getId(), notes, doctor);
    }

    public List<String> getConsultationNotes(UUID consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("Consultation not found"));

        String notes = consultation.getNotes();
        if (notes == null || notes.isEmpty()) {
            return List.of();
        }

        return Arrays.asList(notes.split("\n"));
    }

    @Transactional
    public Consultation editConsultationNote(UUID consultationId, int noteIndex, String newNote, User doctor) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("Consultation not found"));

        if (!consultation.getDoctor().getId().equals(doctor.getId())) {
            throw new SecurityException("Only the assigned doctor can edit notes");
        }

        List<String> notes = getConsultationNotes(consultationId);
        if (noteIndex < 0 || noteIndex >= notes.size()) {
            throw new IllegalArgumentException("Invalid note index");
        }

        String timestamp = LocalDateTime.now().toString();
        String updatedNote = String.format("[%s] (EDITED) %s: %s",
                timestamp, doctor.getFirstName() + " " + doctor.getLastName(), newNote);

        notes.set(noteIndex, updatedNote);
        consultation.setNotes(String.join("\n", notes));

        Consultation saved = consultationRepository.save(consultation);

        auditService.logAction(
                "CONSULTATION_NOTE_EDITED",
                "CONSULTATION",
                consultationId.toString(),
                doctor.getUsername(),
                null,
                null,
                Map.of(
                        "noteIndex", noteIndex,
                        "newNote", newNote
                )
        );

        return saved;
    }

    // ==================== 2. FOLLOW-UP SCHEDULING ====================

    @Transactional
    public Consultation scheduleFollowUp(UUID consultationId, LocalDateTime followUpDate, String reason) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("Consultation not found"));

        if (followUpDate.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Follow-up date cannot be in the past");
        }

        consultation.setFollowUpDate(followUpDate);
        Consultation saved = consultationRepository.save(consultation);

        // Add note about follow-up
        String note = String.format("Follow-up scheduled for: %s. Reason: %s",
                followUpDate.toString(), reason != null ? reason : "Follow-up appointment");
        addConsultationNotes(consultationId, note, consultation.getDoctor());

        auditService.logAction(
                "FOLLOW_UP_SCHEDULED",
                "CONSULTATION",
                consultationId.toString(),
                consultation.getDoctor().getUsername(),
                null,
                null,
                Map.of(
                        "followUpDate", followUpDate,
                        "reason", reason
                )
        );

        log.info("Follow-up scheduled for consultation {} on {}",
                consultationId, followUpDate);
        return saved;
    }

    @Transactional
    public Consultation rescheduleFollowUp(UUID consultationId, LocalDateTime newFollowUpDate, String reason) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("Consultation not found"));

        if (newFollowUpDate.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Follow-up date cannot be in the past");
        }

        LocalDateTime oldDate = consultation.getFollowUpDate();
        consultation.setFollowUpDate(newFollowUpDate);
        Consultation saved = consultationRepository.save(consultation);

        String note = String.format("Follow-up rescheduled from %s to %s. Reason: %s",
                oldDate != null ? oldDate : "Not set",
                newFollowUpDate,
                reason != null ? reason : "Rescheduled");
        addConsultationNotes(consultationId, note, consultation.getDoctor());

        auditService.logAction(
                "FOLLOW_UP_RESCHEDULED",
                "CONSULTATION",
                consultationId.toString(),
                consultation.getDoctor().getUsername(),
                null,
                null,
                Map.of(
                        "oldDate", oldDate,
                        "newDate", newFollowUpDate,
                        "reason", reason
                )
        );

        log.info("Follow-up rescheduled for consultation {} from {} to {}",
                consultationId, oldDate, newFollowUpDate);
        return saved;
    }

    @Transactional
    public Consultation cancelFollowUp(UUID consultationId, String reason) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("Consultation not found"));

        LocalDateTime cancelledDate = consultation.getFollowUpDate();
        consultation.setFollowUpDate(null);
        Consultation saved = consultationRepository.save(consultation);

        String note = String.format("Follow-up cancelled. Previous date: %s. Reason: %s",
                cancelledDate != null ? cancelledDate : "Not set",
                reason != null ? reason : "Cancelled");
        addConsultationNotes(consultationId, note, consultation.getDoctor());

        auditService.logAction(
                "FOLLOW_UP_CANCELLED",
                "CONSULTATION",
                consultationId.toString(),
                consultation.getDoctor().getUsername(),
                null,
                null,
                Map.of(
                        "cancelledDate", cancelledDate,
                        "reason", reason
                )
        );

        log.info("Follow-up cancelled for consultation {}", consultationId);
        return saved;
    }

    public List<Consultation> getFollowUpsDueToday() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        return consultationRepository.findAll().stream()
                .filter(c -> c.getFollowUpDate() != null)
                .filter(c -> c.getFollowUpDate().isAfter(startOfDay) && c.getFollowUpDate().isBefore(endOfDay))
                .toList();
    }

    // ==================== 3. PRESCRIPTION MANAGEMENT ====================

    @Transactional
    public Consultation recordPrescriptionWithDetails(UUID consultationId,
                                                      String medication,
                                                      String dosage,
                                                      String frequency,
                                                      String duration,
                                                      String notes) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("Consultation not found"));

        String prescription = String.format("Medication: %s | Dosage: %s | Frequency: %s | Duration: %s | Notes: %s",
                medication,
                dosage != null ? dosage : "As directed",
                frequency != null ? frequency : "As directed",
                duration != null ? duration : "As directed",
                notes != null ? notes : "N/A");

        consultation.setPrescription(prescription);
        Consultation saved = consultationRepository.save(consultation);

        // Add note about prescription
        String note = String.format("Prescribed: %s %s %s for %s",
                medication, dosage, frequency, duration);
        addConsultationNotes(consultationId, note, consultation.getDoctor());

        auditService.logAction(
                "PRESCRIPTION_RECORDED",
                "CONSULTATION",
                consultationId.toString(),
                consultation.getDoctor().getUsername(),
                null,
                null,
                Map.of(
                        "medication", medication,
                        "dosage", dosage,
                        "frequency", frequency,
                        "duration", duration
                )
        );

        log.info("Prescription recorded for consultation {}: {}", consultationId, medication);
        return saved;
    }

    @Transactional
    public Consultation updatePrescription(UUID consultationId,
                                           String medication,
                                           String dosage,
                                           String frequency,
                                           String duration,
                                           String notes) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("Consultation not found"));

        String oldPrescription = consultation.getPrescription();

        String newPrescription = String.format("Medication: %s | Dosage: %s | Frequency: %s | Duration: %s | Notes: %s",
                medication,
                dosage != null ? dosage : "As directed",
                frequency != null ? frequency : "As directed",
                duration != null ? duration : "As directed",
                notes != null ? notes : "N/A");

        consultation.setPrescription(newPrescription);
        Consultation saved = consultationRepository.save(consultation);

        String note = String.format("Prescription updated from: %s to: %s",
                oldPrescription, newPrescription);
        addConsultationNotes(consultationId, note, consultation.getDoctor());

        auditService.logAction(
                "PRESCRIPTION_UPDATED",
                "CONSULTATION",
                consultationId.toString(),
                consultation.getDoctor().getUsername(),
                null,
                null,
                Map.of(
                        "oldPrescription", oldPrescription,
                        "newPrescription", newPrescription
                )
        );

        log.info("Prescription updated for consultation {}", consultationId);
        return saved;
    }

    public PrescriptionSummary getPrescriptionSummary(UUID consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("Consultation not found"));

        String prescription = consultation.getPrescription();
        if (prescription == null || prescription.isEmpty()) {
            return PrescriptionSummary.builder()
                    .hasPrescription(false)
                    .build();
        }

        Map<String, String> parsed = parsePrescription(prescription);

        return PrescriptionSummary.builder()
                .hasPrescription(true)
                .medication(parsed.get("medication"))
                .dosage(parsed.get("dosage"))
                .frequency(parsed.get("frequency"))
                .duration(parsed.get("duration"))
                .notes(parsed.get("notes"))
                .fullText(prescription)
                .build();
    }

    private Map<String, String> parsePrescription(String prescription) {
        Map<String, String> result = new HashMap<>();
        if (prescription == null) return result;

        String[] parts = prescription.split("\\|");
        for (String part : parts) {
            String[] kv = part.split(":", 2);
            if (kv.length == 2) {
                result.put(kv[0].trim().toLowerCase(), kv[1].trim());
            }
        }
        return result;
    }

    // ==================== 4. CONSULTATION VALIDATION ====================

    @Transactional
    public ConsultationValidationResult validateConsultation(UUID ticketId) {
        Consultation consultation = consultationRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new RuntimeException("Consultation not found for ticket: " + ticketId));

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check if consultation is complete
        if (consultation.getCompletedAt() == null) {
            errors.add("Consultation is not yet completed");
        }

        // Check if diagnosis is recorded
        if (consultation.getDiagnosis() == null || consultation.getDiagnosis().isEmpty()) {
            errors.add("Diagnosis is required to complete consultation");
        }

        // Check if symptoms are recorded
        if (consultation.getSymptoms() == null || consultation.getSymptoms().isEmpty()) {
            warnings.add("Symptoms not recorded");
        }

        // Check if prescription is recorded
        if (consultation.getPrescription() == null || consultation.getPrescription().isEmpty()) {
            warnings.add("Prescription not recorded");
        }

        // Check if lab results are back if labs were ordered
        if (consultation.getLabOrders() != null && !consultation.getLabOrders().isEmpty()) {
            if (consultation.getLabResults() == null || consultation.getLabResults().isEmpty()) {
                warnings.add("Lab results pending");
            }
        }

        // Check diagnosis code
        if (consultation.getDiagnosisCode() == null || consultation.getDiagnosisCode().isEmpty()) {
            warnings.add("Diagnosis code (ICD-10) not recorded");
        }

        // Check if follow-up is scheduled for chronic conditions
        if (consultation.getDiagnosis() != null && isChronicCondition(consultation.getDiagnosis())) {
            if (consultation.getFollowUpDate() == null) {
                warnings.add("Follow-up recommended for chronic condition");
            }
        }

        return ConsultationValidationResult.builder()
                .isValid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .consultationId(consultation.getId())
                .ticketId(ticketId)
                .build();
    }

    private boolean isChronicCondition(String diagnosis) {
        List<String> chronicKeywords = List.of(
                "diabetes", "hypertension", "asthma", "copd", "heart disease",
                "chronic", "arthritis", "depression", "epilepsy", "hiv"
        );
        String lower = diagnosis.toLowerCase();
        return chronicKeywords.stream().anyMatch(lower::contains);
    }

    @Transactional
    public Consultation completeConsultationWithValidation(Ticket ticket) {
        ConsultationValidationResult validation = validateConsultation(ticket.getId());

        if (!validation.isValid()) {
            throw new IllegalStateException(
                    "Consultation cannot be completed: " + String.join(", ", validation.getErrors())
            );
        }

        // Complete the consultation
        completeConsultationRecord(ticket);

        // Log any warnings
        if (!validation.getWarnings().isEmpty()) {
            log.warn("Consultation completed with warnings: {}", String.join(", ", validation.getWarnings()));
        }

        return consultationRepository.findByTicketId(ticket.getId()).get();
    }

    // ==================== INNER CLASSES ====================

    @lombok.Data
    @lombok.Builder
    public static class PrescriptionSummary {
        private boolean hasPrescription;
        private String medication;
        private String dosage;
        private String frequency;
        private String duration;
        private String notes;
        private String fullText;
    }

    @lombok.Data
    @lombok.Builder
    public static class ConsultationValidationResult {
        private boolean isValid;
        private List<String> errors;
        private List<String> warnings;
        private UUID consultationId;
        private UUID ticketId;
    }
}