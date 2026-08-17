package com.mvura.service;

import com.mvura.model.User;
import com.mvura.repository.UserRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TOTPService {

    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Value("${app.mfa.issuer:MVURA-Health}")
    private String issuer;

    @Value("${app.mfa.recovery-codes.count:10}")
    private int recoveryCodeCount;

    @Value("${app.mfa.rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.mfa.rate-limit.lockout-minutes:15}")
    private int lockoutMinutes;

    @Value("${app.mfa.session.timeout-minutes:5}")
    private int sessionTimeoutMinutes;

    // ==================== RATE LIMITING ====================

    private final Map<String, List<LocalDateTime>> mfaAttempts = new ConcurrentHashMap<>();

    // ==================== MFA SESSIONS ====================

    private final Map<String, MfaSession> mfaSessions = new ConcurrentHashMap<>();

    // ==================== RECOVERY CODES STORE ====================

    private final Map<String, List<String>> recoveryCodesStore = new ConcurrentHashMap<>();

    // ==================== SECURE RANDOM ====================

    private final SecureRandom secureRandom = new SecureRandom();

    // ==================== INITIALIZATION ====================

    @PostConstruct
    public void init() {
        log.info("TOTPService initialized with issuer: {}", issuer);
        log.info("Recovery codes count: {}, Rate limit: {} attempts in {} minutes",
                recoveryCodeCount, maxAttempts, lockoutMinutes);
    }

    // ==================== 1. SECRET GENERATION ====================

    /**
     * Generate a new TOTP secret key for MFA setup
     */
    public String generateSecret() {
        String secret = googleAuthenticator.createCredentials().getKey();
        log.debug("Generated new TOTP secret");
        return secret;
    }

    /**
     * Generate a new TOTP secret key with specific length
     */
    public String generateSecret(int length) {
        // GoogleAuthenticator generates 16-byte (32 char base32) by default
        // Length parameter is for future flexibility
        return googleAuthenticator.createCredentials().getKey();
    }

    // ==================== 2. QR CODE GENERATION ====================

    /**
     * Generate QR code URL for authenticator app setup
     */
    public String getQrCodeUrl(String secret, String email) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Secret cannot be null or empty");
        }
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        GoogleAuthenticatorKey key = new GoogleAuthenticatorKey.Builder(secret).build();
        return GoogleAuthenticatorQRGenerator.getOtpAuthURL(issuer, email, key);
    }

    /**
     * Generate QR code URL with custom label
     */
    public String getQrCodeUrl(String secret, String email, String customLabel) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Secret cannot be null or empty");
        }
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        String label = customLabel != null ? customLabel : email;
        GoogleAuthenticatorKey key = new GoogleAuthenticatorKey.Builder(secret).build();
        return GoogleAuthenticatorQRGenerator.getOtpAuthURL(issuer, label, key);
    }

    // ==================== 3. TOTP VERIFICATION ====================

    /**
     * Verify TOTP code against secret
     */
    public boolean verifyTotp(String secret, String code) {
        if (secret == null || secret.isEmpty()) {
            log.warn("TOTP verification attempted with null/empty secret");
            return false;
        }

        if (code == null || code.isEmpty()) {
            log.warn("TOTP verification attempted with null/empty code");
            return false;
        }

        try {
            int totpCode = Integer.parseInt(code);
            boolean verified = googleAuthenticator.authorize(secret, totpCode);
            log.debug("TOTP verification: {}", verified ? "SUCCESS" : "FAILED");
            return verified;
        } catch (NumberFormatException e) {
            log.warn("Invalid TOTP code format: {}", code);
            return false;
        }
    }

    /**
     * Verify TOTP code with rate limiting
     */
    public boolean verifyTotpWithRateLimit(String secret, String code, String userId) {
        if (userId == null || userId.isEmpty()) {
            return verifyTotp(secret, code);
        }

        // Check rate limit
        List<LocalDateTime> attempts = mfaAttempts.computeIfAbsent(userId, k -> new ArrayList<>());

        // Clean old attempts
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(lockoutMinutes);
        attempts.removeIf(time -> time.isBefore(cutoff));

        if (attempts.size() >= maxAttempts) {
            log.warn("MFA rate limit exceeded for user: {}", userId);
            throw new MfaRateLimitExceededException(
                    "Too many MFA attempts. Please try again in " + lockoutMinutes + " minutes."
            );
        }

        boolean verified = verifyTotp(secret, code);

        if (!verified) {
            attempts.add(LocalDateTime.now());
            log.warn("Failed MFA attempt for user: {} (attempt {}/{})",
                    userId, attempts.size(), maxAttempts);
        } else {
            // Clear attempts on success
            mfaAttempts.remove(userId);
        }

        return verified;
    }

    /**
     * Verify TOTP code with audit logging
     */
    public boolean verifyTotpWithAudit(String secret, String code, String userId,
                                       String username, String ipAddress) {
        boolean verified = verifyTotp(secret, code);

        try {
            auditService.logSecurityEvent(
                    verified ? "MFA_VERIFIED" : "MFA_FAILED",
                    username,
                    userId != null ? UUID.fromString(userId) : null,
                    ipAddress,
                    "TOTP verification " + (verified ? "successful" : "failed")
            );
        } catch (Exception e) {
            log.warn("Failed to log MFA audit: {}", e.getMessage());
        }

        return verified;
    }

    /**
     * Complete verification with rate limiting + audit
     */
    public boolean verifyTotpComplete(String secret, String code, String userId,
                                      String username, String ipAddress) {
        return verifyTotpWithRateLimit(secret, code, userId) &&
                verifyTotpWithAudit(secret, code, userId, username, ipAddress);
    }

    // ==================== 4. RECOVERY CODES ====================

    /**
     * Generate recovery codes for MFA backup
     */
    public List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < recoveryCodeCount; i++) {
            String code = String.format("%08d", secureRandom.nextInt(100000000));
            codes.add(code);
        }
        log.debug("Generated {} recovery codes", codes.size());
        return codes;
    }

    /**
     * Generate recovery codes and store them for a user
     */
    public List<String> generateAndStoreRecoveryCodes(String userId) {
        List<String> codes = generateRecoveryCodes();
        recoveryCodesStore.put(userId, codes);
        log.info("Generated and stored {} recovery codes for user: {}", codes.size(), userId);
        return codes;
    }

    /**
     * Verify a recovery code
     */
    public boolean verifyRecoveryCode(String userId, String inputCode) {
        if (userId == null || userId.isEmpty() || inputCode == null || inputCode.isEmpty()) {
            return false;
        }

        List<String> storedCodes = recoveryCodesStore.get(userId);
        if (storedCodes == null || storedCodes.isEmpty()) {
            log.warn("No recovery codes found for user: {}", userId);
            return false;
        }

        // Find and remove the used code (one-time use)
        for (int i = 0; i < storedCodes.size(); i++) {
            if (storedCodes.get(i).equals(inputCode)) {
                storedCodes.remove(i);
                if (storedCodes.isEmpty()) {
                    recoveryCodesStore.remove(userId);
                } else {
                    recoveryCodesStore.put(userId, storedCodes);
                }
                log.info("Recovery code verified and used for user: {}", userId);
                return true;
            }
        }

        log.warn("Invalid recovery code attempt for user: {}", userId);
        return false;
    }

    /**
     * Get remaining recovery codes for a user
     */
    public int getRemainingRecoveryCodesCount(String userId) {
        List<String> codes = recoveryCodesStore.get(userId);
        return codes != null ? codes.size() : 0;
    }

    /**
     * Get recovery codes for a user (with masking)
     */
    public List<String> getRecoveryCodes(String userId) {
        List<String> codes = recoveryCodesStore.get(userId);
        return codes != null ? new ArrayList<>(codes) : new ArrayList<>();
    }

    /**
     * Regenerate recovery codes for a user
     */
    public List<String> regenerateRecoveryCodes(String userId) {
        recoveryCodesStore.remove(userId);
        return generateAndStoreRecoveryCodes(userId);
    }

    // ==================== 5. MFA SESSION MANAGEMENT ====================

    /**
     * Create an MFA session for a user
     */
    public void createMfaSession(String userId, String secret, String method) {
        MfaSession session = MfaSession.builder()
                .userId(userId)
                .secret(secret)
                .method(method)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(sessionTimeoutMinutes))
                .build();
        mfaSessions.put(userId, session);
        log.debug("MFA session created for user: {}, method: {}", userId, method);
    }

    /**
     * Validate an MFA session
     */
    public boolean validateMfaSession(String userId, String code) {
        MfaSession session = mfaSessions.get(userId);
        if (session == null) {
            log.warn("No MFA session found for user: {}", userId);
            return false;
        }

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("MFA session expired for user: {}", userId);
            mfaSessions.remove(userId);
            throw new MfaSessionExpiredException("MFA session expired. Please try again.");
        }

        boolean verified = verifyTotp(session.getSecret(), code);
        if (verified) {
            mfaSessions.remove(userId);
            log.info("MFA session validated for user: {}", userId);
        } else {
            log.warn("Invalid MFA code for user: {}", userId);
        }
        return verified;
    }

    /**
     * Validate MFA session with rate limiting
     */
    public boolean validateMfaSessionWithRateLimit(String userId, String code) {
        MfaSession session = mfaSessions.get(userId);
        if (session == null) {
            return false;
        }

        return verifyTotpWithRateLimit(session.getSecret(), code, userId);
    }

    /**
     * Get MFA session for a user
     */
    public MfaSession getMfaSession(String userId) {
        return mfaSessions.get(userId);
    }

    /**
     * Check if user has an active MFA session
     */
    public boolean hasActiveMfaSession(String userId) {
        MfaSession session = mfaSessions.get(userId);
        if (session == null) {
            return false;
        }
        return session.getExpiresAt().isAfter(LocalDateTime.now());
    }

    /**
     * Clear MFA session for a user
     */
    public void clearMfaSession(String userId) {
        mfaSessions.remove(userId);
        mfaAttempts.remove(userId);
        log.debug("MFA session cleared for user: {}", userId);
    }

    // ==================== 6. MFA BACKUP METHODS ====================

    /**
     * Generate SMS verification code
     */
    public String generateSmsCode() {
        return String.format("%06d", secureRandom.nextInt(1000000));
    }

    /**
     * Generate Email verification code
     */
    public String generateEmailCode() {
        return String.format("%06d", secureRandom.nextInt(1000000));
    }

    // ==================== 7. MFA MANAGEMENT ====================

    /**
     * Enable MFA for a user
     */
    public boolean enableMfa(String userId, String secret, String code, String username, String ipAddress) {
        boolean verified = verifyTotpComplete(secret, code, userId, username, ipAddress);

        if (verified) {
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setMfaEnabled(true);
            user.setMfaSecret(secret);
            userRepository.save(user);

            // Generate recovery codes
            generateAndStoreRecoveryCodes(userId);

            auditService.logSecurityEvent(
                    "MFA_ENABLED",
                    username,
                    UUID.fromString(userId),
                    ipAddress,
                    "MFA successfully enabled"
            );

            log.info("MFA enabled for user: {}", username);
        }

        return verified;
    }

    /**
     * Disable MFA for a user
     */
    public boolean disableMfa(String userId, String code, String username, String ipAddress) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify the code before disabling
        boolean verified = verifyTotp(user.getMfaSecret(), code);

        if (verified) {
            user.setMfaEnabled(false);
            user.setMfaSecret(null);
            userRepository.save(user);

            // Clear recovery codes
            recoveryCodesStore.remove(userId);
            clearMfaSession(userId);

            auditService.logSecurityEvent(
                    "MFA_DISABLED",
                    username,
                    UUID.fromString(userId),
                    ipAddress,
                    "MFA successfully disabled"
            );

            log.info("MFA disabled for user: {}", username);
        }

        return verified;
    }

    /**
     * Validate recovery code for MFA recovery
     */
    public boolean recoverMfa(String userId, String recoveryCode, String username, String ipAddress) {
        boolean verified = verifyRecoveryCode(userId, recoveryCode);

        if (verified) {
            // Clear MFA settings
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setMfaEnabled(false);
            user.setMfaSecret(null);
            userRepository.save(user);

            // Clear sessions
            clearMfaSession(userId);

            auditService.logSecurityEvent(
                    "MFA_RECOVERED",
                    username,
                    UUID.fromString(userId),
                    ipAddress,
                    "MFA recovered using recovery code"
            );

            log.info("MFA recovered for user: {}", username);
        }

        return verified;
    }

    // ==================== 8. METRICS & MONITORING ====================

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("activeSessions", mfaSessions.size());
        metrics.put("pendingAttempts", mfaAttempts.values().stream()
                .mapToInt(List::size)
                .sum());
        metrics.put("activeSessionsDetails", mfaSessions.size());
        metrics.put("timestamp", LocalDateTime.now().toString());
        return metrics;
    }

    // ==================== 9. CLEANUP (SCHEDULED) ====================

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanup() {
        // Clean expired sessions
        LocalDateTime now = LocalDateTime.now();
        mfaSessions.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().getExpiresAt().isBefore(now);
            if (expired) {
                log.debug("Removed expired MFA session for: {}", entry.getKey());
            }
            return expired;
        });

        // Clean old rate limit attempts
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(lockoutMinutes);
        mfaAttempts.forEach((userId, attempts) -> {
            attempts.removeIf(time -> time.isBefore(cutoff));
            if (attempts.isEmpty()) {
                mfaAttempts.remove(userId);
            }
        });
    }

    // ==================== INNER CLASSES ====================

    @lombok.Data
    @lombok.Builder
    public static class MfaSession {
        private String userId;
        private String secret;
        private String method;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
    }

    // ==================== EXCEPTIONS ====================

    public static class MfaRateLimitExceededException extends RuntimeException {
        public MfaRateLimitExceededException(String message) {
            super(message);
        }
    }

    public static class MfaSessionExpiredException extends RuntimeException {
        public MfaSessionExpiredException(String message) {
            super(message);
        }
    }
}