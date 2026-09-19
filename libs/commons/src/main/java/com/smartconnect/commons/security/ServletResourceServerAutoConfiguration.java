package com.smartconnect.commons.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.DispatcherServlet;

import java.util.List;

/**
 * Pre-wired OAuth2 resource-server config for any SmartConnect service on the servlet
 * (Spring MVC) stack: validates the JWT against the issuer configured via the standard
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} property (pulled from Vault
 * at boot by each service), maps Keycloak realm roles to authorities, requires
 * authentication on everything except actuator health/info, stateless sessions.
 *
 * <p>A service only needs the starter dependency + its own issuer URI - no security
 * boilerplate rewritten per service. Back off entirely (via
 * {@code smartconnect.commons.security.enabled=false}) or override any bean if a service
 * needs custom rules.
 */
@AutoConfiguration
@ConditionalOnClass({HttpSecurity.class, DispatcherServlet.class})
@ConditionalOnProperty(prefix = "smartconnect.commons.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableWebSecurity
public class ServletResourceServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        return KeycloakJwtAuthenticationConverterFactory.create();
    }

    @Bean
    @ConditionalOnMissingBean
    public BearerTokenResolver bearerTokenResolver() {
        return new QueryParamAwareBearerTokenResolver();
    }

    /**
     * See {@link MultiIssuerJwtValidatorFactory} for why this exists: JWKS is always fetched
     * via the container-internal issuer (the only address this service can reach Keycloak at),
     * but the resulting decoder must also accept tokens whose {@code iss} claim is the
     * browser-facing, host-published issuer string, since that's what every real login-app /
     * backoffice-app user's token actually carries.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${smartconnect.commons.security.browser-issuer-uri:http://localhost:8080/realms/smartconnect}") String browserIssuerUri) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
        decoder.setJwtValidator(MultiIssuerJwtValidatorFactory.create(List.of(issuerUri, browserIssuerUri)));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain smartConnectSecurityFilterChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
            BearerTokenResolver bearerTokenResolver, JwtDecoder jwtDecoder) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
