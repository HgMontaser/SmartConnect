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
# API client (admin-cli, password grant, against the master realm) that user-service uses
# as its source of truth for users; keycloak.target-realm is the realm those users live in.
vault kv put secret/smartconnect/user-service \
  spring.security.oauth2.resourceserver.jwt.issuer-uri="http://keycloak:8080/realms/smartconnect" \
  keycloak.admin.server-url="http://keycloak:8080" \
  keycloak.admin.realm="master" \
  keycloak.admin.client-id="admin-cli" \
  keycloak.admin.username="admin" \
  keycloak.admin.password="admin" \
  keycloak.target-realm="smartconnect"

echo "seeding secret/smartconnect/api-gateway"
vault kv put secret/smartconnect/api-gateway \
  spring.security.oauth2.resourceserver.jwt.issuer-uri="http://keycloak:8080/realms/smartconnect"

echo "seeding secret/smartconnect/notification-service"
vault kv put secret/smartconnect/notification-service \
  spring.security.oauth2.resourceserver.jwt.issuer-uri="http://keycloak:8080/realms/smartconnect"

echo "vault seed complete"
