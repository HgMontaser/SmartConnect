package com.smartconnect.notificationservice.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes every Kafka event to the backoffice over the {@code /ws/events} bridge, one frame per
 * record, fed by the event log's consumer (one stream behind the users table's "last sync", the
 * Events page's live header and the live feed). User events go to every signed-in session -
 * the users table needs them - with their payload; every other event only to sessions holding
 * {@code events:read}, and as metadata only: payloads stay behind the Events API.
 */
@Component
public class LiveEventBroadcaster {

    static final String EVENTS_READ = "events:read";
    private static final Logger log = LoggerFactory.getLogger(LiveEventBroadcaster.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public LiveEventBroadcaster(ObjectMapper objectMapper) {
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

    public void broadcast(LiveEvent event) {
        if (sessions.isEmpty()) {
            return;
        }
        TextMessage message;
        try {
            message = new TextMessage(objectMapper.writeValueAsString(event));
        } catch (IOException e) {
            log.error("failed to serialize {} for websocket broadcast", event.type(), e);
            return;
        }
        boolean forEveryone = event.user() != null;
        for (WebSocketSession session : sessions) {
            if (!forEveryone && !canReadEvents(session.getPrincipal())) {
                continue;
            }
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                } else {
                    sessions.remove(session);
                }
            } catch (IOException | IllegalStateException e) {
                log.warn("failed to push {} to websocket session {} - dropping session", event.type(), session.getId(), e);
                sessions.remove(session);
                closeQuietly(session);
            }
        }
    }

    static boolean canReadEvents(Principal principal) {
        return principal instanceof Authentication authentication
                && authentication.getAuthorities().stream().anyMatch(a -> EVENTS_READ.equals(a.getAuthority()));
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {
            // already broken, nothing more to do
        }
    }

    /**
     * One record as the backoffice sees it live.
     *
     * @param actor   who did it, when the event says (see EventSummaries)
     * @param summary what happened, as a verb phrase ("created user alice")
     * @param user    the payload of a {@code user.*} event (the users table applies it); null otherwise
     */
    public record LiveEvent(String topic, int partition, long offset, String key, String eventId, String type,
                            String source, Instant timestamp, boolean deadLetter, String error,
                            String correlationId, String actor, String summary, JsonNode user) {
    }
}
