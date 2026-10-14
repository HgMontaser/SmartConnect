package com.smartconnect.notificationservice.events;

import com.smartconnect.commons.events.UserEvent;
import com.smartconnect.commons.messaging.EventEnvelope;
import com.smartconnect.commons.messaging.EventListeners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the {@code user.*} events on {@code events.user} - the hook for reacting to them
 * (notifications later). The commons listener factory restores the trace and correlation id
 * from the record, so these logs line up with the originating request, and retries /
 * dead-letters a record if handling throws. The live push to the backoffice is the event log's
 * job (it sees every topic), not this listener's.
 */
@Component
public class UserEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);

    @KafkaListener(topics = "events.user", containerFactory = EventListeners.FACTORY)
    public void onUserEvent(EventEnvelope event) {
        if (!event.type().startsWith("user.")) {
            return;
        }
        UserEvent user = event.payloadAs(UserEvent.class);
        log.info("received {} (id={}) for userId={} username={}", event.type(), event.id(), user.userId(), user.username());
    }
}
