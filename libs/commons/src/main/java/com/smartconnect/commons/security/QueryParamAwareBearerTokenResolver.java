package com.smartconnect.commons.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Servlet-stack counterpart to {@link QueryParamAwareBearerTokenConverter}: resolves the
 * bearer token from the standard {@code Authorization} header first, falling back to an
 * {@code access_token} query parameter only for {@code /ws/**} requests. Needed so that a
 * service exposing a native (servlet-stack) WebSocket endpoint - e.g. notification-service's
 * live user-events feed - can independently validate the JWT itself (defense in depth, same
 * as every other endpoint) even though the browser's WebSocket API cannot set an
 * Authorization header on the upgrade handshake. Every other path keeps header-only auth.
 */
public class QueryParamAwareBearerTokenResolver implements BearerTokenResolver {

    private static final String WS_PATH_PREFIX = "/ws/";
    private static final String TOKEN_QUERY_PARAM = "access_token";

    private final BearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String headerToken = headerResolver.resolve(request);
        if (headerToken != null) {
            return headerToken;
        }
        String path = request.getRequestURI();
        if (path != null && path.startsWith(WS_PATH_PREFIX)) {
            String queryToken = request.getParameter(TOKEN_QUERY_PARAM);
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken;
            }
        }
        return null;
    }
}
