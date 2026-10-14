package com.smartconnect.commons.messaging;

import com.smartconnect.commons.events.UserEvent;
import com.smartconnect.commons.events.UserEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTopicsTest {

    @Test
    void derivesTopicsFromTypes() {
        assertThat(EventTopics.forType("billing.invoice-paid")).isEqualTo("events.billing");
        assertThat(EventTopics.deadLetter("events.billing", "ledger-service")).isEqualTo("events.billing.ledger-service.dlt");
        assertThat(EventTopics.isDeadLetter("events.billing.ledger-service.dlt")).isTrue();
    }

    @Test
    void rejectsMalformedTypes() {
        assertThatThrownBy(() -> EventTopics.forType("InvoicePaid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventTopics.forType("billing")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payloadRoundTripsThroughTheEnvelope() {
        UserEvent user = new UserEvent(UserEventType.USER_CREATED, "id-1", "alice", "a@x.io", List.of("user"), Instant.parse("2026-01-01T00:00:00Z"));
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), "user.created", 1, "user-service", Instant.now(), null,
                EventEnvelope.toPayload(user));

        assertThat(envelope.payloadAs(UserEvent.class)).isEqualTo(user);
    }
}
