package com.smartconnect.commons.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.support.JacksonUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * The one shape every SmartConnect event has on the wire. The business data is in
 * {@code payload}; everything else is metadata every consumer can rely on.
 *
 * @param id            unique per event - use it to make consumers idempotent
 * @param type          {@code <domain>.<event>}, e.g. {@code billing.invoice-paid}
 * @param version       payload schema version, bumped on breaking payload changes
 * @param source        the publishing service
 * @param occurredAt    when it happened
 * @param correlationId the request that caused it (also in the record headers)
 * @param payload       the event data, read with {@link #payloadAs(Class)}
 */
public record EventEnvelope(
        UUID id,
        String type,
        int version,
        String source,
        Instant occurredAt,
        String correlationId,
        JsonNode payload
) {

    private static final ObjectMapper MAPPER = JacksonUtils.enhancedObjectMapper();

    public <T> T payloadAs(Class<T> payloadType) {
        return MAPPER.convertValue(payload, payloadType);
    }

    static JsonNode toPayload(Object payload) {
        return MAPPER.valueToTree(payload);
    }
}
