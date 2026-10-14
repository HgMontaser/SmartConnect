package com.smartconnect.userservice.role;

import com.smartconnect.userservice.keycloak.KeycloakRealm;
import jakarta.ws.rs.NotFoundException;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CRUD over the realm roles SmartConnect manages. A role is a Keycloak realm role whose
 * composites are permissions (client roles of the permission client) - Keycloak expands them
 * into every token, so nothing here needs a database.
 *
 * <p>Keycloak's own built-in realm roles are invisible to this service. {@link #ADMIN_ROLE}
 * always holds every permission (new permissions are granted to it automatically), so its
 * permission set can't be edited - that's what keeps the backoffice from locking itself out.
 */
@Service
public class RoleService {

    public static final String ADMIN_ROLE = "admin";
    public static final String USER_ROLE = "user";

    private static final int MAX_MEMBERS = 1000;

    private final KeycloakRealm keycloakRealm;
    private final Set<String> builtInRoles;

    public RoleService(KeycloakRealm keycloakRealm) {
        this.keycloakRealm = keycloakRealm;
        this.builtInRoles = Set.of("default-roles-" + keycloakRealm.realmName(), "offline_access", "uma_authorization");
    }

    public List<RoleResponse> list() {
        return managedRoles().stream().map(this::toResponse).toList();
    }

    public RoleResponse get(String name) {
        return toResponse(fetch(name));
    }

    public RoleResponse create(CreateRoleRequest request) {
        if (exists(request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists: " + request.name());
        }
        List<String> permissions = request.permissions() == null ? List.of() : request.permissions();
        requireKnownPermissions(permissions);

        RoleRepresentation rep = new RoleRepresentation();
        rep.setName(request.name());
        rep.setDescription(request.description());
        keycloakRealm.realm().roles().create(rep);

        setPermissions(request.name(), permissions);
        return get(request.name());
    }

    public RoleResponse update(String name, UpdateRoleRequest request) {
        RoleRepresentation rep = fetch(name);
        // Validate everything before the first write so a rejected request changes nothing.
        boolean changePermissions = request.permissions() != null && !ADMIN_ROLE.equals(name);
        if (request.permissions() != null && ADMIN_ROLE.equals(name)
                && !permissionNames(name).equals(new HashSet<>(request.permissions()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The admin role always holds every permission and can't be changed");
        }
        if (changePermissions) {
            requireKnownPermissions(request.permissions());
        }

        rep.setDescription(request.description());
        role(name).update(rep);
        if (changePermissions) {
            setPermissions(name, request.permissions());
        }
        return get(name);
    }

    public void delete(String name) {
        RoleRepresentation rep = fetch(name);
        if (KeycloakRealm.isSystem(rep)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System role can't be deleted: " + name);
        }
        role(name).remove();
    }

    public List<RoleMemberResponse> members(String name) {
        fetch(name);
        return role(name).getUserMembers(0, MAX_MEMBERS).stream()
                .map(u -> new RoleMemberResponse(u.getId(), u.getUsername(), u.getEmail()))
                .toList();
    }

    /** Names of every role this service manages - the only roles a user can be given. */
    public Set<String> managedRoleNames() {
        return managedRoles().stream().map(RoleRepresentation::getName).collect(Collectors.toUnmodifiableSet());
    }

    /** Permission name -> names of the roles that grant it. */
    public Map<String, List<String>> rolesByPermission() {
        return managedRoles().stream()
                .flatMap(role -> permissionNames(role.getName()).stream().map(p -> Map.entry(p, role.getName())))
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    public void grantToAdmin(RoleRepresentation permission) {
        role(ADMIN_ROLE).addComposites(List.of(permission));
    }

    private Map<String, RoleRepresentation> requireKnownPermissions(List<String> requested) {
        Map<String, RoleRepresentation> available = keycloakRealm.permissionsByName();
        for (String name : requested) {
            if (!available.containsKey(name)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown permission: " + name);
            }
        }
        return available;
    }

    private void setPermissions(String roleName, List<String> requested) {
        Map<String, RoleRepresentation> available = requireKnownPermissions(requested);
        Set<String> wanted = new HashSet<>(requested);
        RoleResource role = role(roleName);
        Set<RoleRepresentation> current = role.getClientRoleComposites(keycloakRealm.permissionClientUuid());

        List<RoleRepresentation> toRemove = current.stream().filter(p -> !wanted.contains(p.getName())).toList();
        Set<String> currentNames = current.stream().map(RoleRepresentation::getName).collect(Collectors.toSet());
        List<RoleRepresentation> toAdd = wanted.stream().filter(n -> !currentNames.contains(n)).map(available::get).toList();

        if (!toRemove.isEmpty()) {
            role.deleteComposites(toRemove);
        }
        if (!toAdd.isEmpty()) {
            role.addComposites(toAdd);
        }
    }

    private Set<String> permissionNames(String roleName) {
        return role(roleName).getClientRoleComposites(keycloakRealm.permissionClientUuid()).stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toSet());
    }

    private List<RoleRepresentation> managedRoles() {
        return keycloakRealm.realm().roles().list(false).stream()
                .filter(r -> !builtInRoles.contains(r.getName()))
                .sorted(Comparator.comparing(RoleRepresentation::getName))
                .toList();
    }

    private boolean exists(String name) {
        try {
            role(name).toRepresentation();
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    private RoleRepresentation fetch(String name) {
        if (builtInRoles.contains(name)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found: " + name);
        }
        try {
            return role(name).toRepresentation();
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found: " + name);
        }
    }

    private RoleResource role(String name) {
        return keycloakRealm.realm().roles().get(name);
    }

    private RoleResponse toResponse(RoleRepresentation rep) {
        List<String> permissions = permissionNames(rep.getName()).stream().sorted().toList();
        int userCount = role(rep.getName()).getUserMembers(0, MAX_MEMBERS).size();
        return new RoleResponse(rep.getName(), rep.getDescription(), KeycloakRealm.isSystem(rep), permissions, userCount);
    }
}
