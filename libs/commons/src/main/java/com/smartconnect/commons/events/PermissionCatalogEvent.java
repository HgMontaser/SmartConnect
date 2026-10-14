package com.smartconnect.commons.events;

import java.time.Instant;
import java.util.Map;

/**
 * Payload for the {@code permission-catalog} Kafka topic: the complete set of permissions a
 * service defines (name -> description). Always a full snapshot, never a delta, keyed by
 * {@code service} on a compacted topic - so the latest record per service is the truth, and a
 * lost or replayed message is corrected by the next one. A tombstone (null value) for a
 * service key means the service was removed.
 */
public record PermissionCatalogEvent(
        String service,
        Map<String, String> permissions,
        Instant publishedAt
) {
}
