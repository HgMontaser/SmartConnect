// Thin fetch wrapper around api-gateway's /api/users endpoints.
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
