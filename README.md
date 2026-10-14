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
- Vault UI: http://localhost:8200 (token: `scripts/show-secret.sh vault-root-token`)
- Consul UI: http://localhost:8500 (token: `scripts/show-secret.sh consul-root-token`)
- Jaeger (traces): http://localhost:16686
- Kibana: http://localhost:5601 (dashboard: SmartConnect overview)
- Elasticsearch: http://localhost:9200

Rebuild a single piece after a change, e.g. `docker compose up -d --build user-service`.

## Backoffice

http://localhost:5174 - each page appears only with its permission:

| Page | Shows | Permission |
| --- | --- | --- |
| Users / Roles / Permissions | access management | `users:*`, `roles:*`, `permissions:*` |
| Gateway health | every Consul service, its instances and health, its gateway routes, its circuit breaker (api-gateway, `/api/platform/services`), and its traffic: req/min, 5xx share and p95 latency over the last 5 minutes with a one-hour trend (api-gateway, `/api/platform/metrics?range=15m\|1h\|6h\|24h`, read from the metrics in Elasticsearch) | `platform:read` |
| Traffic | requests/min, 5xx share and p95 latency of every service as small multiples with one shared crosshair, over 15 min / 1 h / 6 h / 24 h; open a service for its endpoints (busiest, slowest, most 5xx), or the gateway for its routes incl. how much it rate-limited (api-gateway, `/api/platform/metrics` and `/api/platform/metrics/endpoints?service=`) | `platform:read` |
| Sessions | active Keycloak SSO sessions; revoke one or sign a user out everywhere (user-service, `/api/sessions`) | `sessions:read` / `sessions:write` |
| Events | Kafka in the browser. **Messages** - **Live activity**: what is happening on the platform in words, one line per request ("admin.seed created user alice", joined from its domain event and audit record by correlation id), pushed over the WebSocket as it happens, failures included; or one topic newest/oldest first per partition with paging and search, each record with its key, headers, payload and SmartConnect event fields (and **Trace the request** with `logs:read`); **Topics** - partitions, message counts, retention, consumer groups; **Consumers** - groups, members and lag (notification-service, `/api/events/recent`, `/api/events/topics[/{topic}/messages]`, `/api/events/groups`) | `events:read` |
| Audit | **Actions**: every change through any SmartConnect API with the person who made it, denied attempts included. **Keycloak changes**: Keycloak's own admin events (30 days, also console changes). **Sign-ins**: logins and failures (30 days) | `audit:read` |
| Request trace (drawer) | **Trace** on an Events message or an Audit action row: the request's path through the services, a timeline per service and every log line it left (framework lines hidden by default), with links to its Jaeger traces (api-gateway, `/api/platform/traces/{correlationId}`, read from the logs in Elasticsearch) | `logs:read` |

### Audit trail

- Every servlet service records each POST/PUT/PATCH/DELETE that reaches a controller - caller,
  endpoint, path variables, status - to the `smartconnect.audit` log and as an
  `audit.performed` event (commons `AuditInterceptor`, on by default; `smartconnect.audit.enabled`).
- Keycloak stores admin events (with change details) and sign-in events for 30 days and logs
  them at INFO, so they reach Kibana too.
- Keycloak shows backoffice changes as made by `service-account-user-service`; the person is in
  the Actions tab / `audit.performed` events.

## Secrets

Nothing secret is in git. On first `docker compose up`, the one-shot `secrets-init` container
generates every secret into the `secrets` Docker volume and never overwrites them afterwards
(`infra/secrets/init.sh`):

- per service listed in `infra/secrets/services.list` (or `examples/<app>/services.list`): a
  Consul ACL token, a Vault AppRole (role-id + secret-id), and optionally a Keycloak client
  secret;
- the Consul root token.

Each service container mounts **only its own folder** of that volume. Vault runs in server
mode (data on the `vault-data` volume, unsealed automatically on start - locally only; use a
KMS auto-unseal in production) and each service logs in with its own AppRole, whose policy can
only read `secret/smartconnect/<service>`. Client secrets are set on the Keycloak clients by
`keycloak-init` and served to the service by Vault, from the same generated file.

