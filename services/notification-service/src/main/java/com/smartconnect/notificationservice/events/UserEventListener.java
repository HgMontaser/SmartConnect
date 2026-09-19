package com.smartconnect.notificationservice.events;

import com.smartconnect.commons.events.UserEvent;
import com.smartconnect.notificationservice.websocket.UserEventBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code user-events}. By the time this method runs,
 * {@code CorrelationIdRecordInterceptor} (wired into the listener container factory) has
 * already read the {@code X-Correlation-Id} record header back into MDC, so this log line
 * carries the same correlation id as the HTTP request that originated the event over in
 * user-service. Phase 2 adds a second reaction alongside the log line: pushing the same event
 * out over {@link UserEventBroadcaster} to any connected backoffice-app WebSocket clients -
 * one consumer, two effects, so the log trail and the live UI can never drift out of sync.
 */
@Component
public class UserEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);

    private final UserEventBroadcaster broadcaster;

    public UserEventListener(UserEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @KafkaListener(topics = "user-events", groupId = "${spring.kafka.consumer.group-id}")
    public void onUserEvent(UserEvent event) {
        log.info(
                "received {} for userId={} username={} email={} realmRoles={} - broadcasting to websocket clients",
                event.eventType(), event.userId(), event.username(), event.email(), event.realmRoles());
        broadcaster.broadcast(event);
    }
}
