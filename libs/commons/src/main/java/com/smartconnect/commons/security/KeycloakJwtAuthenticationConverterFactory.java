package com.smartconnect.commons.security;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/** Builds the one {@link JwtAuthenticationConverter} shape every SmartConnect service uses. */
public final class KeycloakJwtAuthenticationConverterFactory {

    private KeycloakJwtAuthenticationConverterFactory() {
    }

    public static JwtAuthenticationConverter create() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
