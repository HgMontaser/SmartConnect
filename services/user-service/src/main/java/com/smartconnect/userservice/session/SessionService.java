package com.smartconnect.userservice.session;

import com.smartconnect.userservice.keycloak.KeycloakRealm;
import jakarta.ws.rs.NotFoundException;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Active Keycloak sign-in sessions in the realm. Keycloak lists sessions per client, so this
 * walks every client that has active sessions and merges them (one SSO session spans several
 * clients - login-app, backoffice, ...).
 */
@Service
public class SessionService {

    private static final int MAX_PER_CLIENT = 500;

    private final KeycloakRealm keycloakRealm;

    public SessionService(KeycloakRealm keycloakRealm) {
        this.keycloakRealm = keycloakRealm;
    }

    public List<SessionResponse> list() {
        Map<String, SessionResponse> sessions = new LinkedHashMap<>();
        for (Map<String, String> stats : keycloakRealm.realm().getClientSessionStats()) {
            String clientUuid = stats.get("id");
            for (UserSessionRepresentation s : keycloakRealm.realm().clients().get(clientUuid).getUserSessions(0, MAX_PER_CLIENT)) {
                sessions.putIfAbsent(s.getId(), new SessionResponse(s.getId(), s.getUserId(), s.getUsername(), s.getIpAddress(),
                        Instant.ofEpochMilli(s.getStart()), Instant.ofEpochMilli(s.getLastAccess()),
                        s.getClients() == null ? List.of() : s.getClients().values().stream().sorted().toList()));
            }
        }
        return sessions.values().stream().sorted(Comparator.comparing(SessionResponse::lastAccessAt).reversed()).toList();
    }

    public void revoke(String sessionId) {
        try {
            keycloakRealm.realm().deleteSession(sessionId, false);
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId);
        }
    }

    public void signOutEverywhere(String userId) {
        try {
            keycloakRealm.realm().users().get(userId).logout();
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
        }
    }
}
