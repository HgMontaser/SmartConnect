package com.smartconnect.userservice.user;

import com.smartconnect.commons.events.UserEvent;
import com.smartconnect.commons.events.UserEventType;
import com.smartconnect.userservice.config.KeycloakAdminProperties;
import com.smartconnect.userservice.events.UserEventPublisher;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Thin REST-CRUD facade over the Keycloak Admin REST API - Keycloak is the sole source of
 * truth for users, this service holds no database of its own. Every mutation publishes a
 * {@link UserEvent} to Kafka after the Keycloak write succeeds (never before - we don't want
 * to announce a change that didn't actually happen).
 *
 * <p>Only realm roles in {@link #MANAGED_REALM_ROLES} are ever added/removed by this service,
 * so we never accidentally strip Keycloak's own default realm roles (offline_access,
 * uma_authorization, etc.) from a user.
 */
@Service
public class UserService {

    private static final Set<String> MANAGED_REALM_ROLES = Set.of("admin", "user");

    private final Keycloak keycloak;
    private final String targetRealm;
    private final UserEventPublisher eventPublisher;

    public UserService(Keycloak keycloak, KeycloakAdminProperties properties, UserEventPublisher eventPublisher) {
        this.keycloak = keycloak;
        this.targetRealm = properties.getTargetRealm();
        this.eventPublisher = eventPublisher;
    }

    public List<UserResponse> list() {
        return usersResource().list().stream().map(this::toResponse).toList();
    }

    public UserResponse get(String id) {
        return toResponse(fetch(id));
    }

    public UserResponse create(CreateUserRequest request) {
        UserRepresentation rep = new UserRepresentation();
        rep.setUsername(request.username());
        rep.setEmail(request.email());
        rep.setFirstName(request.firstName());
        rep.setLastName(request.lastName());
        rep.setEnabled(true);
        rep.setEmailVerified(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.password());
        credential.setTemporary(false);
        rep.setCredentials(List.of(credential));

        try (Response response = usersResource().create(rep)) {
            if (response.getStatus() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already exists: " + request.username());
            }
            if (response.getStatus() != 201) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak rejected user creation (status " + response.getStatus() + ")");
            }
            String userId = CreatedResponseUtil.getCreatedId(response);
            applyRealmRoles(userId, request.realmRoles());

            UserResponse created = get(userId);
            eventPublisher.publish(new UserEvent(
                    UserEventType.USER_CREATED, created.id(), created.username(), created.email(),
                    created.realmRoles(), Instant.now()));
            return created;
        }
    }

    public UserResponse update(String id, UpdateUserRequest request) {
        UserRepresentation rep = fetch(id);
        rep.setEmail(request.email());
        rep.setFirstName(request.firstName());
        rep.setLastName(request.lastName());
        usersResource().get(id).update(rep);

        if (request.realmRoles() != null) {
            applyRealmRoles(id, request.realmRoles());
        }

        UserResponse updated = get(id);
        eventPublisher.publish(new UserEvent(
                UserEventType.USER_UPDATED, updated.id(), updated.username(), updated.email(),
                updated.realmRoles(), Instant.now()));
        return updated;
    }

    public void delete(String id) {
        UserResponse existing = get(id);
        try {
            usersResource().get(id).remove();
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id);
        }
        eventPublisher.publish(new UserEvent(
                UserEventType.USER_DELETED, existing.id(), existing.username(), existing.email(),
                existing.realmRoles(), Instant.now()));
    }

    private void applyRealmRoles(String userId, List<String> requestedRoles) {
        List<String> requested = requestedRoles == null ? List.of() : requestedRoles;
        for (String role : requested) {
            if (!MANAGED_REALM_ROLES.contains(role)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown realm role: " + role);
            }
        }

        UserResource userResource = usersResource().get(userId);
        RealmResource realmResource = keycloak.realm(targetRealm);

        List<RoleRepresentation> currentManaged = userResource.roles().realmLevel().listAll().stream()
                .filter(r -> MANAGED_REALM_ROLES.contains(r.getName()))
                .toList();
        if (!currentManaged.isEmpty()) {
            userResource.roles().realmLevel().remove(currentManaged);
        }

        List<RoleRepresentation> toAdd = requested.stream()
                .map(name -> realmResource.roles().get(name).toRepresentation())
                .toList();
        if (!toAdd.isEmpty()) {
            userResource.roles().realmLevel().add(toAdd);
        }
    }

    private UserRepresentation fetch(String id) {
        try {
            return usersResource().get(id).toRepresentation();
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id);
        }
    }

    private UserResponse toResponse(UserRepresentation rep) {
        List<String> realmRoles = usersResource().get(rep.getId()).roles().realmLevel().listAll().stream()
                .map(RoleRepresentation::getName)
                .filter(MANAGED_REALM_ROLES::contains)
                .toList();
        return new UserResponse(
                rep.getId(), rep.getUsername(), rep.getEmail(), rep.getFirstName(), rep.getLastName(),
                Boolean.TRUE.equals(rep.isEnabled()), realmRoles);
    }

    private UsersResource usersResource() {
        return keycloak.realm(targetRealm).users();
    }
}
