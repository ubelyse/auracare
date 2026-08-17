package com.mvura.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class PhiScrubberService {

    // Patterns for PHI detection
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(\\+?[0-9]{10,15})"
    );

    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile(
            "\\b[0-9]{16}\\b"
    );

    // ===== FIXED: Removed NAME_PATTERN - too aggressive and inaccurate =====
    // Names are better handled at the field level, not in free text scrubbing.
    // If a patient writes "John Smith" in their symptoms, it might be a real
    // symptom description like "John Smith came to visit me" - we don't want
    // to remove that.
    // For proper PHI scrubbing, use NLP-based entity recognition instead of regex.

    public String scrubPhi(String text) {
        if (text == null) return null;

        String scrubbed = text;

        // Remove emails
        scrubbed = EMAIL_PATTERN.matcher(scrubbed).replaceAll("[EMAIL_REMOVED]");

        // Remove phone numbers
        scrubbed = PHONE_PATTERN.matcher(scrubbed).replaceAll("[PHONE_REMOVED]");

        // Remove national IDs (Rwanda format: 16 digits)
        scrubbed = NATIONAL_ID_PATTERN.matcher(scrubbed).replaceAll("[ID_REMOVED]");

        // NOTE: Name scrubbing is intentionally not done here because:
        // 1. Regex can't reliably identify names in free text
        // 2. It would remove legitimate medical terminology
        // 3. Names are better handled at the field level (separate fields)
        // 4. Use NLP-based entity recognition for production-grade PHI removal

        return scrubbed;
    }

    public boolean containsPhi(String text) {
        if (text == null) return false;

        return EMAIL_PATTERN.matcher(text).find() ||
                PHONE_PATTERN.matcher(text).find() ||
                NATIONAL_ID_PATTERN.matcher(text).find();
        // NAME_PATTERN removed for the same reasons as above
    }
}