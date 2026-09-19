# SmartConnect — Build Prompt for Claude Code

Paste this whole thing into Claude Code in the empty project folder. It's written as a direct instruction set — follow it in order, verify each stage boots before moving to the next.

---

## Mission

Build **SmartConnect**: a secure gateway that handles both synchronous (REST) and asynchronous (Kafka) communication, sitting in front of business logic that will be added later. Two client apps, one shared identity layer (Keycloak), secrets centralized in Vault, shared cross-cutting concerns (auth, correlation IDs, error handling) in a common library, rate limiting at the edge, metrics/logs centralized in Elastic.

## Architecture

```
                        ┌─────────────────┐
                        │   Keycloak       │  (identity, SSO, realm: smartconnect)
                        └────────┬─────────┘
                                 │ OIDC / JWT
        ┌────────────────────────┴───────────────────────┐
        │                                                  │
┌───────▼────────┐                                ┌────────▼────────┐
│  login-app      │  standalone, PKCE flow         │  backoffice-app │  React admin panel
│  (React)        │  → redirect to business app     │  (React)        │  manages users
└───────┬─────────┘    home page on success         └────────┬────────┘
        │                                                     │
        │                REST (JWT bearer)                    │ REST + live updates
        └───────────────────────┬─────────────────────────────┘
                                 │
                        ┌────────▼─────────┐
                        │   api-gateway     │  Spring Cloud Gateway
                        │  (resource server,│  validates JWT, routes sync,
                        │   Vault-backed)   │  rate limits, bridges async via Kafka
                        └────────┬─────────┘
                                 │  (all services also re-validate JWT
                                 │   + inherit correlation-id filter from commons)
                 ┌───────────────┼────────────────┐
                 │                                 │
        ┌────────▼────────┐             ┌──────────▼─────────┐
        │  user-service    │──Kafka────▶│ notification-service│
        │ (Keycloak admin  │  producer  │  (consumer stub)     │
        │  API + Kafka)    │            └──────────────────────┘
        └──────────────────┘
                 │
        ┌────────▼────────┐
        │  Vault            │  KV v2 secrets: keycloak admin creds,
        │  (dev mode)       │  kafka creds, db creds per service
        └───────────────────┘

  cross-cutting (not runtime services):
  ┌─────────────────────┐   ┌───────────────────────┐   ┌───────────────────────┐
  │ smartconnect-commons │   │ Redis (rate limiting)  │   │ Elasticsearch + Kibana │
  │ shared library:       │   │ used only by           │   │ + Filebeat             │
  │ - resource-server cfg │   │ api-gateway            │   │ services ship metrics  │
  │ - correlation-id      │   │ RequestRateLimiter     │   │ (Micrometer) + logs    │
  │   interceptor         │   └───────────────────────┘   │ (structured, w/ corr.  │
  │ - Kafka event DTOs    │                                │  id) here              │
  │ - error response map  │                                └───────────────────────┘
  └───────────────────────┘
```

## Tech stack

- **Backend:** Java 17, Spring Boot 3.3.x, Spring Cloud Gateway, Spring Cloud Vault, Spring Kafka, Spring Security OAuth2 Resource Server
- **Identity:** Keycloak 25+, realm `smartconnect`
- **Secrets:** HashiCorp Vault, KV v2, dev mode for local
- **Messaging:** Apache Kafka, KRaft mode (no Zookeeper), single broker for dev
- **Frontend:** React 18 + Vite, `keycloak-js` adapter
- **Rate limiting:** Redis + Spring Cloud Gateway `RequestRateLimiter`
- **Observability:** Elasticsearch + Kibana + Filebeat (logs), Micrometer Elastic registry (metrics), Spring Boot Actuator
- **Shared code:** `smartconnect-commons` — internal library, not a deployed service
- **Infra:** Docker Compose for everything above

## Repo structure

```
smartconnect/
├── docker-compose.yml
├── infra/
│   ├── keycloak/realm-export.json      # realm, clients, roles pre-baked
│   ├── vault/seed.sh                   # pushes initial secrets on startup
│   └── elastic/filebeat.yml            # log shipping config
├── libs/
│   └── commons/                        # shared library, all services depend on it
├── services/
│   ├── api-gateway/
│   ├── user-service/
│   └── notification-service/
├── apps/
│   ├── login-app/
│   └── backoffice-app/
└── README.md
```

## Build order — execute in this sequence, confirm each step works before the next

