package com.retail.config;

import com.retail.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapOwnerConfig {
    @Bean
    ApplicationRunner bootstrapOwner(AuthService authService,
                                     @Value("${app.bootstrap-owner.name:Owner}") String name,
                                     @Value("${app.bootstrap-owner.email:}") String email,
                                     @Value("${app.bootstrap-owner.password:}") String password) {
        return args -> {
            if (email.isBlank() != password.isBlank()) {
                throw new IllegalStateException("Set both BOOTSTRAP_OWNER_EMAIL and BOOTSTRAP_OWNER_PASSWORD, or neither");
            }
            if (!password.isBlank() && password.length() < 8) {
                throw new IllegalStateException("BOOTSTRAP_OWNER_PASSWORD must contain at least 8 characters");
            }
            if (!email.isBlank()) {
                authService.createInitialOwner(name, email, password);
            }
        };
    }
}
