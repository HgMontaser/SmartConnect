package com.smartconnect.userservice.events;

import com.smartconnect.commons.events.UserEvent;
import com.smartconnect.commons.events.UserEventType;
import com.smartconnect.commons.messaging.EventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes user changes as standard SmartConnect events ({@code user.created},
 * {@code user.updated}, {@code user.deleted} on {@code events.user}, keyed by user id), with
 * {@link UserEvent} as the payload.
 */
@Component
public class UserEventPublisher {

    private static final Map<UserEventType, String> TYPES = Map.of(
            UserEventType.USER_CREATED, "user.created",
            UserEventType.USER_UPDATED, "user.updated",
            UserEventType.USER_DELETED, "user.deleted");

    private final EventPublisher events;

    public UserEventPublisher(EventPublisher events) {
        this.events = events;
    }

    public void publish(UserEvent event) {
        events.publish(TYPES.get(event.eventType()), event.userId(), event);
    }
}
