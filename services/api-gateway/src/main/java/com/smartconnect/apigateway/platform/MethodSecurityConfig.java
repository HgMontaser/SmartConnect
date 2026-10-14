package com.smartconnect.apigateway.platform;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;

/** Turns on {@code @PreAuthorize} for the gateway's own endpoints (PlatformController). */
@Configuration
@EnableReactiveMethodSecurity
public class MethodSecurityConfig {
}
