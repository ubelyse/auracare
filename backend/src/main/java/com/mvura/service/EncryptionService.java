package com.mvura.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final SecretKey secretKey;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void initCheck() {
        if (secretKey != null) {
            byte[] encoded = secretKey.getEncoded();
            log.info("🔒 EncryptionService initialized. Algorithm: {}, Key Encoded Length: {} bytes",
                    secretKey.getAlgorithm(),
                    encoded != null ? encoded.length : "UNKNOWN"
            );
            if (encoded != null && encoded.length != 32) {
                log.warn("⚠️ WARNING: SecretKey length is {} bytes, but AES-256 expects exactly 32 bytes! Check your base64 key string format.", encoded.length);
            }
        } else {
            log.error("❌ CRITICAL: SecretKey injected into EncryptionService is NULL!");
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isEmpty()) {
            return null;
        }

        try {
            log.info("🔑 Attempting to decrypt data of length: {}", encryptedBase64.length());

            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedBase64);
            log.info("🔑 Decoded bytes length: {}", encryptedBytes.length);

            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            String result = new String(plaintext, "UTF-8");

            log.info("✅ Decryption successful. Length: {}", result.length());
            log.info("✅ Decrypted text: '{}'", result);

            return result;

        } catch (AEADBadTagException e) {
            log.error("❌ Decryption AUTH FAILED - wrong key or corrupted data", e);
            try {
                auditService.logSecurityEvent(
                        "DECRYPTION_AUTH_FAILURE",
                        "system",
                        null,
                        null,
                        "AES-GCM authentication tag mismatch on decrypt — key mismatch or data tampering detected"
                );
            } catch (Exception auditEx) {
                log.error("Failed to log decryption auth failure to audit trail", auditEx);
            }
            return "CONSULTATION - Data integrity check failed";

        } catch (IllegalArgumentException e) {
            log.info("🔑 Data is not Base64 (likely plain text), returning as-is");
            return encryptedBase64;

        } catch (Exception e) {
            log.error("❌ Decryption FAILED: {}", e.getMessage(), e);
            return "CONSULTATION - Unable to decrypt";
        }
    }

    public boolean isEncrypted(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        if (value.startsWith("[DECRYPTION_ERROR")) {
            return true;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return decoded.length > GCM_IV_LENGTH;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}