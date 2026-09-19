package com.smartconnect.commons.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.net.URL;
import java.util.List;

/**
 * SmartConnect's local/dev docker-compose topology has one real wrinkle: Keycloak derives the
 * {@code iss} claim on every issued JWT from the Host header of whatever request reached it
 * (dynamic hostname, {@code KC_HOSTNAME_STRICT=false}). A browser talks to Keycloak via the
 * host-published port ({@code http://localhost:8080/realms/smartconnect}), so tokens minted
 * for login-app/backoffice-app carry that as {@code iss}. But every resource server discovers
 * its JWKS via the container-internal hostname ({@code http://keycloak:8080/realms/smartconnect}
 * - the only address containers can actually reach Keycloak at), and Spring Security's default
 * single-issuer validator would then reject every browser-obtained token as "invalid issuer"
 * even though the signature is perfectly valid (same server, same signing key either way).
 *
 * <p>This validator accepts a small explicit set of equivalent issuer strings for the one real
 * Keycloak instance this system trusts, rather than either disabling issuer validation
 * entirely or trying to force a single hostname on both browser and container traffic (which
 * would require host-file/DNS changes outside this codebase's control).
 */
public final class MultiIssuerJwtValidatorFactory {

    private MultiIssuerJwtValidatorFactory() {
    }

    public static OAuth2TokenValidator<Jwt> create(List<String> acceptedIssuers) {
        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> {
            URL issuer = jwt.getIssuer();
            String issuerValue = issuer != null ? issuer.toString() : null;
            if (issuerValue != null && acceptedIssuers.contains(issuerValue)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_issuer",
                    "The iss claim [" + issuerValue + "] is not one of the accepted SmartConnect issuers " + acceptedIssuers,
                    null);
            return OAuth2TokenValidatorResult.failure(error);
        };
        return new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), issuerValidator);
    }
}
