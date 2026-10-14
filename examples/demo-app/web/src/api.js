// The demo frontend only ever talks to api-gateway, which routes /api/demo/** to demo-service
// because demo-service asked for it via Consul.
const GATEWAY = import.meta.env.VITE_GATEWAY_HTTP_URL || 'http://localhost:8090';

export async function call(keycloak, path, { method = 'GET', body } = {}) {
  await keycloak.updateToken(30).catch(() => keycloak.login());
  const response = await fetch(`${GATEWAY}${path}`, {
    method,
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${keycloak.token}`,
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const error = new Error(data?.message || `Request failed (${response.status})`);
    error.status = response.status;
    throw error;
  }
  return data;
}
