package com.smartconnect.demoservice;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Answers after a chosen delay, to see the gateway's timeouts and circuit breaker at work. */
@RestController
@RequestMapping("/api/demo")
public class SlowController {

    private static final long MAX_DELAY_MS = 60_000;

    @GetMapping("/slow")
    @PreAuthorize("hasAuthority('demo:read')")
    public Map<String, Object> slow(@RequestParam(defaultValue = "1000") long ms) throws InterruptedException {
        long delay = Math.max(0, Math.min(ms, MAX_DELAY_MS));
        Thread.sleep(delay);
        return Map.of("delayedMs", delay);
    }
}
