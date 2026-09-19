# SmartConnect

Secure gateway system: Keycloak identity, Vault secrets, Kafka async messaging, Redis-backed rate limiting, Elastic observability.

**Status: Phase 1 (backend + infra only).** Frontend apps (`login-app`, `backoffice-app`) are out of scope until phase 2.

## Repo structure

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

## Bring up infra

```
docker compose up -d
```

Reachable at:
- Keycloak admin console: http://localhost:8080 (admin / admin)
- Vault UI: http://localhost:8200 (token: `smartconnect-root-token`)
- Kibana: http://localhost:5601
- Elasticsearch: http://localhost:9200

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

## Build order

1. Docker Compose infra
2. Keycloak realm
3. Vault seed
4. `libs/commons`
5. `api-gateway`
6. `user-service`
7. `notification-service`
8. Kibana sanity check (trace one correlation ID across all three services)