### 1. Docker Compose infra
- Vault (dev mode, root token fixed for local dev, KV v2 mounted at `secret/`)
- Keycloak (import `realm-export.json` on boot — realm `smartconnect`)
- Kafka (KRaft, single node, plaintext listener for dev)
- Postgres (Keycloak's own DB, separate from any business DB)
- Redis (rate limiter store, used only by `api-gateway`)
- Elasticsearch + Kibana + Filebeat (metrics/log stack — bring up now so every service can ship to it from day one instead of bolting it on later)

### 2. Keycloak realm (`infra/keycloak/realm-export.json`)
Clients:
- `login-app` — public client, PKCE required, redirect URIs to itself + business app home
- `backoffice-app` — public client, PKCE required
- `gateway` — bearer-only / resource server, used only for token audience validation

Roles: `admin`, `user`. One seed admin user for testing.

### 3. Vault seed (`infra/vault/seed.sh`)
Push to `secret/smartconnect/user-service`: Keycloak admin client id/secret.
Push to `secret/smartconnect/api-gateway`: Keycloak issuer URI, JWK set URI.
No secrets hardcoded anywhere in service code or `application.yml` — everything pulled via `spring-cloud-starter-vault-config` at bootstrap.

### 4. `libs/commons` — shared library, build this before any service that uses it
Every backend service depends on this as a local Maven module (not published anywhere external). It contains, once, what would otherwise be copy-pasted into every service:
- **Resource-server security config**: OAuth2 resource server auto-config pre-wired for the Keycloak issuer, plus a role mapper that turns Keycloak realm roles into Spring `GrantedAuthority`. Each service just adds the starter dependency and its own `application.yml` issuer URI — no security boilerplate rewritten per service.
- **Correlation-ID filter/interceptor**: generates or propagates an `X-Correlation-Id` header on every inbound request, puts it in SLF4J MDC so every log line carries it, and forwards it on any outbound call (REST client, Kafka producer headers). This is what lets you trace one request across `api-gateway` → `user-service` → Kafka → `notification-service` in Kibana.
- **Kafka event DTOs/schemas**: the `user-events` payload classes, shared by producer (`user-service`) and consumers, so they can never drift out of sync.
- **Standard error response mapper**: one `@ControllerAdvice` shape for exceptions → consistent JSON error body across every service.
- **Shared Actuator/Micrometer config**: common tags (service name, environment) applied to every metric, common `/actuator/health`, `/actuator/prometheus` (or Elastic registry) exposure.

### 5. `api-gateway` (Spring Cloud Gateway)
- Resource server (via commons config): validates JWT against Keycloak realm's JWK endpoint
- Routes: `/api/users/**` → `user-service`, `/ws/**` → websocket bridge for live Kafka events to `backoffice-app`
- Global filter: reject unsigned/expired/wrong-audience tokens before routing; correlation-id filter from commons runs first so even rejected requests are traceable
- **Rate limiting**: `RequestRateLimiter` filter backed by Redis, keyed per authenticated user (fall back to per-IP for unauthenticated routes like login), token-bucket config per route — this is the only place rate limiting lives, don't duplicate it downstream
- Pulls its config from Vault at boot (fail fast if Vault unreachable)
- Ships metrics + logs to the Elastic stack via commons' shared config

### 6. `user-service`
- REST CRUD endpoints for users, backed by Keycloak Admin REST API (not a separate user DB — Keycloak is the source of truth)
- On every create/update/delete, publish to Kafka topic `user-events` (using the shared DTO from commons) with event type (`USER_CREATED`, `USER_UPDATED`, `USER_DELETED`) and payload, correlation ID propagated into the Kafka headers
- Also a resource server in its own right (via commons) — re-validates the JWT even though the gateway already checked; never trust the network boundary alone
- Vault-backed for Keycloak admin credentials
- Only reachable through `api-gateway`, never exposed directly

### 7. `notification-service`
- Kafka consumer on `user-events` (shared DTO from commons), logs/processes events — this is the proof that async plumbing works end to end. Stub the actual business reaction; that's for later.
- Reads the correlation ID off the Kafka message headers and puts it back in MDC, so the trace continues into this service's logs too

### 8. `login-app` (standalone React)
- Own deploy target, e.g. `auth.smartconnect.local`
- Uses `keycloak-js`, Authorization Code + PKCE flow against the `login-app` client
- On successful handshake (valid token confirmed): redirect the browser to the business app's home URL (configurable env var `BUSINESS_APP_HOME_URL`). Rely on Keycloak's SSO cookie so the business app can pick up the session silently (`checkSsoSilently`) rather than passing tokens in the URL.
- No app shell beyond the login screen — this app does one job.

### 9. `backoffice-app` (React)
- Admin panel: list/create/update/delete users, calling `api-gateway` → `user-service`
- Subscribes to the gateway's websocket bridge for live updates when `user-events` fire (so two admins see changes in real time without polling)
- Auth via the `backoffice-app` Keycloak client, same PKCE pattern

### 10. Wire up Kibana dashboards (basic)
- One dashboard: requests per service, error rate, p95 latency (from Micrometer/Actuator metrics)
- One saved search: trace a single correlation ID across all services' logs — this is your smoke test that the whole observability chain works, not just that Elastic is running

## Constraints — do not violate these

- No secret ever lives in a Dockerfile, `application.yml`, or frontend `.env` checked into git — Vault or runtime env only
- `user-service` is never called directly by a frontend — always through `api-gateway`
- Rate limiting lives only in `api-gateway` — never re-implemented in a downstream service
- Every backend service is its own resource server (via commons) — the gateway checking the JWT does not exempt a service from checking it again
- `commons` stays a plain shared library — it never becomes a runtime service, never gets its own port, never gets called over the network
- Keep each service's `pom.xml` minimal beyond commons — don't drag in starters that aren't used yet; business logic comes later, this phase is plumbing only
- Every service should fail fast and loudly if Vault, Keycloak, or Kafka isn't reachable at boot — no silent degraded mode

## Order of operations for you (Claude Code)

Build and verify `docker-compose up` for infra first (including Redis and the Elastic stack — don't defer them). Then build `commons` and confirm it compiles standalone. Then `api-gateway`, confirm it rejects unauthenticated requests and enforces the rate limit. Then `user-service`, confirm Kafka messages land and a correlation ID survives from gateway request to Kafka message. Then `notification-service` as consumer proof, confirm the correlation ID shows up in its logs too. Then wire the Kibana dashboard as a sanity check on the whole chain. Then the two React apps last, since they depend on everything else being live. Ask before inventing business-app details — that app is out of scope, we only need the redirect contract.
