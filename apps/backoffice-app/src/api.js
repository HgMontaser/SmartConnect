// Thin fetch wrapper around api-gateway endpoints (/api/users, /api/roles, /api/permissions).
// backoffice-app NEVER talks to user-service directly — only this gateway base URL.
const GATEWAY_HTTP_URL = import.meta.env.VITE_GATEWAY_HTTP_URL || 'http://localhost:8090';

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function request(path, { method = 'GET', token, body } = {}) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await fetch(`${GATEWAY_HTTP_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (err) {
    throw new ApiError('Could not reach the api-gateway. Check your connection and try again.', 0);
  }

  if (!response.ok) {
    let detail = '';
    try {
      const data = await response.json();
      detail = data?.message || data?.error || '';
    } catch {
      // No JSON body on the error response — fall back to a generic message below.
    }
    throw new ApiError(
      detail || `Request failed with status ${response.status}${response.statusText ? ` (${response.statusText})` : ''}`,
      response.status
    );
  }

  if (response.status === 204) return null;

  try {
    return await response.json();
  } catch {
    return null;
  }
}

export function listUsers(token) {
  return request('/api/users', { token });
}

export function getUser(id, token) {
  return request(`/api/users/${id}`, { token });
}

export function createUser(payload, token) {
  return request('/api/users', { method: 'POST', token, body: payload });
}

export function updateUser(id, payload, token) {
  return request(`/api/users/${id}`, { method: 'PUT', token, body: payload });
}

export function deleteUser(id, token) {
  return request(`/api/users/${id}`, { method: 'DELETE', token });
}

export function resetUserPassword(id, payload, token) {
  return request(`/api/users/${id}/reset-password`, { method: 'POST', token, body: payload });
}

export function listRoles(token) {
  return request('/api/roles', { token });
}

export function listRoleMembers(name, token) {
  return request(`/api/roles/${encodeURIComponent(name)}/users`, { token });
}

export function createRole(payload, token) {
  return request('/api/roles', { method: 'POST', token, body: payload });
}

export function updateRole(name, payload, token) {
  return request(`/api/roles/${encodeURIComponent(name)}`, { method: 'PUT', token, body: payload });
}

export function deleteRole(name, token) {
  return request(`/api/roles/${encodeURIComponent(name)}`, { method: 'DELETE', token });
}

export function listPermissions(token) {
  return request('/api/permissions', { token });
}

export function createPermission(payload, token) {
  return request('/api/permissions', { method: 'POST', token, body: payload });
}

export function updatePermission(name, payload, token) {
  return request(`/api/permissions/${encodeURIComponent(name)}`, { method: 'PUT', token, body: payload });
}

export function deletePermission(name, token) {
  return request(`/api/permissions/${encodeURIComponent(name)}`, { method: 'DELETE', token });
}

export function getPlatformServices(token) {
  return request('/api/platform/services', { token });
}

export function listSessions(token) {
  return request('/api/sessions', { token });
}

export function revokeSession(id, token) {
  return request(`/api/sessions/${encodeURIComponent(id)}`, { method: 'DELETE', token });
}

export function signOutEverywhere(userId, token) {
  return request(`/api/sessions/users/${encodeURIComponent(userId)}`, { method: 'DELETE', token });
}

export function listRecentEvents(token, limit = 200) {
  return request(`/api/events/recent?limit=${limit}`, { token });
}
