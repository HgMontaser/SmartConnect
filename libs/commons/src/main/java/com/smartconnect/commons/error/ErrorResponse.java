package com.smartconnect.commons.error;

import java.time.Instant;

/** One consistent JSON error body shape across every SmartConnect service. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId
) {
}