Print one with `scripts/show-secret.sh` (`vault-root-token`, `consul-root-token`, or
`<service> <name>`). To rotate everything locally, `docker compose down` and remove the
`smartconnect_secrets` and `smartconnect_vault-data` volumes.

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
      # prefix a pattern with ws: for WebSocket paths, e.g. /api/events/**,ws:/ws/**
  ```
  A path already served by another service is refused (logged), never shared, and
  `/api/platform/**` is reserved for the gateway itself.
- **Config:** secrets come from Vault; non-secret config comes from Consul KV, seeded from
  `infra/consul/config/` on every Consul start — `application.yml` is shared by all services,
  `<service-name>.yml` by that service only. Changes in the Consul UI apply live within a few
  seconds (e.g. the gateway's rate limits); to make one permanent, edit the file and run
  `docker compose restart consul`.
- Fleet-wide Consul defaults live in commons (`ServiceDiscoveryDefaults`), so a service only
  needs the two Consul starters and `spring.config.import: "vault://,consul:"`.
- **ACLs, deny by default.** Each service gets its own generated Consul token (see Secrets
  below). On every start Consul creates, per service, a token that may register only that
  service, discover the others, and read only `config/application` and `config/<service>` -
  no writes. Anonymous requests see and do nothing.

## Events

Services talk asynchronously through Kafka using one envelope (`EventEnvelope`, in commons):
`id`, `type`, `version`, `source`, `occurredAt`, `correlationId`, and the business `payload`.

- **Types** are `<domain>.<event>` (e.g. `user.updated`). All events of a domain share the topic
  `events.<domain>`, keyed by entity id so one entity's events stay in order.
- **Consumers** use the `EventListeners.FACTORY` listener factory: each service is its own
  consumer group (it sees every event once), traces and correlation ids carry over, and a
  listener that throws is retried 3 times (0.5s, 1s, 2s) and then the record is parked on
  `events.<domain>.<service>.dlt` with the error in its headers - one bad message never blocks
  the rest. Undecodable messages go straight there.
- Make consumers idempotent with `event.id()`; bump `version` on breaking payload changes.
- A service that builds its own listener factory or `KafkaTemplate` must turn tracing on itself
  (`setObservationEnabled(true)`; for a template that isn't a bean, `ObservedKafkaTemplates`):
  `spring.kafka.*.observation-enabled` only reaches Boot's own ones.
- The platform uses it too: user-service publishes `user.created/updated/deleted` on
  `events.user`. notification-service pushes **every** event from every topic to the backoffice
  over one WebSocket (`/ws/events`) as it lands: user events to every signed-in session (the
  users table applies them), all others - metadata only - to sessions with `events:read`.
- Kafka keeps its log on the `kafka-data` volume (`KAFKA_LOG_DIRS`), so events survive a
  container recreate.
- Topics are auto-created by the broker locally; create them explicitly (partitions,
  retention) in production.

## Tracing

Every service sends OpenTelemetry spans to Jaeger (http://localhost:16686). One request is one
trace across the gateway, service-to-service calls (`ServiceClients`) and Kafka (producer to
consumer). Every log line carries `trace=<id>`, so a trace in Jaeger leads to its logs in Kibana
(`trace.id : <id>`). Sampling and the endpoint are in `infra/consul/config/application.yml`.

## Logs and metrics (Kibana)

Filebeat ships every container's log to `smartconnect-logs-*` and lifts the fleet log format's
`log.level`, `log.logger`, `trace.id`, `span.id` and `correlation_id` into fields
(`infra/elastic/filebeat.yml`). Services push Micrometer metrics to `smartconnect-metrics-*`
every 30s. Every container logs to `json-file` explicitly (`logging:` in compose) - Filebeat
can't read Docker's `local` driver, which newer Docker Desktop uses by default.

On every start `kibana-init` imports `infra/kibana/smartconnect.ndjson`:
- **SmartConnect overview** dashboard: requests, 5xx error rate and p95 latency per service,
  and ERROR log lines per container.
- **Trace a correlation ID** saved search (the backoffice's request trace drawer shows the same, `logs:read`): replace `*` in `correlation_id : *` with the
  `X-Correlation-Id` of a response to follow that request through every service, Kafka included.

The import overwrites those objects, so the file is the source of truth: change them in Kibana,
then re-export (the command is in `infra/kibana/init.sh`), or "Save as" to keep your own copy.

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
- user-service talks to Keycloak (26.x) as its own `user-service` service account, limited to
  the `smartconnect` realm, and - through Keycloak's fine-grained admin permissions - to
  managing only the `gateway` client among clients: it can't edit other apps (redirect URIs,
  secrets) or create clients. It still needs realm-wide `manage-users` and `manage-realm` (realm
  roles have no finer grain). Set up by the one-shot `keycloak-init` job
  (`infra/keycloak/init.sh`), the only user of the master admin credentials.

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
| Publish / consume events | inject `EventPublisher`: `events.publish("billing.invoice-paid", id, payload)`; consume with `@KafkaListener(topics = "events.billing", containerFactory = EventListeners.FACTORY)` taking an `EventEnvelope` (see Events below) |
| Secrets | generated per environment - list the service in `services.list` (the script does); add `client-secret` for a Keycloak client secret, served by Vault as `smartconnect.client-secret` |
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
mvn verify              # unit tests + integration tests (needs Docker, ~1 min)
mvn verify -DskipITs    # unit tests only
```

- `*Test` classes are unit tests. `*IT` classes run against real containers (Testcontainers):
  - commons `MessagingIT` - real Kafka: publish/consume, retries then dead-letter, undecodable
    records straight to dead-letter.
  - api-gateway `GatewayRoutingIT` - real Consul (seeded from `infra/consul/config`), Vault and
    Redis plus a stub service registered in Consul: discovered routing, 401/404, 504 on timeout,
    503 and circuit opening when the service dies.
  - user-service `UserServiceKeycloakIT` - real Keycloak (the repo's realm export and
    `infra/keycloak/init.sh`), Kafka, Consul and Vault, with real tokens: permission bootstrap,
    roles CRUD and the admin lock, validate-before-write, catalog sync and orphaning, and
    permission enforcement.
- CI runs `mvn verify`, so integration tests gate every push.
