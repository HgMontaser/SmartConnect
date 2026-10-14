package com.smartconnect.userservice.session;

import java.time.Instant;
import java.util.List;

public record SessionResponse(
        String id,
        String userId,
        String username,
        String ipAddress,
        Instant startedAt,
        Instant lastAccessAt,
        List<String> clients
) {
}
