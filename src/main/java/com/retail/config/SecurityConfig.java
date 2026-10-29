package com.retail.config;

import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.exception.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                            ObjectMapper objectMapper) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(request, response, objectMapper, HttpStatus.UNAUTHORIZED,
                                        "AUTHENTICATION_REQUIRED", "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(request, response, objectMapper, HttpStatus.FORBIDDEN,
                                        "ACCESS_DENIED", "You do not have permission to access this resource")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/google").permitAll()
                        .requestMatchers(HttpMethod.POST, "/internal/v1/ai-data/query").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/chat").hasAnyRole("OWNER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.GET, "/api/chat/models").hasAnyRole("OWNER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.GET, "/api/chat/conversations", "/api/chat/conversations/*")
                                .hasAnyRole("OWNER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.GET, "/api/auth/users").hasRole("OWNER")
                        .requestMatchers(HttpMethod.PATCH, "/api/auth/users/*/status", "/api/auth/users/*/role").hasRole("OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/products/low-stock").hasAnyRole("OWNER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/*",
                                "/api/categories", "/api/customers", "/api/customers/*").hasAnyRole("OWNER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.POST, "/api/products/**")
                                .hasRole("OWNER")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**", "/api/categories/**")
                                .hasRole("OWNER")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**", "/api/categories/**", "/api/customers/**")
                                .hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/categories/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/customers/**", "/api/sales/**").hasAnyRole("OWNER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/operational").hasAnyRole("OWNER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.GET, "/api/sales/**", "/api/dashboard/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/auth/receptionists").hasRole("OWNER")
                        .requestMatchers("/api/auth/me", "/api/auth/logout", "/api/auth/me/preferences")
                                .hasAnyRole("OWNER", "RECEPTIONIST")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void writeError(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response,
                            ObjectMapper objectMapper, HttpStatus status, String code, String detail)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.create(status, code, detail, request));
    }
}
