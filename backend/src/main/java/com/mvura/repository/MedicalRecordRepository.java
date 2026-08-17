package com.mvura.repository;

import com.mvura.model.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    List<MedicalRecord> findByPatientIdOrderByRecordDateDesc(UUID patientId);

    Optional<MedicalRecord> findByIdAndPatientId(UUID id, UUID patientId);

    List<MedicalRecord> findByPatientIdAndRecordType(UUID patientId, String recordType);

    List<MedicalRecord> findByPatientIdAndRecordDateBetween(UUID patientId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT m FROM MedicalRecord m WHERE m.patient.id = :patientId AND m.recordType LIKE %:keyword%")
    List<MedicalRecord> findByPatientIdAndRecordTypeContaining(@Param("patientId") UUID patientId,
                                                               @Param("keyword") String keyword);

    @Query("SELECT COUNT(m) FROM MedicalRecord m WHERE m.patient.id = :patientId")
    long countByPatientId(@Param("patientId") UUID patientId);
}