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
    /** Service that defines a permission (published via the permission catalog). */
    public static final String OWNER_ATTRIBUTE = "smartconnect.owner";
    /** Set when the owning service stopped declaring the permission; cleared if it comes back. */
    public static final String ORPHANED_ATTRIBUTE = "smartconnect.orphaned";

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
        return "true".equals(attribute(role, SYSTEM_ATTRIBUTE));
    }

    public static void markSystem(RoleRepresentation role) {
        setAttribute(role, SYSTEM_ATTRIBUTE, "true");
    }

    public static String owner(RoleRepresentation role) {
        return attribute(role, OWNER_ATTRIBUTE);
    }

    public static boolean isOrphaned(RoleRepresentation role) {
        return "true".equals(attribute(role, ORPHANED_ATTRIBUTE));
    }

    public static String attribute(RoleRepresentation role, String key) {
        Map<String, List<String>> attributes = role.getAttributes();
        if (attributes == null) {
            return null;
        }
        List<String> values = attributes.getOrDefault(key, List.of());
        return values.isEmpty() ? null : values.get(0);
    }

    /** {@code value == null} removes the attribute. */
    public static void setAttribute(RoleRepresentation role, String key, String value) {
        Map<String, List<String>> attributes = role.getAttributes() == null ? new HashMap<>() : new HashMap<>(role.getAttributes());
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, List.of(value));
        }
        role.setAttributes(attributes);
    }
}
