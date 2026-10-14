# SmartConnect

Secure gateway system: Keycloak identity, Vault secrets, Kafka async messaging, Redis-backed rate limiting, Elastic observability.

Everything runs locally with Docker only — no host JDK, Maven or Node needed. (The `k8s/`
charts are parked until the project is finished; nothing deploys to the cloud.)

## Run it

```
docker compose up -d --build
```

Reachable at:
- Login app: http://localhost:5173
- Backoffice: http://localhost:5174 (sign in as `admin.seed` / `SmartConnect123!`)
- api-gateway: http://localhost:8090
- Keycloak admin console: http://localhost:8080 (admin / admin)
- Vault UI: http://localhost:8200 (token: `smartconnect-root-token`)
- Kibana: http://localhost:5601
- Elasticsearch: http://localhost:9200

Rebuild a single piece after a change, e.g. `docker compose up -d --build user-service`.

## Users, roles and permissions

Keycloak is the only store — there's no extra database.

- **Permissions** are client roles of the `gateway` client, named `resource:action`
  (`users:read`, `roles:write`, ...). Every API endpoint requires one.
- **Roles** are realm roles whose composites are permissions. Users get permissions only
  through roles; Keycloak expands them into `resource_access.gateway.roles` in the token.
- `admin` always holds every permission (new permissions are granted to it automatically).
  `admin`, `user` and the built-in permissions are *system* entries and can't be deleted.
- user-service syncs this catalog into Keycloak on every boot, so existing Keycloak
  databases are upgraded too.

| Endpoint | Permission |
| --- | --- |
| `GET /api/users[/{id}]` | `users:read` |
| `POST /api/users`, `PUT /api/users/{id}`, `POST /api/users/{id}/reset-password` | `users:write` |
| `DELETE /api/users/{id}` | `users:delete` |
| `GET /api/roles[/{name}]` | `roles:read` (`/{name}/users` also needs `users:read`) |
| `POST/PUT/DELETE /api/roles[/{name}]` | `roles:write` |
| `GET /api/permissions[/{name}]` | `permissions:read` |
| `POST/PUT/DELETE /api/permissions[/{name}]` | `permissions:write` |

## Get a test token (realm `smartconnect`, seed user `admin.seed` / `SmartConnect123!`)

```
curl -s -X POST \
  http://localhost:8080/realms/smartconnect/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'client_id=login-app' \
  -d 'username=admin.seed' \
  -d 'password=SmartConnect123!' | jq -r .access_token
```

## Tests

```
docker run --rm -v "$PWD":/w -w /w maven:3.9-eclipse-temurin-17 mvn -B verify
```
