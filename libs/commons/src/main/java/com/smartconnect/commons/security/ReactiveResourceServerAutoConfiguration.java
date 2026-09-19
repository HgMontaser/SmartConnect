package com.smartconnect.commons.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.DispatcherHandler;

import java.util.Arrays;
import java.util.List;

/**
 * Pre-wired OAuth2 resource-server config for api-gateway (WebFlux). Same shape as
 * {@link ServletResourceServerAutoConfiguration} but for the reactive stack: validates the
 * JWT against the configured issuer, maps Keycloak realm roles to authorities, requires
 * authentication on everything except actuator health/info.
 */
@AutoConfiguration
@ConditionalOnClass({ServerHttpSecurity.class, DispatcherHandler.class})
@ConditionalOnProperty(prefix = "smartconnect.commons.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableWebFluxSecurity
public class ReactiveResourceServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReactiveJwtAuthenticationConverterAdapter reactiveJwtAuthenticationConverter() {
        JwtAuthenticationConverter delegate = KeycloakJwtAuthenticationConverterFactory.create();
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }

    /**
     * See {@link MultiIssuerJwtValidatorFactory} for why this exists: JWKS is always fetched
     * via the container-internal issuer (the only address api-gateway can reach Keycloak at),
     * but the resulting decoder must also accept tokens whose {@code iss} claim is the
     * browser-facing, host-published issuer string, since that's what every real login-app /
     * backoffice-app user's token actually carries.
     */
    @Bean
    @ConditionalOnMissingBean
    public ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${smartconnect.commons.security.browser-issuer-uri:http://localhost:8080/realms/smartconnect}") String browserIssuerUri) {
        NimbusReactiveJwtDecoder decoder = (NimbusReactiveJwtDecoder) ReactiveJwtDecoders.fromIssuerLocation(issuerUri);
        decoder.setJwtValidator(MultiIssuerJwtValidatorFactory.create(List.of(issuerUri, browserIssuerUri)));
        return decoder;
    }

    /**
     * Without this, the {@code SecurityWebFilterChain} below authenticates every exchange
     * (via {@code .anyExchange().authenticated()}) including CORS preflight {@code OPTIONS}
     * requests, which never carry a bearer token — so browsers calling api-gateway from
     * login-app/backoffice-app get a {@code 401} on the preflight itself and the real
     * request never fires. Wiring {@code .cors(...)} with this source makes Spring Security
     * short-circuit preflight requests before authorization runs, per
     * {@code CorsUtils.isPreFlightRequest}.
     */
    @Bean
    @ConditionalOnMissingBean(CorsConfigurationSource.class)
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${smartconnect.commons.security.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
            String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    @ConditionalOnMissingBean(SecurityWebFilterChain.class)
    public SecurityWebFilterChain smartConnectSecurityWebFilterChain(
            ServerHttpSecurity http, ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter,
            ReactiveJwtDecoder reactiveJwtDecoder, CorsConfigurationSource corsConfigurationSource) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(authorize -> authorize
                        .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenConverter(new QueryParamAwareBearerTokenConverter())
                        .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
