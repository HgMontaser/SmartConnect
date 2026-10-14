package com.smartconnect.demoservice;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Starting point: proves routing, authentication and permissions work end to end. */
@RestController
@RequestMapping("/api/demo")
public class StatusController {

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('demo:read')")
    public Map<String, String> status(@AuthenticationPrincipal Jwt caller) {
        return Map.of("service", "demo-service", "status", "UP", "caller", caller.getClaimAsString("preferred_username"));
    }
}
