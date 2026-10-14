// Permissions are Keycloak client roles of the `gateway` client; Keycloak expands the
// caller's roles into them under resource_access.gateway.roles in every access token.
export const PERMISSION_CLIENT = 'gateway';

export const P = {
  USERS_READ: 'users:read',
  USERS_WRITE: 'users:write',
  USERS_DELETE: 'users:delete',
  ROLES_READ: 'roles:read',
  ROLES_WRITE: 'roles:write',
  PERMISSIONS_READ: 'permissions:read',
  PERMISSIONS_WRITE: 'permissions:write',
  PLATFORM_READ: 'platform:read',
  SESSIONS_READ: 'sessions:read',
  SESSIONS_WRITE: 'sessions:write',
  EVENTS_READ: 'events:read',
  AUDIT_READ: 'audit:read',
  LOGS_READ: 'logs:read',
};

export function permissionsFromToken(tokenParsed) {
  return new Set(tokenParsed?.resource_access?.[PERMISSION_CLIENT]?.roles || []);
}

// "users:read" -> { resource: "users", action: "read" }
export function splitPermission(name) {
  const idx = name.indexOf(':');
  return idx === -1 ? { resource: name, action: '' } : { resource: name.slice(0, idx), action: name.slice(idx + 1) };
}

export function groupPermissions(permissions) {
  const groups = new Map();
  permissions.forEach((p) => {
    const { resource } = splitPermission(p.name);
    if (!groups.has(resource)) groups.set(resource, []);
    groups.get(resource).push(p);
  });
  return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b));
}
