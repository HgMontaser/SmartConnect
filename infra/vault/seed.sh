#!/bin/sh
# Loads generated secrets into Vault (run by entrypoint.sh, as root, on every start).
# Each service's Keycloak client secret is stored as `smartconnect.client-secret` under
# secret/smartconnect/<svc>, readable only by that service's AppRole.
#
# Only real secrets live in Vault. Non-secret config (issuer URI, Keycloak server URL, rate
# limits, ...) is in Consul KV (infra/consul/config).
set -e

for dir in /secrets/services/*/; do
  svc=$(basename "$dir")
  if [ -s "$dir/client-secret" ]; then
    vault kv put "secret/smartconnect/$svc" smartconnect.client-secret="$(cat "$dir/client-secret")" >/dev/null
    echo "seeded secret/smartconnect/$svc"
  fi
done
echo "vault seed complete"
