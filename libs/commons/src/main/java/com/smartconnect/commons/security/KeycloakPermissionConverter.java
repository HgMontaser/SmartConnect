package com.smartconnect.commons.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps the caller's permissions (Keycloak client roles of {@link SmartConnectPermissions#CLIENT_ID},
 * found under {@code resource_access.<client>.roles}) onto unprefixed {@link GrantedAuthority}s,
 * so endpoints guard with {@code hasAuthority('users:read')}.
 */
public class KeycloakPermissionConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    private static final String ROLES_CLAIM = "roles";

    private final String clientId;

    public KeycloakPermissionConverter() {
        this(SmartConnectPermissions.CLIENT_ID);
    }

    public KeycloakPermissionConverter(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS_CLAIM);
        if (resourceAccess == null || !(resourceAccess.get(clientId) instanceof Map<?, ?> client)) {
            return Collections.emptyList();
        }
        if (!(client.get(ROLES_CLAIM) instanceof List<?> roles)) {
            return Collections.emptyList();
        }
        return roles.stream()
                .map(String::valueOf)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }
}
