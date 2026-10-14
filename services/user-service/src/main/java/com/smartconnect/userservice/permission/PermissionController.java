package com.smartconnect.userservice.permission;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('permissions:read')")
    public List<PermissionResponse> list() {
        return permissionService.list();
    }

    @GetMapping("/{name}")
    @PreAuthorize("hasAuthority('permissions:read')")
    public PermissionResponse get(@PathVariable String name) {
        return permissionService.get(name);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('permissions:write')")
    public ResponseEntity<PermissionResponse> create(@Valid @RequestBody CreatePermissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(permissionService.create(request));
    }

    @PutMapping("/{name}")
    @PreAuthorize("hasAuthority('permissions:write')")
    public PermissionResponse update(@PathVariable String name, @Valid @RequestBody UpdatePermissionRequest request) {
        return permissionService.update(name, request);
    }

    @DeleteMapping("/{name}")
    @PreAuthorize("hasAuthority('permissions:write')")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        permissionService.delete(name);
        return ResponseEntity.noContent().build();
    }
}
