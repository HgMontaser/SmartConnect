package com.smartconnect.userservice.keycloak;

import com.smartconnect.commons.security.SmartConnectPermissions;
import com.smartconnect.userservice.config.KeycloakAdminProperties;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point to the managed realm and to the client whose roles are SmartConnect's
 * permissions (see {@link SmartConnectPermissions}). Also owns the "system" marker - a role
 * attribute set on every role/permission the code itself depends on, which the services
 * refuse to delete.
 */
@Component
public class KeycloakRealm {

    public static final String SYSTEM_ATTRIBUTE = "smartconnect.system";

    private final Keycloak keycloak;
    private final String realmName;
    private volatile String permissionClientUuid;

    public KeycloakRealm(Keycloak keycloak, KeycloakAdminProperties properties) {
        this.keycloak = keycloak;
        this.realmName = properties.getTargetRealm();
    }

    public RealmResource realm() {
        return keycloak.realm(realmName);
    }

    public String realmName() {
        return realmName;
    }

    public String permissionClientUuid() {
        String uuid = permissionClientUuid;
        if (uuid == null) {
            List<ClientRepresentation> matches = realm().clients().findByClientId(SmartConnectPermissions.CLIENT_ID);
            if (matches.isEmpty()) {
                throw new IllegalStateException("Permission client '" + SmartConnectPermissions.CLIENT_ID
                        + "' does not exist in realm " + realmName);
            }
            uuid = matches.get(0).getId();
            permissionClientUuid = uuid;
        }
        return uuid;
    }

    public ClientResource permissionClient() {
        return realm().clients().get(permissionClientUuid());
    }

    /** All permissions (full representations, attributes included), sorted by name. */
    public List<RoleRepresentation> permissions() {
        return permissionClient().roles().list(false).stream()
                .sorted(Comparator.comparing(RoleRepresentation::getName))
                .toList();
    }

    public Map<String, RoleRepresentation> permissionsByName() {
        Map<String, RoleRepresentation> byName = new HashMap<>();
        permissions().forEach(p -> byName.put(p.getName(), p));
        return byName;
    }

    public static boolean isSystem(RoleRepresentation role) {
        Map<String, List<String>> attributes = role.getAttributes();
        return attributes != null && attributes.getOrDefault(SYSTEM_ATTRIBUTE, List.of()).contains("true");
    }

    public static void markSystem(RoleRepresentation role) {
        Map<String, List<String>> attributes = role.getAttributes() == null ? new HashMap<>() : new HashMap<>(role.getAttributes());
        attributes.put(SYSTEM_ATTRIBUTE, List.of("true"));
        role.setAttributes(attributes);
    }
}
