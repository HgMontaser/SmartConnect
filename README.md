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
- Jaeger (traces): http://localhost:16686
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

## Tracing

Every service sends OpenTelemetry spans to Jaeger (http://localhost:16686). One request is one
trace across the gateway, service-to-service calls (`ServiceClients`) and Kafka (producer to
consumer). Every log line carries `trace=<id>`, so a trace in Jaeger leads to its logs in Kibana
(search for the trace id). Sampling and the endpoint are in `infra/consul/config/application.yml`.

## Resilience (api-gateway)

Every discovered HTTP route gets, in order:
- **Rate limiting** per user (or IP) - `smartconnect.gateway.rate-limit` in Consul.
- **Timeouts** - connect 2s, response 10s by default (`spring.cloud.gateway.httpclient` in
  Consul). A service that needs longer sets `smartconnect.gateway.timeout: 30s` in its config.
- **A circuit breaker per service** - after repeated infrastructure failures (connection errors,
  timeouts, 502/503/504) the gateway refuses calls for 10s so the service can recover, then lets
  a few through to test it. Tuned under `resilience4j.circuitbreaker` in Consul.
- **Retries for GET only**, on connection errors and 502/503, twice with backoff. Writes are
  never retried; neither are timeouts.

Failures come back in the standard error JSON: **504** when the service was too slow, **503**
when it's down or its breaker is open. A service that disappears keeps its route for 10 minutes,
so callers see 503 while it restarts rather than 404.

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
- user-service talks to Keycloak as its own `user-service` service account, limited to the
  `smartconnect` realm (created by the one-shot `keycloak-init` job, `infra/keycloak/init.sh`).
  The master admin is only used by that job.

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

```
scripts/new-service.sh billing-service            # platform service under services/
scripts/new-service.sh billing-service --example my-app   # or under examples/my-app/
docker compose up -d --build billing-service
```

The generated service is empty (one protected `GET /api/billing/status`) but already plugged in:
Vault + Consul config, Consul registration, a gateway route for `/api/billing/**`, JWT security,
and its own permission catalog (`billing:read`, `billing:write`). Grant those to a role in the
backoffice and it's reachable through the gateway.

Plug-in points a service uses, all declared in its own repo folder:

| Need | How |
| --- | --- |
| Gateway route | `smartconnect.gateway.routes` (+ `scheme: ws` for WebSockets) |
| Permissions | `smartconnect.permissions.catalog`, or a `PermissionCatalogProvider` bean reading its own DB (call `PermissionCatalogPublisher.publish()` after changes) |
| Call another service | inject `ServiceClients`: `asCaller()` forwards the user's token, `asService()` uses the service's own token. URLs use Consul names: `http://user-service/api/users` |
| Keycloak client (frontend, service account) | a client JSON in `infra/keycloak/clients/` (or `examples/<app>/keycloak/`); optional `<clientId>.permissions` grants permissions to its service account. Applied by `keycloak-init` on every `docker compose up` |
| Secrets | `infra/vault/seed.sh` under `secret/smartconnect/<service>` (then `docker compose restart vault`) |
| Non-secret config | `infra/consul/config/<service>.yml` |

Startup fails if an `@PreAuthorize` check uses a permission missing from the catalog
(SmartConnect's system permissions are exempt).

### Demo app

`examples/demo-app` is a mock business app built with the script, used to exercise all of the
above: a backend (`demo-service`) and a React frontend (`demo-web`, http://localhost:5175)
with its own Keycloak client and SSO. It never starts with the platform:

```
docker compose --profile demo-app up -d --build
```

Sign in as `admin.seed` - the Connectivity card calls user-service from demo-service with both
token modes. Delete the folder (and its line in `docker-compose.yml` / `pom.xml`) to remove it.

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
