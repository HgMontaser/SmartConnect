package com.smartconnect.userservice.role;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('roles:read')")
    public List<RoleResponse> list() {
        return roleService.list();
    }

    @GetMapping("/{name}")
    @PreAuthorize("hasAuthority('roles:read')")
    public RoleResponse get(@PathVariable String name) {
        return roleService.get(name);
    }

    @GetMapping("/{name}/users")
    @PreAuthorize("hasAuthority('roles:read') and hasAuthority('users:read')")
    public List<RoleMemberResponse> members(@PathVariable String name) {
        return roleService.members(name);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('roles:write')")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.create(request));
    }

    @PutMapping("/{name}")
    @PreAuthorize("hasAuthority('roles:write')")
    public RoleResponse update(@PathVariable String name, @Valid @RequestBody UpdateRoleRequest request) {
        return roleService.update(name, request);
    }

    @DeleteMapping("/{name}")
    @PreAuthorize("hasAuthority('roles:write')")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        roleService.delete(name);
        return ResponseEntity.noContent().build();
    }
}
