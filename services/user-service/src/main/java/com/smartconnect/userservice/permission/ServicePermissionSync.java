package com.smartconnect.userservice.permission;

import com.smartconnect.commons.permissions.PermissionNames;
import com.smartconnect.commons.security.SmartConnectPermissions;
import com.smartconnect.userservice.keycloak.KeycloakRealm;
import com.smartconnect.userservice.role.RoleService;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Brings Keycloak in line with one business service's published permission catalog. The
 * service owns its permission definitions; SmartConnect owns who gets them (roles), so this
 * only ever creates, re-describes or flags permissions - it never touches role assignments
 * beyond granting new permissions to admin.
 *
 * <p>Ownership is per resource (the part before ':'): the first service to declare
 * {@code orders:*} owns it, and another service declaring {@code orders:anything} is refused.
 * SmartConnect's own system resources can't be claimed. A permission the owner stops
 * declaring is flagged orphaned rather than deleted, so a bad deploy can't silently strip
 * access from roles; an admin deletes it from the backoffice once sure.
 */
@Service
public class ServicePermissionSync {

    private static final Logger log = LoggerFactory.getLogger(ServicePermissionSync.class);
    private static final String SMARTCONNECT_OWNER = "smartconnect";

    private final KeycloakRealm keycloakRealm;
    private final RoleService roleService;

    public ServicePermissionSync(KeycloakRealm keycloakRealm, RoleService roleService) {
        this.keycloakRealm = keycloakRealm;
        this.roleService = roleService;
    }

    public void sync(String service, Map<String, String> declared) {
        if (!PermissionNames.isValidService(service)) {
            log.warn("ignoring permission catalog with invalid service name '{}'", service);
            return;
        }
        Map<String, String> catalog = declared == null ? Map.of() : declared;
        Map<String, RoleRepresentation> existing = keycloakRealm.permissionsByName();
        Map<String, String> resourceOwners = resourceOwners(existing);
        RolesResource permissions = keycloakRealm.permissionClient().roles();
        int created = 0;
        int updated = 0;
        int rejected = 0;

        for (Map.Entry<String, String> entry : catalog.entrySet()) {
            String name = entry.getKey();
            String description = entry.getValue();
            String resource = PermissionNames.resource(name);
            String owner = resourceOwners.get(resource);
            if (!PermissionNames.isValid(name) || (owner != null && !owner.equals(service))) {
                log.warn("{} may not declare '{}' ({})", service, name,
                        owner == null ? "invalid name" : "'" + resource + ":*' is owned by " + owner);
                rejected++;
                continue;
            }
            resourceOwners.put(resource, service);

            RoleRepresentation rep = existing.get(name);
            if (rep == null) {
                RoleRepresentation fresh = new RoleRepresentation();
                fresh.setName(name);
                fresh.setDescription(description);
                permissions.create(fresh);
                rep = permissions.get(name).toRepresentation();
                KeycloakRealm.setAttribute(rep, KeycloakRealm.OWNER_ATTRIBUTE, service);
                permissions.get(name).update(rep);
                roleService.grantToAdmin(rep);
                created++;
            } else if (!Objects.equals(rep.getDescription(), description)
                    || !service.equals(KeycloakRealm.owner(rep)) || KeycloakRealm.isOrphaned(rep)) {
                // Also adopts a same-named permission an admin created by hand before the service existed.
                rep.setDescription(description);
                KeycloakRealm.setAttribute(rep, KeycloakRealm.OWNER_ATTRIBUTE, service);
                KeycloakRealm.setAttribute(rep, KeycloakRealm.ORPHANED_ATTRIBUTE, null);
                permissions.get(name).update(rep);
                updated++;
            }
        }

        int orphaned = 0;
        for (RoleRepresentation rep : existing.values()) {
            if (service.equals(KeycloakRealm.owner(rep)) && !catalog.containsKey(rep.getName()) && !KeycloakRealm.isOrphaned(rep)) {
                KeycloakRealm.setAttribute(rep, KeycloakRealm.ORPHANED_ATTRIBUTE, "true");
                permissions.get(rep.getName()).update(rep);
                orphaned++;
            }
        }
        log.info("synced permission catalog of {}: {} declared, {} created, {} updated, {} orphaned, {} rejected",
                service, catalog.size(), created, updated, orphaned, rejected);
    }

    /** resource -> owning service; SmartConnect's own resources are reserved. */
    private static Map<String, String> resourceOwners(Map<String, RoleRepresentation> existing) {
        Map<String, String> owners = new HashMap<>();
        SmartConnectPermissions.SYSTEM.keySet().forEach(p -> owners.put(PermissionNames.resource(p), SMARTCONNECT_OWNER));
        existing.values().forEach(rep -> {
            String owner = KeycloakRealm.owner(rep);
            if (owner != null) {
                owners.putIfAbsent(PermissionNames.resource(rep.getName()), owner);
            }
        });
        return owners;
    }
}
