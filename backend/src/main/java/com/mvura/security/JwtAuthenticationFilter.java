package com.mvura.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String jwt = null;
        String username = null;

        // ===== LOG ALL REQUESTS =====
        String uri = request.getRequestURI();
        log.info("🔴 JWT Filter processing: {}", uri);

        // ===== GET TOKEN FROM HEADER =====
        String authorizationHeader = request.getHeader("Authorization");
        log.info("🔴 Authorization header: {}", authorizationHeader != null ? "Present" : "Not present");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            log.info("🔴 Token from header: {}", jwt.substring(0, Math.min(20, jwt.length())) + "...");
            try {
                username = jwtUtils.extractUsername(jwt);
                log.info("🔴 Username from header token: {}", username);
            } catch (Exception e) {
                log.warn("Failed to extract username from JWT header: {}", e.getMessage());
            }
        }

        // ===== ALSO CHECK URL PARAMETER (for SSE) =====
        if (jwt == null) {
            String paramToken = request.getParameter("token");
            if (paramToken != null && !paramToken.isEmpty()) {
                jwt = paramToken;
                log.info("🔴 Token from URL parameter: {}", jwt.substring(0, Math.min(20, jwt.length())) + "...");
                try {
                    username = jwtUtils.extractUsername(jwt);
                    log.info("🔴 Username from URL token: {}", username);
                } catch (Exception e) {
                    log.warn("Failed to extract username from JWT param: {}", e.getMessage());
                }
            }
        }

        // ===== IF STILL NO TOKEN, CHECK COOKIE OR OTHER SOURCES =====
        if (jwt == null) {
            log.warn("❌ No token found for request: {}", uri);
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                log.info("🔴 Loading user details for: {}", username);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                log.info("🔴 User authorities: {}", userDetails.getAuthorities());

                if (jwtUtils.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("✅ Authentication set for user: {} with role: {}", username, userDetails.getAuthorities());
                } else {
                    log.warn("❌ Token validation failed for user: {}", username);
                }
            } catch (Exception e) {
                log.warn("Authentication failed: {}", e.getMessage());
            }
        } else if (username == null) {
            log.warn("❌ Username is null for request: {}", uri);
        }

        chain.doFilter(request, response);
    }
}