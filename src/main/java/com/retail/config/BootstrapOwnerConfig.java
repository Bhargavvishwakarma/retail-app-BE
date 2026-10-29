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
                                     @Value("${app.bootstrap-owner.email:}") String email) {
        return args -> {
            if (!email.isBlank()) {
                authService.bootstrapInitialOwner(name, email);
            }
        };
    }
}
