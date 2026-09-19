package com.smartconnect.commons.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Resolves the bearer token from the standard {@code Authorization: Bearer <token>} header,
 * same as Spring Security's default resolver. Falls back to an {@code access_token} query
 * parameter, but ONLY on {@code /ws/**} paths - browsers' native WebSocket API cannot set
 * custom headers on the upgrade handshake, so the websocket bridge to notification-service
 * (backoffice-app's live-update feed) has no other way to carry a token. Every other route
 * keeps header-only auth: query-param tokens are more exposure-prone (access logs, browser
 * history, intermediate proxies) so this fallback is deliberately scoped as narrowly as
 * possible rather than applied gateway-wide.
 */
public class QueryParamAwareBearerTokenConverter implements ServerAuthenticationConverter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String WS_PATH_PREFIX = "/ws/";
    private static final String TOKEN_QUERY_PARAM = "access_token";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String headerToken = resolveHeaderToken(exchange);
        if (headerToken != null) {
            return Mono.just(new BearerTokenAuthenticationToken(headerToken));
        }
        if (exchange.getRequest().getPath().value().startsWith(WS_PATH_PREFIX)) {
            String queryToken = exchange.getRequest().getQueryParams().getFirst(TOKEN_QUERY_PARAM);
            if (queryToken != null && !queryToken.isBlank()) {
                return Mono.just(new BearerTokenAuthenticationToken(queryToken));
            }
        }
        return Mono.empty();
    }

    private String resolveHeaderToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
