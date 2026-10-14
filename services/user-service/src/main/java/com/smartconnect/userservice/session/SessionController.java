package com.smartconnect.userservice.session;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sessions:read')")
    public List<SessionResponse> list() {
        return sessions.list();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sessions:write')")
    public ResponseEntity<Void> revoke(@PathVariable String id) {
        sessions.revoke(id);
        return ResponseEntity.noContent().build();
    }

    /** Ends every session of one user (all devices, all apps). */
    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('sessions:write')")
    public ResponseEntity<Void> signOutEverywhere(@PathVariable String userId) {
        sessions.signOutEverywhere(userId);
        return ResponseEntity.noContent().build();
    }
}
