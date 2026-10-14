package com.smartconnect.userservice.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the single {@link Keycloak} admin client this service uses as its "database driver" -
 * Keycloak itself is the source of truth for users, this service never persists anything of
 * its own. Authenticates as the {@code user-service} client's service account (client
 * credentials, in the smartconnect realm only - never the master admin) using the secret
 * pulled from Vault at boot; fails fast at startup if the
 * credentials or realm target are wrong (first admin call throws), matching the rest of the
 * fleet's fail-fast-on-dependency posture.
 */
@Configuration
@EnableConfigurationProperties(KeycloakAdminProperties.class)
public class KeycloakAdminConfig {

    @Bean
    public Keycloak keycloakAdminClient(KeycloakAdminProperties properties) {
        return KeycloakBuilder.builder()
                .serverUrl(properties.getAdmin().getServerUrl())
                .realm(properties.getAdmin().getRealm())
                .clientId(properties.getAdmin().getClientId())
                .clientSecret(properties.getAdmin().getClientSecret())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }
}
