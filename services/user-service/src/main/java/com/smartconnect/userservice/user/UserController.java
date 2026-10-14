package com.smartconnect.userservice.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Routed to exclusively via api-gateway's {@code /api/users/**} predicate in normal
 * operation. Rate limiting lives in the gateway, not here - this controller has no opinion
 * on request volume. Each endpoint requires a permission from the caller's token.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('users:read')")
    public List<UserResponse> list() {
        return userService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('users:read')")
    public UserResponse get(@PathVariable String id) {
        return userService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('users:write')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('users:write')")
    public UserResponse update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request,
                               @AuthenticationPrincipal Jwt caller) {
        return userService.update(id, request, caller.getSubject());
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('users:write')")
    public ResponseEntity<Void> resetPassword(@PathVariable String id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('users:delete')")
    public ResponseEntity<Void> delete(@PathVariable String id, @AuthenticationPrincipal Jwt caller) {
        userService.delete(id, caller.getSubject());
        return ResponseEntity.noContent().build();
    }
}
