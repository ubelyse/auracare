package com.mvura.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class FacilityDTO {
    private UUID id;
    private String name;
    private String code;
    private String address;
    private String phone;
    private String email;
    private boolean active;
}