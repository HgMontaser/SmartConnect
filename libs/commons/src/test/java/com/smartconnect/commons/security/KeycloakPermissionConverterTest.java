package com.smartconnect.commons.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakPermissionConverterTest {

    private final KeycloakPermissionConverter converter = new KeycloakPermissionConverter();

    @Test
    void mapsGatewayClientRolesToUnprefixedAuthorities() {
        Jwt jwt = token(Map.of(
                "gateway", Map.of("roles", List.of("users:read", "roles:write")),
                "account", Map.of("roles", List.of("manage-account"))));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("users:read", "roles:write");
    }

    @Test
    void returnsEmptyWhenClientMissing() {
        Jwt jwt = token(Map.of("account", Map.of("roles", List.of("manage-account"))));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void factoryCombinesRealmRolesAndPermissions() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "someone")
                .claim("realm_access", Map.of("roles", List.of("admin")))
                .claim("resource_access", Map.of("gateway", Map.of("roles", List.of("users:read"))))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(KeycloakJwtAuthenticationConverterFactory.create().convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "users:read");
    }

    private static Jwt token(Map<String, Object> resourceAccess) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("resource_access", resourceAccess)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
