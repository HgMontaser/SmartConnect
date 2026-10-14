#!/bin/sh
# Generates every SmartConnect secret on first run into the `secrets` volume, and never
# overwrites one that exists - so nothing secret lives in git, and restarts keep the values.
#
#   /secrets/infra/consul-root-token           Consul ACL root (UI login, Consul's own CLI)
#   /secrets/services/<svc>/consul-token       the service's Consul ACL token
#   /secrets/services/<svc>/vault-role-id      the service's Vault AppRole login
#   /secrets/services/<svc>/vault-secret-id
#   /secrets/services/<svc>/client-secret      its Keycloak client secret (if listed with client-secret)
#
# Services come from infra/secrets/services.list and examples/<app>/services.list. A service
# container mounts ONLY /secrets/services/<svc>; Vault, Consul and keycloak-init mount it all.
set -e

uuid() { cat /proc/sys/kernel/random/uuid; }
random() { head -c 32 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=\n'; }

ensure() { # ensure <file> <generator>
  if [ ! -s "$1" ]; then
    mkdir -p "$(dirname "$1")"
    $2 > "$1"
    chmod 0444 "$1"
    echo "generated $1"
  fi
}

ensure /secrets/infra/consul-root-token uuid

for list in /lists/services.list /examples/*/services.list; do
  [ -f "$list" ] || continue
  grep -v '^[[:space:]]*\(#\|$\)' "$list" | while read -r svc flags; do
    dir=/secrets/services/$svc
    ensure "$dir/consul-token" uuid
    ensure "$dir/vault-role-id" uuid
    ensure "$dir/vault-secret-id" uuid
    case " $flags " in *" client-secret "*) ensure "$dir/client-secret" random ;; esac
  done
done
echo "secrets ready"
