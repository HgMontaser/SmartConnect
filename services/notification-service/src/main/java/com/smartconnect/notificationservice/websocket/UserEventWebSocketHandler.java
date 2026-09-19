package com.smartconnect.notificationservice.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Server side of the {@code /ws/events} bridge. This is a push-only channel - clients (only
 * backoffice-app today) don't send anything meaningful over it, so incoming text frames are
 * ignored; the handler exists purely to track session lifecycle for
 * {@link UserEventBroadcaster}.
 */
@Component
public class UserEventWebSocketHandler extends TextWebSocketHandler {

    private final UserEventBroadcaster broadcaster;

    public UserEventWebSocketHandler(UserEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        broadcaster.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.unregister(session);
    }
}
