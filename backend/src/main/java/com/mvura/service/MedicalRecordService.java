package com.mvura.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvura.model.MedicalRecord;
import com.mvura.model.User;
import com.mvura.repository.MedicalRecordRepository;
import com.mvura.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedicalRecordService {

    private final MedicalRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final EncryptionService encryptionService;

    @Transactional
    public MedicalRecord createRecord(UUID patientId, String recordType,
                                      String summary, String details, Map<String, Object> metadata,
                                      String actorUsername) {

        User patient = userRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        MedicalRecord record = MedicalRecord.builder()
                .patient(patient)
                .recordType(recordType)
                .recordDate(LocalDateTime.now())
                .summary(summary)
                .details(details)
                .metadata(metadataToJson(metadata))
                .build();

        MedicalRecord saved = recordRepository.save(record);

        auditService.logAction(
                "MEDICAL_RECORD_CREATED",
                "MEDICAL_RECORD",
                saved.getId().toString(),
                actorUsername,
                null,
                null,
                Map.of("recordType", recordType, "patientId", patientId)
        );

        log.info("Medical record created for patient {}: {}", patientId, recordType);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<MedicalRecord> getPatientRecords(UUID patientId, String actorUsername) {
        List<MedicalRecord> records = recordRepository.findByPatientIdOrderByRecordDateDesc(patientId);

        for (MedicalRecord record : records) {
            // Decrypt summary
            if (record.getSummary() != null) {
                log.info("🔑 Raw summary (first 50 chars): {}",
                        record.getSummary().length() > 50 ? record.getSummary().substring(0, 50) + "..." : record.getSummary());

                try {
                    // Try to decrypt
                    String decrypted = encryptionService.decrypt(record.getSummary());
                    log.info("🔑 Decrypted summary: {}", decrypted);
                    record.setSummary(decrypted);
                } catch (Exception e) {
                    log.error("❌ Failed to decrypt summary: {}", e.getMessage());
                    // Keep the original value
                    record.setSummary("[ERROR: " + e.getMessage() + "]");
                }
            }

            // Decrypt details
            if (record.getDetails() != null) {
                log.info("🔑 Raw details (first 50 chars): {}",
                        record.getDetails().length() > 50 ? record.getDetails().substring(0, 50) + "..." : record.getDetails());

                try {
                    String decrypted = encryptionService.decrypt(record.getDetails());
                    log.info("🔑 Decrypted details: {}", decrypted);
                    record.setDetails(decrypted);
                } catch (Exception e) {
                    log.error("❌ Failed to decrypt details: {}", e.getMessage());
                    record.setDetails("[ERROR: " + e.getMessage() + "]");
                }
            }
        }

        auditService.logAction(
                "PATIENT_VIEW_HISTORY",
                "MEDICAL_RECORD",
                patientId.toString(),
                actorUsername,
                null,
                null,
                Map.of("recordCount", records.size())
        );

        log.info("Retrieved {} records for patient {}", records.size(), patientId);
        return records;
    }

    @Transactional(readOnly = true)
    public MedicalRecord getRecordById(UUID recordId, UUID patientId, String actorUsername) {
        MedicalRecord record = recordRepository.findByIdAndPatientId(recordId, patientId)
                .orElseThrow(() -> new RuntimeException("Record not found or access denied"));

        if (record.getSummary() != null) {
            try {
                record.setSummary(encryptionService.decrypt(record.getSummary()));
            } catch (Exception e) {
                log.error("❌ Failed to decrypt summary: {}", e.getMessage());
                record.setSummary("[ERROR: " + e.getMessage() + "]");
            }
        }
        if (record.getDetails() != null) {
            try {
                record.setDetails(encryptionService.decrypt(record.getDetails()));
            } catch (Exception e) {
                log.error("❌ Failed to decrypt details: {}", e.getMessage());
                record.setDetails("[ERROR: " + e.getMessage() + "]");
            }
        }

        auditService.logAction(
                "VIEW_RECORD",
                "MEDICAL_RECORD",
                recordId.toString(),
                actorUsername,
                null,
                null,
                Map.of("recordType", record.getRecordType())
        );

        return record;
    }

    @Transactional(readOnly = true)
    public List<MedicalRecord> searchRecords(UUID patientId, String keyword, String actorUsername) {
        List<MedicalRecord> records = recordRepository.findByPatientIdAndRecordTypeContaining(
                patientId, keyword
        );

        for (MedicalRecord record : records) {
            if (record.getSummary() != null) {
                try {
                    record.setSummary(encryptionService.decrypt(record.getSummary()));
                } catch (Exception e) {
                    log.error("❌ Failed to decrypt summary: {}", e.getMessage());
                    record.setSummary("[ERROR: " + e.getMessage() + "]");
                }
            }
            if (record.getDetails() != null) {
                try {
                    record.setDetails(encryptionService.decrypt(record.getDetails()));
                } catch (Exception e) {
                    log.error("❌ Failed to decrypt details: {}", e.getMessage());
                    record.setDetails("[ERROR: " + e.getMessage() + "]");
                }
            }
        }

        auditService.logAction(
                "SEARCH_RECORDS",
                "MEDICAL_RECORD",
                patientId.toString(),
                actorUsername,
                null,
                null,
                Map.of("keyword", keyword, "resultCount", records.size())
        );

        log.info("Found {} records for patient {} with keyword '{}'", records.size(), patientId, keyword);
        return records;
    }

    private String metadataToJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.error("Failed to serialize medical record metadata", e);
            return "{}";
        }
    }
}