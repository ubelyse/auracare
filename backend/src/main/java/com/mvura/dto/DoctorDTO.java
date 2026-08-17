package com.mvura.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class DoctorDTO {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
}