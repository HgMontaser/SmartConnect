# SmartConnect

Secure gateway system: Keycloak identity, Vault secrets, Consul service discovery and shared config, Kafka async messaging, Redis-backed rate limiting, Elastic observability.

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
- Consul UI: http://localhost:8500 (registered services, health, KV config)
- Kibana: http://localhost:5601
- Elasticsearch: http://localhost:9200

Rebuild a single piece after a change, e.g. `docker compose up -d --build user-service`.

## Discovery, routing and config

- **Consul** is the service registry. Every backend service registers itself on startup (by IP,
  with a heartbeat carrying its actuator health) and re-registers if Consul restarts.
- **api-gateway has no hard-coded routes.** A service asks for routes in its own config and
  the gateway builds load-balanced routes from Consul within seconds of the service becoming
  healthy, and drops them when it goes away:
  ```yaml
  smartconnect:
    gateway:
      routes: /api/users/**,/api/roles/**   # comma-separated path patterns
      scheme: ws                             # only for WebSocket services (default http)
  ```
  A path already served by another service is refused (logged), never shared.
- **Config:** secrets come from Vault; non-secret config comes from Consul KV, seeded from
  `infra/consul/config/` on every Consul start — `application.yml` is shared by all services,
  `<service-name>.yml` by that service only. Changes in the Consul UI apply live within a few
  seconds (e.g. the gateway's rate limits); to make one permanent, edit the file and run
  `docker compose restart consul`.
- Fleet-wide Consul defaults live in commons (`ServiceDiscoveryDefaults`), so a service only
  needs the two Consul starters and `spring.config.import: "vault://,consul:"`.

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

## Adding a business service

A business service defines its own permissions; SmartConnect decides who gets them.

1. Depend on `smartconnect-commons` and `spring-kafka`, and set the service name:
   ```yaml
   smartconnect:
     permissions:
       service: billing-service
       catalog:                       # omit if the permissions live in your own database
         - name: billing:read
           description: View invoices
   ```
2. Keeping permissions in your own database instead? Register a `PermissionCatalogProvider`
   bean that reads them (e.g. `SELECT name, description FROM permissions`), and call
   `PermissionCatalogPublisher.publish()` after every change to that table.
3. Guard endpoints with `@PreAuthorize("hasAuthority('billing:read')")`. Startup fails if a
   checked permission isn't in the catalog (SmartConnect's system permissions are exempt).
4. Add `spring-cloud-starter-consul-discovery` + `spring-cloud-starter-consul-config`, set
   `spring.config.import: "vault://,consul:"` and `smartconnect.gateway.routes` — the gateway
   routes to it automatically. Then add a Vault entry in `infra/vault/seed.sh` (run
   `docker compose restart vault` afterwards so it re-seeds) and a compose service with
   `CONSUL_HOST: consul`.

What happens: on startup (and on every `publish()`) commons sends the full catalog to the
compacted `permission-catalog` Kafka topic. user-service syncs it into Keycloak: new
permissions are created and granted to `admin`, descriptions are updated, and permissions the
service no longer declares are flagged **orphaned** (never deleted automatically). In the
backoffice you add them to roles as usual.

Rules:
- A service owns whole resources: the first to declare `billing:*` owns it, and other services
  can't declare `billing:anything`. `users`, `roles` and `permissions` are reserved.
- Service-owned permissions can't be created, edited or deleted in the backoffice, except that
  an orphaned one can be deleted.
- Definitions flow one way only, service → SmartConnect. Role assignments live only in Keycloak.

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
