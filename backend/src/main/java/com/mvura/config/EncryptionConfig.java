package com.mvura.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Configuration
@Getter
public class EncryptionConfig {

    @Value("${app.encryption.aes-key:}")
    private String base64Key;

    @Value("${app.encryption.use-vault:false}")
    private boolean useVault;

    @Bean
    public SecretKey encryptionKey() {
        if (base64Key != null && !base64Key.isEmpty()) {
            // Use provided key from environment/vault
            byte[] decodedKey = Base64.getDecoder().decode(base64Key);
            return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
        } else {
            // Generate a secure key for development
            try {
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256, SecureRandom.getInstanceStrong());
                SecretKey key = keyGen.generateKey();

                // Log the key for development (remove in production!)
                String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
                System.out.println("=== DEVELOPMENT ENCRYPTION KEY ===");
                System.out.println("Add to application.yml: app.encryption.aes-key: " + encodedKey);
                System.out.println("==================================");

                return key;
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate encryption key", e);
            }
        }
    }
}