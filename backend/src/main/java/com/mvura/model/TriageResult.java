package com.mvura.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageResult {
    private Priority priority;
    private Integer triageScore;
    private String triageMethod;
    private Double aiConfidence;
    private Integer estimatedWaitMinutes;
    private String recommendations;
    private String aiResponse; // For debugging
}