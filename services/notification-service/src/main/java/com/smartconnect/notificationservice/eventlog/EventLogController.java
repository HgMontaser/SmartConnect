package com.smartconnect.notificationservice.eventlog;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventLogController {

    private final EventLog eventLog;

    public EventLogController(EventLog eventLog) {
        this.eventLog = eventLog;
    }

    @GetMapping("/recent")
    @PreAuthorize("hasAuthority('events:read')")
    public List<EventLog.EventLogEntry> recent(@RequestParam(defaultValue = "100") int limit) {
        return eventLog.recent(Math.max(1, Math.min(limit, EventLog.CAPACITY)));
    }
}
