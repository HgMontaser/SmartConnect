#!/usr/bin/env bash
# Prints a generated secret (they live only in Docker volumes, never in git).
#
#   scripts/show-secret.sh vault-root-token       Vault UI / CLI root token
#   scripts/show-secret.sh consul-root-token      Consul UI / CLI root token
#   scripts/show-secret.sh <service> <name>       e.g. user-service client-secret
set -euo pipefail

case "${1:-}" in
  vault-root-token) docker exec smartconnect-vault cat /vault/data/init/root-token ;;
  consul-root-token) docker exec smartconnect-vault cat /secrets/infra/consul-root-token ;;
  "") echo "usage: scripts/show-secret.sh vault-root-token | consul-root-token | <service> <name>" >&2; exit 1 ;;
  *) docker exec smartconnect-vault cat "/secrets/services/$1/${2:?name, e.g. client-secret}" ;;
esac
echo
