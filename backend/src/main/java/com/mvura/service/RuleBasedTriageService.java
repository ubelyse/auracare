package com.mvura.service;

import com.mvura.model.Priority;
import com.mvura.model.TriageResult;
import com.mvura.model.Ticket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RuleBasedTriageService {

    public TriageResult evaluate(Ticket ticket) {
        int score = 0;
        int age = ticket.getAge() != null ? ticket.getAge() : 30;

        // ===== AGE-BASED RISK =====
        if (age >= 80) {
            score += 40;
            log.debug("Age 80+ - CRITICAL flag");
        } else if (age >= 65) {
            score += 25;
            log.debug("Age 65+ - HIGH flag");
        } else if (age < 5) {
            score += 25;
            log.debug("Age under 5 - HIGH flag");
        } else if (age < 12) {
            score += 10;
            log.debug("Age under 12 - MEDIUM flag");
        }

        // ===== PREGNANCY RISK =====
        if (ticket.getIsPregnant() != null && ticket.getIsPregnant()) {
            score += 30;
            log.debug("Pregnancy - HIGH flag");
        }

        // ===== CHRONIC CONDITIONS =====
        if (ticket.getChronicConditions() != null && !ticket.getChronicConditions().isEmpty()) {
            String[] conditions = ticket.getChronicConditions().split(",");
            for (String condition : conditions) {
                switch (condition.trim().toUpperCase()) {
                    case "DIABETES":
                        score += 20;
                        log.debug("Diabetes - HIGH flag");
                        break;
                    case "HYPERTENSION":
                        score += 15;
                        log.debug("Hypertension - MEDIUM flag");
                        break;
                    case "HEART_DISEASE":
                        score += 30;
                        log.debug("Heart Disease - HIGH flag");
                        break;
                    case "ASTHMA":
                        score += 15;
                        log.debug("Asthma - MEDIUM flag");
                        break;
                    case "KIDNEY_DISEASE":
                        score += 25;
                        log.debug("Kidney Disease - HIGH flag");
                        break;
                    case "CANCER":
                        score += 30;
                        log.debug("Cancer - HIGH flag");
                        break;
                    default:
                        score += 10;
                        log.debug("Chronic condition: {} - MEDIUM flag", condition);
                }
            }
        }

        // ===== RECENT SURGERY =====
        if (ticket.getHasRecentSurgery() != null && ticket.getHasRecentSurgery()) {
            score += 20;
            log.debug("Recent surgery - HIGH flag");
        }

        // ===== SYMPTOM-BASED SCORING =====
        String symptoms = ticket.getSymptoms() != null ? ticket.getSymptoms().toLowerCase() : "";

        if (symptoms.contains("chest pain") ||
                symptoms.contains("difficulty breathing") ||
                symptoms.contains("shortness of breath")) {
            score += 35;
            log.debug("Emergency symptom detected: chest pain/breathing difficulty");
        }

        if (symptoms.contains("severe headache") || symptoms.contains("migraine")) {
            score += 15;
            log.debug("Severe headache detected");
        }

        if (symptoms.contains("bleeding") || symptoms.contains("hemorrhage") ||
                symptoms.contains("blood in stool") || symptoms.contains("blood in urine")) {
            score += 30;
            log.debug("Bleeding symptom detected");
        }

        if (symptoms.contains("fever") && symptoms.contains("chills")) {
            score += 15;
            log.debug("Fever + chills detected");
        }

        if (symptoms.contains("vomiting") || symptoms.contains("nausea")) {
            score += 10;
            log.debug("Vomiting/nausea detected");
        }

        if (symptoms.contains("dizziness") || symptoms.contains("fainting") ||
                symptoms.contains("loss of consciousness")) {
            score += 25;
            log.debug("Dizziness/fainting detected");
        }

        if (symptoms.contains("severe pain") || symptoms.contains("unbearable pain")) {
            score += 20;
            log.debug("Severe pain detected");
        }

        if (symptoms.contains("stroke") || symptoms.contains("paralysis") ||
                symptoms.contains("numbness") || symptoms.contains("slurred speech")) {
            score += 40;
            log.debug("Stroke symptoms detected - CRITICAL");
        }

        // ===== VITALS =====
        if (ticket.getTemperature() != null) {
            if (ticket.getTemperature() > 39.0) {
                score += 25;
                log.debug("Temperature > 39°C - HIGH");
            } else if (ticket.getTemperature() > 38.0) {
                score += 15;
                log.debug("Temperature > 38°C - MEDIUM");
            }
        }

        if (ticket.getHeartRate() != null) {
            if (ticket.getHeartRate() > 120) {
                score += 20;
                log.debug("Heart rate > 120 - HIGH");
            } else if (ticket.getHeartRate() > 100) {
                score += 10;
                log.debug("Heart rate > 100 - MEDIUM");
            }
        }

        // ===== DETERMINE PRIORITY =====
        Priority priority;
        if (score >= 80) {
            priority = Priority.EMERGENCY;
        } else if (score >= 60) {
            priority = Priority.HIGH;
        } else if (score >= 40) {
            priority = Priority.MEDIUM;
        } else {
            priority = Priority.LOW;
        }

        // ===== CALCULATE WAIT TIME =====
        int waitMinutes = switch (priority) {
            case EMERGENCY -> 5;
            case HIGH -> 15;
            case MEDIUM -> 30;
            default -> 60;
        };

        // ===== BUILD RECOMMENDATIONS =====
        StringBuilder recommendations = new StringBuilder();
        recommendations.append("Priority: ").append(priority).append("\n");
        recommendations.append("Triage Score: ").append(score).append("/100\n\n");

        if (priority == Priority.EMERGENCY) {
            recommendations.append("⚠️ IMMEDIATE MEDICAL ATTENTION REQUIRED!\n");
            recommendations.append("Please see a doctor urgently or call emergency services.");
        } else if (priority == Priority.HIGH) {
            recommendations.append("High priority. Please see a doctor within 15-20 minutes.");
        } else if (priority == Priority.MEDIUM) {
            recommendations.append("Moderate priority. Please wait for your turn.");
        } else {
            recommendations.append("Low priority. Please wait for your turn.");
        }

        if (ticket.getIsPregnant() != null && ticket.getIsPregnant()) {
            recommendations.append("\n⚠️ Pregnant patient - requires special attention.");
        }

        if (age >= 80) {
            recommendations.append("\n⚠️ Elderly patient (80+) - high risk.");
        }

        if (ticket.getChronicConditions() != null && !ticket.getChronicConditions().isEmpty()) {
            recommendations.append("\nChronic conditions detected: ");
            recommendations.append(ticket.getChronicConditions());
        }

        log.info("Rule-based triage complete - Score: {}, Priority: {}, Method: RULE_BASED",
                score, priority);

        return TriageResult.builder()
                .priority(priority)
                .triageScore(score)
                .triageMethod("RULE_BASED")
                .estimatedWaitMinutes(waitMinutes)
                .recommendations(recommendations.toString())
                .build();
    }
}