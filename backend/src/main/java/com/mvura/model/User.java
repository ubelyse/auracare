package com.mvura.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ===== AUTHENTICATION FIELDS =====
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    @JsonIgnore
    private String password;

    // ===== PERSONAL INFORMATION =====
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    // ===== INSURANCE (ADDED) =====
    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_type")
    private InsuranceType insuranceType;  // MUTUELLE, RSSB, MMI, PRIVATE, UNINSURED

    @Column(name = "insurance_number")
    private String insuranceNumber;       // Mutuelle card number, RSSB member number

    // ===== PATIENT-SPECIFIC FIELDS =====
    @Column(name = "blood_type")
    private String bloodType;

    @Column(name = "weight_kg")
    private Double weight;

    @Column(name = "height_cm")
    private Double height;

    @Column(name = "chronic_conditions", columnDefinition = "TEXT")
    private String chronicConditions;

    @Column(name = "allergies", columnDefinition = "TEXT")
    private String allergies;

    // ===== EMERGENCY CONTACT =====
    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;

    // ===== ROLE =====
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole role;

    // ===== STATUS =====
    @Builder.Default
    @Column(name = "is_active")
    private boolean active = true;

    @Builder.Default
    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Builder.Default
    @Column(name = "mfa_enabled")
    private boolean mfaEnabled = false;

    @Column(name = "mfa_secret")
    @JsonIgnore
    private String mfaSecret;

    // ===== STAFF-ONLY FIELDS (Doctors, Admins, Staff) =====
    @ManyToOne
    @JoinColumn(name = "primary_facility_id")
    private Facility primaryFacility;

    @ManyToOne
    @JoinColumn(name = "primary_department_id")
    private Department primaryDepartment;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_facilities",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "facility_id")
    )
    @JsonIgnore
    private Set<Facility> facilities = new HashSet<>();

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "doctor_departments",
            joinColumns = @JoinColumn(name = "doctor_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    @JsonIgnore
    private Set<Department> departments = new HashSet<>();

    // ===== TIMESTAMPS =====
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt;

    // ===== HELPER METHODS =====
    public int getAge() {
        if (dateOfBirth == null) return 0;
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    public boolean isPatient() {
        return role == UserRole.PATIENT;
    }

    public boolean isDoctor() {
        return role == UserRole.DOCTOR;
    }

    public boolean isAdmin() {
        return role == UserRole.DISTRICT_ADMIN || role == UserRole.FACILITY_ADMIN;
    }

    public boolean hasInsurance() {
        return insuranceType != null && insuranceType != InsuranceType.UNINSURED;
    }

    // ===== STAFF METHODS =====
    public UUID getPrimaryDepartmentId() {
        if (primaryDepartment != null) {
            return primaryDepartment.getId();
        }
        return null;
    }

    public String getPrimaryDepartmentName() {
        if (primaryDepartment != null) {
            return primaryDepartment.getName();
        }
        return null;
    }

    public String getPrimaryDepartmentCode() {
        if (primaryDepartment != null) {
            return primaryDepartment.getCode();
        }
        return null;
    }

    public void addFacility(Facility facility) {
        if (facilities == null) {
            facilities = new HashSet<>();
        }
        facilities.add(facility);
    }

    public void removeFacility(Facility facility) {
        if (facilities != null) {
            facilities.remove(facility);
        }
    }

    public void addDepartment(Department department) {
        if (departments == null) {
            departments = new HashSet<>();
        }
        departments.add(department);
    }

    public void removeDepartment(Department department) {
        if (departments != null) {
            departments.remove(department);
        }
    }

    public boolean hasFacility(UUID facilityId) {
        if (facilities == null) return false;
        return facilities.stream().anyMatch(f -> f.getId().equals(facilityId));
    }

    public boolean hasDepartment(UUID departmentId) {
        if (departments == null || departments.isEmpty()) {
            return false;
        }
        return departments.stream().anyMatch(d -> d.getId().equals(departmentId));
    }

    // ===== SPRING SECURITY METHODS =====
    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return active && emailVerified;
    }

    // ===== LIFE CYCLE CALLBACKS =====
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastUpdatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastUpdatedAt = LocalDateTime.now();
    }
}