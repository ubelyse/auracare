package com.mvura.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvura.repository.LoginAttemptRepository;
import com.mvura.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final LoginAttemptRepository loginAttemptRepository;
    private final AuditService auditService;

    @Value("${security.rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${security.rate-limit.window-minutes:15}")
    private int windowMinutes;

    @Value("${security.rate-limit.whitelist-ips:}")
    private String whitelistIps;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // Only apply to login endpoints
        if (!request.getRequestURI().equals("/api/auth/login")) {
            chain.doFilter(request, response);
            return;
        }

        String ipAddress = getClientIpAddress(request);

        // Skip rate limiting for whitelisted IPs
        if (isWhitelisted(ipAddress)) {
            log.info("Skipping rate limit for whitelisted IP: {}", ipAddress);
            chain.doFilter(request, response);
            return;
        }

        // Wrap the request to allow reading body multiple times
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

        // Extract username WITHOUT consuming the input stream
        String username = extractUsernameFromWrapper(wrappedRequest);

        if (username != null && ipAddress != null) {
            LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);

            // Check username-based limits
            long userFailures = loginAttemptRepository.countFailedAttemptsSince(username, since);
            if (userFailures >= maxAttempts) {
                log.warn("Rate limit exceeded for username: {}, IP: {}, attempts: {}", username, ipAddress, userFailures);

                auditService.logSecurityEvent(
                        "RATE_LIMIT_EXCEEDED",
                        username,
                        null,
                        ipAddress,
                        "User attempts: " + userFailures
                );

                returnRateLimitResponse(response, username, ipAddress, "Too many login attempts for user");
                return;
            }

            // Check IP-based limits (distributed attacks)
            long ipFailures = loginAttemptRepository.countFailedAttemptsByIpSince(ipAddress, since);
            if (ipFailures >= maxAttempts * 2) {
                log.warn("IP rate limit exceeded: {}, attempts: {}", ipAddress, ipFailures);

                auditService.logSecurityEvent(
                        "IP_RATE_LIMIT_EXCEEDED",
                        username,
                        null,
                        ipAddress,
                        "IP attempts: " + ipFailures
                );

                returnRateLimitResponse(response, username, ipAddress, "Too many login attempts from this IP");
                return;
            }
        }

        // Continue with the wrapped request so Spring can read the body
        chain.doFilter(wrappedRequest, response);
    }

    /**
     * Extract username WITHOUT consuming the input stream.
     * Uses the cached content from ContentCachingRequestWrapper.
     */
    private String extractUsernameFromWrapper(ContentCachingRequestWrapper request) {
        // Try from parameters (form data)
        String username = request.getParameter("username");
        if (username != null && !username.isEmpty()) {
            return username;
        }

        // Try from JSON body using the cached content
        try {
            byte[] content = request.getContentAsByteArray();
            if (content != null && content.length > 0) {
                String body = new String(content, request.getCharacterEncoding());
                if (body != null && !body.isEmpty()) {
                    JsonNode node = objectMapper.readTree(body);
                    if (node.has("username")) {
                        return node.get("username").asText();
                    }
                    if (node.has("email")) {
                        return node.get("email").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract username from request body: {}", e.getMessage());
        }

        return null;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip.split(",")[0].trim() : null;
    }

    private boolean isWhitelisted(String ipAddress) {
        if (whitelistIps == null || whitelistIps.isEmpty() || ipAddress == null) {
            return false;
        }
        return Arrays.stream(whitelistIps.split(","))
                .map(String::trim)
                .anyMatch(ip -> ip.equals(ipAddress) || ip.equals("127.0.0.1") || ip.equals("::1"));
    }

    private void returnRateLimitResponse(HttpServletResponse response, String username, String ipAddress, String reason)
            throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"error\": \"Rate limit exceeded\", \"message\": \"%s\", \"retryAfter\": %d}",
                reason, windowMinutes * 60
        ));
        response.getWriter().flush();
    }
}