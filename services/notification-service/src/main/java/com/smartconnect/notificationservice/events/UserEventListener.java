package com.smartconnect.notificationservice.events;

import com.smartconnect.commons.events.UserEvent;
import com.smartconnect.commons.messaging.EventEnvelope;
import com.smartconnect.commons.messaging.EventListeners;
import com.smartconnect.notificationservice.websocket.UserEventBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the {@code user.*} events on {@code events.user} and pushes each one to every
 * connected backoffice WebSocket client. The commons listener factory restores the trace and
 * correlation id from the record (so these logs line up with the originating request) and
 * retries / dead-letters a record if broadcasting throws.
 */
@Component
public class UserEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);

    private final UserEventBroadcaster broadcaster;

    public UserEventListener(UserEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @KafkaListener(topics = "events.user", containerFactory = EventListeners.FACTORY)
    public void onUserEvent(EventEnvelope event) {
        if (!event.type().startsWith("user.")) {
            return;
        }
        UserEvent user = event.payloadAs(UserEvent.class);
        log.info("received {} (id={}) for userId={} username={} - broadcasting to websocket clients",
                event.type(), event.id(), user.userId(), user.username());
        broadcaster.broadcast(user);
    }
}
