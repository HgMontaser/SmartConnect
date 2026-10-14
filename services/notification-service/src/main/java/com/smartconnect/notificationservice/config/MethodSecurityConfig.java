package com.smartconnect.notificationservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Turns on the {@code @PreAuthorize} checks on REST endpoints (the event log). */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
