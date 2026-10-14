package com.smartconnect.userservice.permission;

import com.smartconnect.userservice.keycloak.KeycloakRealm;
import com.smartconnect.userservice.role.RoleService;
import jakarta.ws.rs.NotFoundException;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * CRUD over permissions - client roles of the permission client. A new permission is
 * granted to the admin role straight away so admins never lose access to anything; system
 * permissions (the ones the code enforces) can be re-described but never deleted.
 */
@Service
public class PermissionService {

    private final KeycloakRealm keycloakRealm;
    private final RoleService roleService;

    public PermissionService(KeycloakRealm keycloakRealm, RoleService roleService) {
        this.keycloakRealm = keycloakRealm;
        this.roleService = roleService;
    }

    public List<PermissionResponse> list() {
        Map<String, List<String>> rolesByPermission = roleService.rolesByPermission();
        return keycloakRealm.permissions().stream()
                .map(p -> toResponse(p, rolesByPermission))
                .toList();
    }

    public PermissionResponse get(String name) {
        return toResponse(fetch(name), roleService.rolesByPermission());
    }

    public PermissionResponse create(CreatePermissionRequest request) {
        if (keycloakRealm.permissionsByName().containsKey(request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Permission already exists: " + request.name());
        }
        RoleRepresentation rep = new RoleRepresentation();
        rep.setName(request.name());
        rep.setDescription(request.description());
        keycloakRealm.permissionClient().roles().create(rep);

        roleService.grantToAdmin(fetch(request.name()));
        return get(request.name());
    }

    public PermissionResponse update(String name, UpdatePermissionRequest request) {
        RoleRepresentation rep = fetch(name);
        rep.setDescription(request.description());
        permission(name).update(rep);
        return get(name);
    }

    public void delete(String name) {
        RoleRepresentation rep = fetch(name);
        if (KeycloakRealm.isSystem(rep)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System permission can't be deleted: " + name);
        }
        permission(name).remove();
    }

    private RoleRepresentation fetch(String name) {
        try {
            return permission(name).toRepresentation();
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found: " + name);
        }
    }

    private RoleResource permission(String name) {
        return keycloakRealm.permissionClient().roles().get(name);
    }

    private PermissionResponse toResponse(RoleRepresentation rep, Map<String, List<String>> rolesByPermission) {
        List<String> roles = rolesByPermission.getOrDefault(rep.getName(), List.of()).stream().sorted().toList();
        return new PermissionResponse(rep.getName(), rep.getDescription(), KeycloakRealm.isSystem(rep), roles);
    }
}
