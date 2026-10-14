package com.smartconnect.commons.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The permission model shared by every service. Permissions are Keycloak client roles on the
 * bearer-only {@link #CLIENT_ID} client (the API resource server); realm roles are composites
 * of those client roles. Keycloak expands composites at token issue time, so a token carries
 * the caller's effective permissions under {@code resource_access.gateway.roles} and services
 * only ever check permissions - never role names.
 *
 * <p>{@link #SYSTEM} is the catalog the code itself enforces; user-service bootstraps it into
 * Keycloak at startup and refuses to delete any of it.
 */
public final class SmartConnectPermissions {

    public static final String CLIENT_ID = "gateway";

    public static final String USERS_READ = "users:read";
    public static final String USERS_WRITE = "users:write";
    public static final String USERS_DELETE = "users:delete";
    public static final String ROLES_READ = "roles:read";
    public static final String ROLES_WRITE = "roles:write";
    public static final String PERMISSIONS_READ = "permissions:read";
    public static final String PERMISSIONS_WRITE = "permissions:write";
    public static final String PLATFORM_READ = "platform:read";
    public static final String SESSIONS_READ = "sessions:read";
    public static final String SESSIONS_WRITE = "sessions:write";
    public static final String EVENTS_READ = "events:read";
    public static final String AUDIT_READ = "audit:read";
    public static final String LOGS_READ = "logs:read";

    public static final Map<String, String> SYSTEM = systemCatalog();

    private SmartConnectPermissions() {
    }

    private static Map<String, String> systemCatalog() {
        Map<String, String> catalog = new LinkedHashMap<>();
        catalog.put(USERS_READ, "View users");
        catalog.put(USERS_WRITE, "Create and edit users, reset passwords, suspend accounts");
        catalog.put(USERS_DELETE, "Delete users");
        catalog.put(ROLES_READ, "View roles");
        catalog.put(ROLES_WRITE, "Create, edit and delete roles");
        catalog.put(PERMISSIONS_READ, "View permissions");
        catalog.put(PERMISSIONS_WRITE, "Create, edit and delete permissions");
        catalog.put(PLATFORM_READ, "View platform health and traffic: services, instances, routes, circuit breakers, request metrics");
        catalog.put(SESSIONS_READ, "View active sign-in sessions");
        catalog.put(SESSIONS_WRITE, "Revoke sessions and sign users out");
        catalog.put(EVENTS_READ, "View the platform event log and dead letters");
        catalog.put(AUDIT_READ, "View the audit trail: who changed what, admin changes, sign-ins");
        catalog.put(LOGS_READ, "Trace a request through every service's logs (log lines can contain personal data)");
        return Collections.unmodifiableMap(catalog);
    }
}
