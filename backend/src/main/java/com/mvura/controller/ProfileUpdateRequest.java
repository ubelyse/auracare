package com.mvura.controller;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

// FIX: previously had zero validation constraints at all. Fields remain
// nullable (a null means "don't change this field" in the partial-update
// logic in the controller) but non-null values are now constrained.
@Data
public class ProfileUpdateRequest {

    @Size(max = 50, message = "First name too long")
    private String firstName;

    @Size(max = 50, message = "Last name too long")
    private String lastName;

    @Pattern(regexp = "^$|^[0-9+\\-() ]{6,20}$", message = "Invalid phone number format")
    private String phone;

    private LocalDate dateOfBirth;

    private String gender;

    @Size(max = 1000, message = "Chronic conditions field too long")
    private String chronicConditions;

    @Size(max = 1000, message = "Allergies field too long")
    private String allergies;

    @Size(max = 100, message = "Emergency contact name too long")
    private String emergencyContactName;

    @Pattern(regexp = "^$|^[0-9+\\-() ]{6,20}$", message = "Invalid emergency contact phone format")
    private String emergencyContactPhone;

    // FIX: previously accepted any arbitrary string. Blood type is a
    // constrained set of real values — a doctor reading this in an
    // emergency needs it to actually mean something.
    @Pattern(regexp = "^$|^(A|B|AB|O)[+-]$", message = "Invalid blood type — expected e.g. A+, O-, AB+")
    private String bloodType;
}