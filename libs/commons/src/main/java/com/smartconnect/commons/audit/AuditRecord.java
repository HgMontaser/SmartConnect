package com.smartconnect.commons.audit;

import java.time.Instant;
import java.util.Map;

/**
 * One change attempted through a SmartConnect API, by a person or a service account.
 *
 * @param actor    the caller's username ({@code preferred_username}), or client id for services
 * @param actorId  the token subject
 * @param service  the service that handled it
 * @param method   HTTP method
 * @param action   the endpoint pattern, e.g. {@code PUT /api/roles/{name}}
 * @param targets  the path variables, e.g. {@code {name=support}}
 * @param status   the HTTP status returned - failed and denied attempts are recorded too
 */
public record AuditRecord(
        String actor,
        String actorId,
        String service,
        String method,
        String action,
        Map<String, String> targets,
        int status,
        Instant at
) {
}
