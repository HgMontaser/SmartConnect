package com.smartconnect.commons.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps Keycloak realm roles (the {@code realm_access.roles} claim) onto Spring
 * {@link GrantedAuthority}s, prefixed {@code ROLE_} so they work with both
 * {@code hasRole(...)} and {@code @PreAuthorize}. Shared by the servlet and reactive
 * resource-server auto-configs so role mapping can never drift between services.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return Collections.emptyList();
        }
        Object rolesClaim = realmAccess.get(ROLES_CLAIM);
        if (!(rolesClaim instanceof List<?> roles)) {
            return Collections.emptyList();
        }
        return roles.stream()
                .map(String::valueOf)
                .map(role -> ROLE_PREFIX + role.toUpperCase(Locale.ROOT))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }
}
