package com.smartconnect.commons.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Builds the one {@link JwtAuthenticationConverter} shape every SmartConnect service uses:
 * realm roles as {@code ROLE_*} authorities plus permissions as plain authorities.
 */
public final class KeycloakJwtAuthenticationConverterFactory {

    private KeycloakJwtAuthenticationConverterFactory() {
    }

    public static JwtAuthenticationConverter create() {
        KeycloakRealmRoleConverter realmRoles = new KeycloakRealmRoleConverter();
        KeycloakPermissionConverter permissions = new KeycloakPermissionConverter();

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(realmRoles.convert(jwt));
            authorities.addAll(permissions.convert(jwt));
            return authorities;
        });
        return converter;
    }
}
