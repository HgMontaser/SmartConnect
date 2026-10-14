package com.smartconnect.demoservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Mock business data (in memory) to exercise read vs write permissions. */
@RestController
@RequestMapping("/api/demo/notes")
public class NotesController {

    private final List<Note> notes = new CopyOnWriteArrayList<>();

    @GetMapping
    @PreAuthorize("hasAuthority('demo:read')")
    public List<Note> list() {
        return notes;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('demo:write')")
    public ResponseEntity<Note> create(@Valid @RequestBody NoteRequest request, @AuthenticationPrincipal Jwt caller) {
        Note note = new Note(UUID.randomUUID(), request.text(), caller.getClaimAsString("preferred_username"), Instant.now());
        notes.add(0, note);
        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }

    public record NoteRequest(@NotBlank @Size(max = 280) String text) {
    }

    public record Note(UUID id, String text, String author, Instant createdAt) {
    }
}
