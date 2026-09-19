package com.smartconnect.notificationservice.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the {@code /ws/events} endpoint. Matches api-gateway's {@code /ws/**} route
 * predicate (see api-gateway's application.yml "ws-bridge" route) without any path stripping,
 * so the path a browser connects to via the gateway (ws://gateway:8090/ws/events) is the same
 * path this service serves directly.
 *
 * <p>Origins are wide open here deliberately: the real access control for this endpoint is
 * the JWT resource-server filter chain (see commons' ServletResourceServerAutoConfiguration +
 * QueryParamAwareBearerTokenResolver), which authenticates every request to this path - Origin
 * checking would be redundant defense against a threat (CSRF-style browser abuse) that doesn't
 * apply to a bearer-token-authenticated, non-cookie-based endpoint.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final UserEventWebSocketHandler userEventWebSocketHandler;

    public WebSocketConfig(UserEventWebSocketHandler userEventWebSocketHandler) {
        this.userEventWebSocketHandler = userEventWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(userEventWebSocketHandler, "/ws/events")
                .setAllowedOriginPatterns("*");
    }
}
