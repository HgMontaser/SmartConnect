# SmartConnect — Phase 1: Backend Only

Paste this into Claude Code. **Backend and infra only for now — do not touch `apps/login-app` or `apps/backoffice-app`, we'll come back for those once this is verified end to end.**

---

## Mission

Build the backend plumbing for SmartConnect: infra, shared library, gateway, and two services — secure, observable, rate-limited. No business logic yet, no frontend yet. This phase is done when a request can be traced from an authenticated call at the gateway, through `user-service`, onto Kafka, into `notification-service`, and show up as one correlation ID in Kibana.

## Repo structure (this phase)

```
smartconnect/
├── docker-compose.yml
├── infra/
│   ├── keycloak/realm-export.json
│   ├── vault/seed.sh
│   └── elastic/filebeat.yml
├── libs/
│   └── commons/
├── services/
│   ├── api-gateway/
│   ├── user-service/
│   └── notification-service/
└── README.md
```

(`apps/` folder doesn't exist yet — that's phase 2.)

## Tech stack

Java 17, Spring Boot 3.3.x, Spring Cloud Gateway, Spring Cloud Vault, Spring Kafka, Spring Security OAuth2 Resource Server. Keycloak 25+, Vault (dev mode), Kafka (KRaft), Postgres (Keycloak's DB), Redis (rate limiting), Elasticsearch + Kibana + Filebeat.

## Build order — do these in sequence, confirm each one before moving on

### 1. Docker Compose infra
- Vault (dev mode, fixed root token, KV v2 at `secret/`)
- Keycloak (import `realm-export.json` — realm `smartconnect`, clients `gateway`, `backoffice-app`, `login-app` even though those apps don't exist yet, so the realm is ready when we need it)
- Kafka (KRaft, single node, plaintext for dev)
- Postgres (Keycloak's DB only)
- Redis (rate limiter store)
- Elasticsearch + Kibana + Filebeat

Confirm: everything comes up clean with `docker-compose up`, Keycloak admin console reachable, Vault UI reachable.

### 2. Keycloak realm
Clients: `gateway` (bearer-only, resource server), `backoffice-app` and `login-app` (public, PKCE — config only, unused until phase 2). Roles: `admin`, `user`. One seed admin user for testing tokens via `curl`/Postman.

### 3. Vault seed
Push Keycloak admin client id/secret to `secret/smartconnect/user-service`. Push Keycloak issuer/JWK URIs to `secret/smartconnect/api-gateway`. No secrets in code or `application.yml` — pulled via `spring-cloud-starter-vault-config` at boot.

### 4. `libs/commons` — build and confirm it compiles standalone before touching any service
- Resource-server security config, pre-wired for the Keycloak issuer, with a role mapper (Keycloak realm roles → Spring `GrantedAuthority`)
- Correlation-ID filter/interceptor: generates or propagates `X-Correlation-Id`, puts it in SLF4J MDC, forwards it on outbound REST calls and Kafka producer headers
- Kafka event DTOs for `user-events` (shared by producer and consumer, so they can't drift)
- Standard `@ControllerAdvice` error response mapper
- Shared Actuator/Micrometer config (common tags: service name, environment)

### 5. `api-gateway`
- Resource server via commons, validates JWT against Keycloak's JWK endpoint
- Route: `/api/users/**` → `user-service` (route `/ws/**` too, even with nothing behind it yet — wire the shape now)
- Rejects unsigned/expired/wrong-audience tokens before routing; correlation-id filter runs first so rejections are still traceable
- Rate limiting: `RequestRateLimiter` backed by Redis, keyed per authenticated user, fallback per-IP for unauthenticated calls
- Config pulled from Vault at boot, fails fast if Vault's unreachable
- Ships metrics/logs to Elastic via commons' shared config

Confirm: an unauthenticated request gets rejected, a valid token routes through, hammering it trips the rate limit.

### 6. `user-service`
- CRUD for users via Keycloak Admin REST API — Keycloak is the source of truth, no separate user table
- Every mutation publishes to Kafka topic `user-events` (shared DTO from commons), correlation ID propagated into the Kafka headers
- Its own resource server too (via commons) — re-validates the JWT independently of the gateway
- Vault-backed for Keycloak admin credentials
- Only reachable through the gateway

Confirm: a create/update/delete through the gateway lands a message on `user-events` with the same correlation ID as the originating request.

### 7. `notification-service`
- Kafka consumer on `user-events` (shared DTO), reads the correlation ID off message headers back into MDC, logs the event. Stub the actual reaction — that's for later.

Confirm: the correlation ID from step 6 shows up in this service's logs too.

### 8. Kibana sanity check
One saved search: pull every log line for a single correlation ID across all three services. One basic dashboard: requests/min, error rate, p95 latency per service. This is the actual finish line for phase 1 — not "containers are running," but "I can trace one request end to end."

## Constraints

- No secret in a Dockerfile, `application.yml`, or anything checked into git — Vault or runtime env only
- `user-service` never called directly, only through the gateway
- Rate limiting lives only in the gateway
- Every backend service is its own resource server — the gateway checking the token doesn't exempt a service from checking it again
- `commons` is a plain library — no port, no network calls to it, ever
- Fail fast and loud if Vault, Keycloak, or Kafka isn't reachable at boot

## Stop condition

Stop after step 8 and report back. Don't start on `login-app` or `backoffice-app` — that's a separate conversation once this is confirmed working.
