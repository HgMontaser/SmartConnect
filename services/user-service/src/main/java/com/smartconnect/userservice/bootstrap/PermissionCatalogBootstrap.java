package com.smartconnect.userservice.bootstrap;

import com.smartconnect.commons.security.SmartConnectPermissions;
import com.smartconnect.userservice.keycloak.KeycloakRealm;
import com.smartconnect.userservice.role.RoleService;
import jakarta.ws.rs.NotFoundException;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Idempotently brings the realm up to the permission model the code expects, on every boot:
 * (given the permission client exists) the {@link SmartConnectPermissions#SYSTEM} catalog, the system
 * {@code admin}/{@code user} roles, and admin holding every permission. Doing this in code
 * rather than only in realm-export.json means an existing Keycloak database (where
 * {@code --import-realm} is a no-op) is upgraded too. Any failure aborts startup.
 */
@Component
public class PermissionCatalogBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionCatalogBootstrap.class);

    private static final Map<String, String> SYSTEM_ROLES = Map.of(
            RoleService.ADMIN_ROLE, "SmartConnect administrator - always holds every permission",
            RoleService.USER_ROLE, "SmartConnect standard user");

    private final KeycloakRealm keycloakRealm;

    public PermissionCatalogBootstrap(KeycloakRealm keycloakRealm) {
        this.keycloakRealm = keycloakRealm;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensurePermissionClient();

        RolesResource permissions = keycloakRealm.permissionClient().roles();
        SmartConnectPermissions.SYSTEM.forEach((name, description) -> ensureSystemRole(permissions, name, description));

        RolesResource realmRoles = keycloakRealm.realm().roles();
        SYSTEM_ROLES.forEach((name, description) -> ensureSystemRole(realmRoles, name, description));

        realmRoles.get(RoleService.ADMIN_ROLE).addComposites(keycloakRealm.permissions());
        log.info("permission catalog in sync: {} permissions, admin holds all", keycloakRealm.permissions().size());
    }

    /**
     * The client is created by keycloak-init (infra/keycloak/init.sh): this service's account
     * can manage that one client but deliberately can't create clients.
     */
    private void ensurePermissionClient() {
        if (keycloakRealm.realm().clients().findByClientId(SmartConnectPermissions.CLIENT_ID).isEmpty()) {
            throw new IllegalStateException("Permission client '" + SmartConnectPermissions.CLIENT_ID
                    + "' is missing - run keycloak-init (docker compose up keycloak-init)");
        }
    }

    private void ensureSystemRole(RolesResource roles, String name, String description) {
        RoleResource role = roles.get(name);
        RoleRepresentation rep;
        try {
            rep = role.toRepresentation();
        } catch (NotFoundException e) {
            RoleRepresentation created = new RoleRepresentation();
            created.setName(name);
            created.setDescription(description);
            roles.create(created);
            rep = role.toRepresentation();
            log.info("created system role/permission '{}'", name);
        }
        if (!KeycloakRealm.isSystem(rep)) {
            KeycloakRealm.markSystem(rep);
            if (rep.getDescription() == null || rep.getDescription().isBlank()) {
                rep.setDescription(description);
            }
            role.update(rep);
        }
    }
}
