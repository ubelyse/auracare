package com.mvura.controller;

import com.mvura.dto.LoginRequest;
import com.mvura.dto.RegistrationRequest;
import com.mvura.model.User;
import com.mvura.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {

        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        Map<String, Object> response = authService.login(
                loginRequest.getUsername(), loginRequest.getPassword(), ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-mfa")
    public ResponseEntity<?> verifyMfa(
            @RequestParam UUID userId,
            @RequestParam String totpCode,
            HttpServletRequest request) {

        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        Map<String, Object> response = authService.verifyMfa(userId, totpCode, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest request) {
        User user = authService.registerPatient(request);
        return ResponseEntity.ok(Map.of(
                "message", "Registration successful. Please check your email for verification.",
                "userId", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail()
        ));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        try {
            boolean verified = authService.verifyEmail(token);
            if (verified) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Email verified successfully! You can now log in."
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Verification failed. Please try again."
                ));
            }
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestParam String email) {
        try {
            authService.resendVerificationEmail(email);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Verification email sent. Please check your inbox."
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestParam String refreshToken, HttpServletRequest request) {
        String ipAddress = getClientIpAddress(request);
        Map<String, Object> response = authService.refreshToken(refreshToken, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // NOTE: the old manual /setup-mfa endpoint is removed. MFA enrollment
    // now happens automatically inside AuthService.login() on a user's
    // first successful password check — see login()'s mfaSetupRequired
    // branch. There is no longer a path where MFA can be skipped.

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        return ResponseEntity.ok(Map.of(
                "message", "User details endpoint"
        ));
    }

    /**
     * NOTE — known limitation, not fully fixed here: this trusts
     * X-Forwarded-For / Proxy-Client-IP / WL-Proxy-Client-IP headers
     * verbatim. Those headers are fully attacker-controllable unless your
     * infrastructure strips and re-sets them at a trusted edge proxy
     * (nginx/load balancer). As written, anyone can forge these headers to
     * poison the IP address recorded in your audit log and login attempt
     * history — which undermines the "non-repudiable audit record" claim.
     *
     * The correct fix isn't more manual header parsing — it's telling
     * Spring which proxies to trust and letting it resolve the real client
     * IP, e.g. in application.properties:
     *   server.forward-headers-strategy=native
     * paired with your reverse proxy actually being the only path to this
     * app (no direct internet access to port 8080). Worth doing before
     * you rely on audit log IPs for anything in your thesis.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip.split(",")[0].trim() : null;
    }
}