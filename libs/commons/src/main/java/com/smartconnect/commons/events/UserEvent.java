package com.smartconnect.commons.events;

import java.time.Instant;
import java.util.List;

/**
 * Payload for the {@code user-events} Kafka topic. Shared by producer (user-service) and
 * consumer (notification-service) so the two can never drift out of sync. The correlation id
 * itself travels as a Kafka record header (see {@code commons.kafka}), not in this payload -
 * headers are the natural place for cross-cutting trace metadata.
 */
public record UserEvent(
        UserEventType eventType,
        String userId,
        String username,
        String email,
        List<String> realmRoles,
        Instant occurredAt
) {
}
