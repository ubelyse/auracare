package com.mvura.controller;

import com.mvura.dto.MedicalRecordDTO;
import com.mvura.model.MedicalRecord;
import com.mvura.model.User;
import com.mvura.repository.UserRepository;
import com.mvura.service.MedicalRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/patient/history")
@RequiredArgsConstructor
public class PatientHistoryController {

    private final MedicalRecordService recordService;
    private final UserRepository userRepository;

    private UUID getPatientId(Authentication auth) {
        String username = auth.getName();
        User patient = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Patient not found: " + username));
        return patient.getId();
    }

    private MedicalRecordDTO convertToDTO(MedicalRecord record) {
        MedicalRecordDTO dto = new MedicalRecordDTO();
        dto.setId(record.getId());
        dto.setRecordType(record.getRecordType());
        dto.setSummary(record.getSummary());
        dto.setDetails(record.getDetails());
        dto.setMetadata(record.getMetadata());
        dto.setRecordDate(record.getRecordDate());
        dto.setCreatedAt(record.getCreatedAt());
        dto.setUpdatedAt(record.getCreatedAt());
        dto.setDoctorName("N/A");
        dto.setDoctorId(null);
        return dto;
    }

    private List<MedicalRecordDTO> convertToDTOList(List<MedicalRecord> records) {
        return records.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @GetMapping("/records")
    public ResponseEntity<?> getMyRecords(Authentication auth) {
        UUID patientId = getPatientId(auth);
        List<MedicalRecord> records = recordService.getPatientRecords(patientId, auth.getName());
        List<MedicalRecordDTO> recordDTOs = convertToDTOList(records);

        return ResponseEntity.ok(Map.of(
                "records", recordDTOs,
                "count", recordDTOs.size()
        ));
    }

    @GetMapping("/records/{recordId}")
    public ResponseEntity<?> getRecord(@PathVariable UUID recordId, Authentication auth) {
        UUID patientId = getPatientId(auth);
        MedicalRecord record = recordService.getRecordById(recordId, patientId, auth.getName());
        MedicalRecordDTO recordDTO = convertToDTO(record);

        return ResponseEntity.ok(recordDTO);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchRecords(@RequestParam String keyword, Authentication auth) {
        UUID patientId = getPatientId(auth);
        List<MedicalRecord> records = recordService.searchRecords(patientId, keyword, auth.getName());
        List<MedicalRecordDTO> recordDTOs = convertToDTOList(records);

        return ResponseEntity.ok(Map.of(
                "records", recordDTOs,
                "count", recordDTOs.size(),
                "keyword", keyword
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getRecordStats(Authentication auth) {
        UUID patientId = getPatientId(auth);

        return ResponseEntity.ok(Map.of(
                "totalVisits", 0,
                "lastVisit", "2024-01-01",
                "upcomingAppointments", 0
        ));
    }
}