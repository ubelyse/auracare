package com.mvura.config;

import com.mvura.security.CustomUserDetailsService;
import com.mvura.security.JwtAuthenticationFilter;
import com.mvura.security.RateLimitingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ===== 1. PUBLIC ENDPOINTS - NO AUTHENTICATION REQUIRED =====
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/ussd/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()

                        // ===== 2. SSE ENDPOINTS - PERMIT ALL (token validated in filter) =====
                        .requestMatchers("/api/sse/**").permitAll()

                        // ===== 3. CHECK-IN ENDPOINTS - PUBLIC (authorized via @PreAuthorize) =====
                        .requestMatchers("/api/checkin/**").permitAll()

                        // ===== 4. ADMIN ENDPOINTS - DISTRICT_ADMIN ONLY =====
                        .requestMatchers("/api/admin/**").hasRole("DISTRICT_ADMIN")

                        // ===== 5. FACILITY ENDPOINTS - FACILITY_ADMIN OR DISTRICT_ADMIN =====
                        .requestMatchers("/api/facility/**").hasAnyRole("FACILITY_ADMIN", "DISTRICT_ADMIN")

                        // ===== 6. DOCTOR ENDPOINTS - DOCTOR ONLY =====
                        .requestMatchers("/api/doctor/**").hasRole("DOCTOR")

                        // ===== 7. PATIENT ENDPOINTS - PATIENT OR HIGHER =====
                        .requestMatchers("/api/patient/**").hasAnyRole("PATIENT", "DOCTOR", "STAFF")

                        // ===== 8. BILLING ENDPOINTS - PATIENT, STAFF, OR ADMIN =====
                        .requestMatchers("/api/billing/**").hasAnyRole("PATIENT", "STAFF", "FACILITY_ADMIN", "DISTRICT_ADMIN")

                        // ===== 9. ALL OTHER REQUESTS REQUIRE AUTHENTICATION =====
                        .anyRequest().authenticated()
                )
                .authenticationProvider(daoAuthenticationProvider())
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Add local development origins + your Vercel production domain
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173",
                "http://localhost:3000",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:3000",
                "https://auracare-bydnts8jg-ubelyses-projects.vercel.app"

        ));

        configuration.setAllowedMethods(Arrays.asList(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "OPTIONS",
                "PATCH"
        ));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "X-Request-ID"  // <--- ADD THIS LINE
        ));
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Disposition"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}