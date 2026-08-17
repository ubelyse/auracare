package com.mvura.converter;

import com.mvura.service.EncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Converter
@Component
@Slf4j
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static EncryptionService encryptionService;

    @Autowired
    public void setEncryptionService(EncryptionService service) {
        encryptionService = service;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }

        log.info("🔑 Converter reading data: HAS_DATA (length: {})", dbData.length());

        try {
            // Try to decode as Base64
            byte[] decoded = Base64.getDecoder().decode(dbData);

            // Valid Base64 - decrypt it
            String result = encryptionService.decrypt(dbData);

            // Check if decryption returned an error
            if (result != null && (result.startsWith("CONSULTATION -") || result.startsWith("[DECRYPTION_ERROR"))) {
                log.warn("🔑 Decryption returned error: {}", result);
                // Return the encrypted data as-is (handle old plain text data)
                return dbData;
            }

            log.info("🔑 Converter result: {}", result != null ? result.substring(0, Math.min(result.length(), 50)) : "NULL");
            return result;

        } catch (IllegalArgumentException e) {
            // Not valid Base64 - plain text
            log.info("🔑 Data is NOT Base64 (plain text), returning as-is");
            return dbData;
        }
    }
}