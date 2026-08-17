package com.mvura.service;


import com.mvura.dto.AdminCreateUserRequest;
import com.mvura.dto.RegistrationRequest;
import com.mvura.event.UserRegisteredEvent;
import com.mvura.exception.AccountLockedException;
import com.mvura.exception.InvalidCredentialsException;
import com.mvura.exception.TokenExpiredException;
import com.mvura.model.*;
import com.mvura.repository.LoginAttemptRepository;
import com.mvura.repository.RefreshTokenRepository;
import com.mvura.repository.UserRepository;
import com.mvura.repository.VerificationTokenRepository;
import com.mvura.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_WINDOW_MINUTES = 15;

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final AuditService auditService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final TOTPService totpService;
    private final ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------------
    // LOGIN
    // ------------------------------------------------------------------

    @Transactional
    public Map<String, Object> login(String username, String password, String ipAddress, String userAgent) {
        if (isLockedOut(username)) {
            auditService.logSecurityEvent("LOGIN_BLOCKED_LOCKED_OUT", username, null, ipAddress,
                    "Account temporarily locked after repeated failed attempts");
            throw new AccountLockedException(
                    "Too many failed login attempts. Try again in " + LOCKOUT_WINDOW_MINUTES + " minutes.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

            if (!user.isActive() || !user.isEmailVerified()) {
                throw new InvalidCredentialsException("Account inactive or email not verified.");
            }

            recordAttempt(username, ipAddress, userAgent, true);

            if (!user.isMfaEnabled()) {
                String secret = totpService.generateSecret();
                user.setMfaSecret(secret);
                userRepository.save(user);

                auditService.logSecurityEvent("MFA_ENROLLMENT_REQUIRED", username, user.getId(), ipAddress,
                        "First login — MFA enrollment required before session issued");

                Map<String, Object> response = new HashMap<>();
                response.put("mfaSetupRequired", true);
                response.put("userId", user.getId());
                response.put("qrCodeUrl", totpService.getQrCodeUrl(secret, user.getEmail()));
                return response;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("requiresMfa", true);
            response.put("userId", user.getId());
            auditService.logLoginEvent(user.getId(), username, ipAddress, "MFA_REQUIRED");
            return response;

        } catch (BadCredentialsException e) {
            recordAttempt(username, ipAddress, userAgent, false);
            log.warn("Login failed for user: {} — {}", username, e.getMessage());
            throw new InvalidCredentialsException("Invalid credentials or account not verified");
        }
    }

    @Transactional
    public Map<String, Object> verifyMfa(UUID userId, String totpCode, String ipAddress, String userAgent) {
        User user = userRepository.findByIdWithDepartments(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        if (!totpService.verifyTotp(user.getMfaSecret(), totpCode)) {
            auditService.logSecurityEvent("MFA_FAILED", user.getUsername(), userId, ipAddress, "Invalid TOTP");
            throw new InvalidCredentialsException("Invalid verification code");
        }

        if (!user.isMfaEnabled()) {
            user.setMfaEnabled(true);
            userRepository.save(user);
            auditService.logSecurityEvent("MFA_ENROLLED", user.getUsername(), userId, ipAddress, "MFA enabled for account");
        }

        return completeLogin(user, ipAddress, userAgent);
    }

    private Map<String, Object> completeLogin(User user, String ipAddress, String userAgent) {
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities("ROLE_" + user.getRole().name())
                .build();

        String accessToken = jwtUtils.generateToken(userDetails);
        String refreshToken = generateRefreshToken(user);

        auditService.logLoginEvent(user.getId(), user.getUsername(), ipAddress, "LOGIN_SUCCESS");

        Map<String, Object> userMap = buildUserPayload(user);

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("user", userMap);
        return response;
    }

    // ------------------------------------------------------------------
    // REFRESH TOKEN ROTATION & LOGOUT
    // ------------------------------------------------------------------

    @Transactional
    public Map<String, Object> refreshToken(String currentRefreshToken, String ipAddress) {
        RefreshToken token = refreshTokenRepository.findByToken(currentRefreshToken)
                .orElseThrow(() -> new TokenExpiredException("Invalid refresh token"));

        if (token.isRevoked() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Refresh token expired or revoked");
        }

        token.setRevoked(true);
        refreshTokenRepository.save(token);

        User user = token.getUser();
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities("ROLE_" + user.getRole().name())
                .build();

        String newAccessToken = jwtUtils.generateToken(userDetails);
        String newRefreshToken = generateRefreshToken(user);

        auditService.logSecurityEvent("TOKEN_REFRESHED", user.getUsername(), user.getId(), ipAddress,
                "Access and Refresh tokens rotated");

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", newAccessToken);
        response.put("refreshToken", newRefreshToken);
        return response;
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            auditService.logSecurityEvent("USER_LOGOUT", token.getUser().getUsername(), token.getUser().getId(), null,
                    "Session revoked via logout");
        });
    }

    private String generateRefreshToken(User user) {
        String token = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    // ------------------------------------------------------------------
    // REGISTRATION WITH ASYNC EVENTS
    // ------------------------------------------------------------------

    @Transactional
    public User registerPatient(RegistrationRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(UserRole.PATIENT)
                .active(false)
                .emailVerified(false)
                .mfaEnabled(false)
                .build();

        User savedUser = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = VerificationToken.builder()
                .user(savedUser)
                .token(token)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();
        verificationTokenRepository.save(verificationToken);

        eventPublisher.publishEvent(new UserRegisteredEvent(savedUser, token));

        auditService.logSecurityEvent(
                "USER_REGISTERED",
                savedUser.getUsername(),
                savedUser.getId(),
                null,
                "Self-registered as PATIENT, pending email verification"
        );

        return savedUser;
    }

    // ------------------------------------------------------------------
    // ADMIN USER CREATION (WITH VERIFICATION DISPATCH)
    // ------------------------------------------------------------------

    // ===== FIXED: Added actorUsername and ipAddress parameters =====
    @Transactional
    public User createUserWithRole(AdminCreateUserRequest request, String actorUsername, String ipAddress) {
        if (userRepository.existsByUsername(request.getUsername()))
            throw new IllegalArgumentException("Username already taken");
        if (userRepository.existsByEmail(request.getEmail()))
            throw new IllegalArgumentException("Email already registered");

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(request.getRole())
                .active(true)
                .emailVerified(false)
                .mfaEnabled(false)
                .build();

        User savedUser = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = VerificationToken.builder()
                .user(savedUser)
                .token(token)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();
        verificationTokenRepository.save(verificationToken);

        eventPublisher.publishEvent(new UserRegisteredEvent(savedUser, token));

        auditService.logAction("USER_CREATED_BY_ADMIN", "USER", savedUser.getId().toString(),
                actorUsername, ipAddress, null, Map.of("role", savedUser.getRole().name()));

        return savedUser;
    }

    // ------------------------------------------------------------------
    // EMAIL VERIFICATION & RESEND
    // ------------------------------------------------------------------

    @Transactional
    public boolean verifyEmail(String token) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (verificationToken.isUsed()) {
            throw new IllegalArgumentException("Verification token has already been used");
        }

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Verification token has expired. Please request a new one.");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        user.setActive(true);  // ← ADD THIS LINE
        userRepository.save(user);

        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);

        auditService.logSecurityEvent(
                "EMAIL_VERIFIED",
                user.getUsername(),
                user.getId(),
                null,
                "Email address successfully verified"
        );

        return true;
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found with provided email"));

        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("Email is already verified");
        }

        String newToken = UUID.randomUUID().toString();
        VerificationToken verificationToken = VerificationToken.builder()
                .user(user)
                .token(newToken)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();
        verificationTokenRepository.save(verificationToken);

        eventPublisher.publishEvent(new UserRegisteredEvent(user, newToken));

        auditService.logSecurityEvent(
                "VERIFICATION_EMAIL_RESENT",
                user.getUsername(),
                user.getId(),
                null,
                "New verification token dispatched"
        );
    }

    // ------------------------------------------------------------------
    // LOCKOUT & HELPERS
    // ------------------------------------------------------------------

    private void recordAttempt(String username, String ipAddress, String userAgent, boolean success) {
        LoginAttempt attempt = LoginAttempt.builder()
                .username(username)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(success)
                .build();
        loginAttemptRepository.save(attempt);
    }

    private boolean isLockedOut(String username) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(LOCKOUT_WINDOW_MINUTES);
        long recentFailures = loginAttemptRepository.countFailedAttemptsSince(username, cutoff);
        return recentFailures >= MAX_FAILED_ATTEMPTS;
    }

    private Map<String, Object> buildUserPayload(User user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("firstName", user.getFirstName());
        userMap.put("lastName", user.getLastName());
        userMap.put("email", user.getEmail());
        userMap.put("role", user.getRole().name());
        userMap.put("active", user.isActive());
        userMap.put("emailVerified", user.isEmailVerified());

        if (user.getPrimaryFacility() != null) {
            userMap.put("facilityId", user.getPrimaryFacility().getId());
            userMap.put("facilityName", user.getPrimaryFacility().getName());
        }

        if (user.getRole() == UserRole.DOCTOR) {
            if (user.getPrimaryDepartment() != null) {
                Department dept = user.getPrimaryDepartment();
                userMap.put("departmentId", dept.getId());
                userMap.put("departmentName", dept.getName());
                userMap.put("departmentCode", dept.getCode());

                log.debug("Doctor {} primary department: {} ({})",
                        user.getUsername(), dept.getName(), dept.getId());
            } else {
                log.warn("Doctor {} has no primary department assigned", user.getUsername());

                if (user.getDepartments() != null && !user.getDepartments().isEmpty()) {
                    Department firstDept = user.getDepartments().iterator().next();
                    userMap.put("departmentId", firstDept.getId());
                    userMap.put("departmentName", firstDept.getName());
                    userMap.put("departmentCode", firstDept.getCode());
                    log.info("Doctor {} using fallback department: {}",
                            user.getUsername(), firstDept.getName());
                }
            }
        }

        return userMap;
    }
}