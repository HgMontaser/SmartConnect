package com.smartconnect.userservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Turns on the {@code @PreAuthorize} permission checks on every controller. */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
