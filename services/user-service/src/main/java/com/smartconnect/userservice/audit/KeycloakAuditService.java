package com.smartconnect.userservice.audit;

import com.smartconnect.userservice.keycloak.KeycloakRealm;
import jakarta.ws.rs.NotFoundException;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keycloak's own audit records for the realm (kept 30 days, enabled by keycloak-init): admin
 * events - every change to users, roles, clients, made from anywhere including the Keycloak
 * console - and sign-in events. Ids are resolved to usernames / client ids for display.
 * Changes made through the backoffice appear here as made by service-account-user-service;
 * the person behind them is in the audit.performed events (see AuditInterceptor).
 */
@Service
public class KeycloakAuditService {

    private static final int MAX = 200;

    private final KeycloakRealm keycloakRealm;
    private final Map<String, String> usernames = new ConcurrentHashMap<>();
    private final Map<String, String> clientIds = new ConcurrentHashMap<>();

    public KeycloakAuditService(KeycloakRealm keycloakRealm) {
        this.keycloakRealm = keycloakRealm;
    }

    public List<AdminChange> adminChanges(int max) {
        return keycloakRealm.realm().getAdminEvents(null, null, null, null, null, null, null, null, 0, Math.min(max, MAX))
                .stream().map(this::toAdminChange).toList();
    }

    public List<SignIn> signIns(int max) {
        return keycloakRealm.realm().getEvents(null, null, null, null, null, null, 0, Math.min(max, MAX))
                .stream().map(this::toSignIn).toList();
    }

    private AdminChange toAdminChange(AdminEventRepresentation e) {
        var auth = e.getAuthDetails();
        return new AdminChange(Instant.ofEpochMilli(e.getTime()), e.getOperationType(), e.getResourceType(), e.getResourcePath(),
                auth == null ? null : username(auth.getUserId()),
                auth == null ? null : clientId(auth.getClientId()),
                auth == null ? null : auth.getIpAddress(), e.getError());
    }

    private SignIn toSignIn(EventRepresentation e) {
        Map<String, String> details = e.getDetails() == null ? Map.of() : e.getDetails();
        String user = details.getOrDefault("username", username(e.getUserId()));
        return new SignIn(Instant.ofEpochMilli(e.getTime()), e.getType(), user, e.getClientId(), e.getIpAddress(), e.getError());
    }

    private String username(String userId) {
        if (userId == null) {
            return null;
        }
        return usernames.computeIfAbsent(userId, id -> {
            try {
                return keycloakRealm.realm().users().get(id).toRepresentation().getUsername();
            } catch (NotFoundException e) {
                return id + " (deleted)";
            }
        });
    }

    private String clientId(String clientUuid) {
        if (clientUuid == null) {
            return null;
        }
        return clientIds.computeIfAbsent(clientUuid, id -> {
            try {
                return keycloakRealm.realm().clients().get(id).toRepresentation().getClientId();
            } catch (RuntimeException e) {
                return id;
            }
        });
    }

    public record AdminChange(Instant at, String operation, String resourceType, String resourcePath, String actor,
                              String client, String ipAddress, String error) {
    }

    public record SignIn(Instant at, String type, String username, String client, String ipAddress, String error) {
    }
}
