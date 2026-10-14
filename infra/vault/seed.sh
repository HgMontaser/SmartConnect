#!/bin/sh
set -e

echo "waiting for vault to be ready..."
until vault status >/dev/null 2>&1; do
  sleep 1
done

echo "enabling kv-v2 secrets engine at secret/ (no-op if already enabled)"
vault secrets enable -path=secret -version=2 kv 2>/dev/null || true

echo "seeding secret/smartconnect/user-service"
# Keys are written to match Spring property paths 1:1 so spring-cloud-vault-config binds
# them directly with no renaming layer. keycloak.admin.* configures the Keycloak Admin REST
# API client (the `user-service` service account, client-credentials grant, scoped to the
# smartconnect realm - created by infra/keycloak/init.sh) that user-service uses as its
# source of truth for users; keycloak.target-realm is the realm those users live in.
vault kv put secret/smartconnect/user-service \
  keycloak.admin.server-url="http://keycloak:8080" \
  keycloak.admin.realm="smartconnect" \
  keycloak.admin.client-id="user-service" \
  keycloak.admin.client-secret="$USER_SERVICE_CLIENT_SECRET" \
  keycloak.target-realm="smartconnect"

# Only real secrets live here. Non-secret config (issuer URI, rate limits, ...) is in Consul KV
# (infra/consul/config), so a new service needs a Vault entry only if it has secrets.

echo "vault seed complete"
