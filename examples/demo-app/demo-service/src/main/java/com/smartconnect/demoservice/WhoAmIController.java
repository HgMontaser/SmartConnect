package com.smartconnect.demoservice;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Shows what this service sees of the caller: identity and the permissions in the token. */
@RestController
@RequestMapping("/api/demo")
public class WhoAmIController {

    @GetMapping("/whoami")
    @PreAuthorize("hasAuthority('demo:read')")
    public Map<String, Object> whoami(JwtAuthenticationToken caller) {
        List<String> permissions = caller.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .sorted()
                .toList();
        return Map.of(
                "username", caller.getToken().getClaimAsString("preferred_username"),
                "permissions", permissions);
    }
}
