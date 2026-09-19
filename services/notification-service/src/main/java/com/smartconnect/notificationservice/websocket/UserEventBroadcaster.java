package com.smartconnect.notificationservice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartconnect.commons.events.UserEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fans a {@link UserEvent} out to every open WebSocket session on the {@code /ws/events}
 * bridge. {@link UserEventListener} calls {@link #broadcast(UserEvent)} for the exact same
 * Kafka record its {@code @KafkaListener} method already received - one consumer feeding both
 * the log line (phase 1) and the live push to backoffice-app (phase 2), per the UI spec's
 * requirement that the users-table "last sync" indicator and the event-stream panel are
 * driven by the same underlying stream rather than two disconnected polling loops.
 */
@Component
public class UserEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(UserEventBroadcaster.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public UserEventBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(WebSocketSession session) {
        sessions.add(session);
        log.info("websocket client connected: {} ({} total)", session.getId(), sessions.size());
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
        log.info("websocket client disconnected: {} ({} total)", session.getId(), sessions.size());
    }

    public void broadcast(UserEvent event) {
        if (sessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (IOException e) {
            log.error("failed to serialize {} for websocket broadcast", event.eventType(), e);
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                } else {
                    sessions.remove(session);
                }
            } catch (IOException e) {
                log.warn("failed to push {} to websocket session {} - dropping session", event.eventType(), session.getId(), e);
                sessions.remove(session);
                closeQuietly(session);
            }
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {
            // already broken, nothing more to do
        }
    }
}
