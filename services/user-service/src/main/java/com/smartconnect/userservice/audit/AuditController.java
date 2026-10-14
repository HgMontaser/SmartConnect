package com.smartconnect.userservice.audit;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final KeycloakAuditService audit;

    public AuditController(KeycloakAuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/admin-changes")
    @PreAuthorize("hasAuthority('audit:read')")
    public List<KeycloakAuditService.AdminChange> adminChanges(@RequestParam(defaultValue = "100") int max) {
        return audit.adminChanges(max);
    }

    @GetMapping("/sign-ins")
    @PreAuthorize("hasAuthority('audit:read')")
    public List<KeycloakAuditService.SignIn> signIns(@RequestParam(defaultValue = "100") int max) {
        return audit.signIns(max);
    }
}
