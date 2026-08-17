package com.mvura.controller;

import com.mvura.model.Gender;
import com.mvura.model.User;
import com.mvura.repository.UserRepository;
import com.mvura.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/patient/profile")
@RequiredArgsConstructor
public class PatientProfileController {

    private final UserRepository userRepository;
    private final AuditService auditService;

    private UUID getPatientId(Authentication auth) {
        String username = auth.getName();
        User patient = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Patient not found: " + username));
        return patient.getId();
    }

    @GetMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getProfile(Authentication auth) {
        UUID patientId = getPatientId(auth);
        User patient = userRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        return ResponseEntity.ok(Map.ofEntries(
                Map.entry("id", patient.getId()),
                Map.entry("firstName", patient.getFirstName() != null ? patient.getFirstName() : ""),
                Map.entry("lastName", patient.getLastName() != null ? patient.getLastName() : ""),
                Map.entry("email", patient.getEmail() != null ? patient.getEmail() : ""),
                Map.entry("phone", patient.getPhone() != null ? patient.getPhone() : ""),
                Map.entry("dateOfBirth", patient.getDateOfBirth() != null ? patient.getDateOfBirth().toString() : ""),
                Map.entry("gender", patient.getGender() != null ? patient.getGender().name() : ""),
                Map.entry("chronicConditions", patient.getChronicConditions() != null ? patient.getChronicConditions() : ""),
                Map.entry("allergies", patient.getAllergies() != null ? patient.getAllergies() : ""),
                Map.entry("emergencyContactName", patient.getEmergencyContactName() != null ? patient.getEmergencyContactName() : ""),
                Map.entry("emergencyContactPhone", patient.getEmergencyContactPhone() != null ? patient.getEmergencyContactPhone() : ""),
                Map.entry("bloodType", patient.getBloodType() != null ? patient.getBloodType() : "")
        ));
    }

    @PutMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody ProfileUpdateRequest request, Authentication auth) {
        UUID patientId = getPatientId(auth);
        User patient = userRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        // Track what actually changed for the audit log — cheap to do
        // here since we already have old vs new values in hand.
        Map<String, Object> changedFields = new HashMap<>();

        if (request.getFirstName() != null && !request.getFirstName().equals(patient.getFirstName())) {
            changedFields.put("firstName", true);
            patient.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null && !request.getLastName().equals(patient.getLastName())) {
            changedFields.put("lastName", true);
            patient.setLastName(request.getLastName());
        }
        if (request.getPhone() != null && !request.getPhone().equals(patient.getPhone())) {
            changedFields.put("phone", true);
            patient.setPhone(request.getPhone());
        }
        if (request.getDateOfBirth() != null && !request.getDateOfBirth().equals(patient.getDateOfBirth())) {
            changedFields.put("dateOfBirth", true);
            patient.setDateOfBirth(request.getDateOfBirth());
        }

        if (request.getGender() != null && !request.getGender().isEmpty()) {
            try {
                Gender newGender = Gender.valueOf(request.getGender().toUpperCase());
                if (newGender != patient.getGender()) {
                    changedFields.put("gender", true);
                    patient.setGender(newGender);
                }
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid gender value provided"));
            }
        }

        // These three are flagged individually in the audit log — they're
        // safety-critical clinical fields (allergies/conditions feed
        // directly into triage scoring at check-in; blood type matters in
        // an emergency), unlike name/phone which are just contact details.
        if (request.getChronicConditions() != null && !request.getChronicConditions().equals(patient.getChronicConditions())) {
            changedFields.put("chronicConditionsChanged", true);
            patient.setChronicConditions(request.getChronicConditions());
        }
        if (request.getAllergies() != null && !request.getAllergies().equals(patient.getAllergies())) {
            changedFields.put("allergiesChanged", true);
            patient.setAllergies(request.getAllergies());
        }
        if (request.getBloodType() != null && !request.getBloodType().equals(patient.getBloodType())) {
            changedFields.put("bloodTypeChanged", true);
            patient.setBloodType(request.getBloodType());
        }

        if (request.getEmergencyContactName() != null) patient.setEmergencyContactName(request.getEmergencyContactName());
        if (request.getEmergencyContactPhone() != null) patient.setEmergencyContactPhone(request.getEmergencyContactPhone());

        userRepository.save(patient);

        // FIX: previously no audit event at all for profile updates,
        // despite this endpoint modifying safety-critical clinical fields.
        // Deliberately logs WHICH fields changed, not the actual values —
        // no need to put PHI content itself into the audit log to know
        // that "allergies was modified at time X by user Y".
        if (!changedFields.isEmpty()) {
            auditService.logAction(
                    "PROFILE_UPDATED",
                    "USER",
                    patient.getId().toString(),
                    auth.getName(),
                    null,
                    null,
                    changedFields
            );
        }

        // FIX: previously returned the raw User entity. Even though
        // User.java now has @JsonIgnore on password/mfaSecret, returning
        // an explicit response shape here is defense in depth rather than
        // relying solely on that global annotation.
        return ResponseEntity.ok(Map.of(
                "message", "Profile updated successfully",
                "patient", Map.ofEntries(
                        Map.entry("id", patient.getId()),
                        Map.entry("firstName", patient.getFirstName() != null ? patient.getFirstName() : ""),
                        Map.entry("lastName", patient.getLastName() != null ? patient.getLastName() : ""),
                        Map.entry("email", patient.getEmail() != null ? patient.getEmail() : ""),
                        Map.entry("phone", patient.getPhone() != null ? patient.getPhone() : ""),
                        Map.entry("dateOfBirth", patient.getDateOfBirth() != null ? patient.getDateOfBirth().toString() : ""),
                        Map.entry("gender", patient.getGender() != null ? patient.getGender().name() : ""),
                        Map.entry("chronicConditions", patient.getChronicConditions() != null ? patient.getChronicConditions() : ""),
                        Map.entry("allergies", patient.getAllergies() != null ? patient.getAllergies() : ""),
                        Map.entry("emergencyContactName", patient.getEmergencyContactName() != null ? patient.getEmergencyContactName() : ""),
                        Map.entry("emergencyContactPhone", patient.getEmergencyContactPhone() != null ? patient.getEmergencyContactPhone() : ""),
                        Map.entry("bloodType", patient.getBloodType() != null ? patient.getBloodType() : "")
                )
        ));
    }
}