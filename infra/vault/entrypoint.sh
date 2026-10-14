#!/bin/sh
# Starts Vault in server mode and brings it to a usable state on every start:
#   1. first start only: initialize (1 key share - local only) and keep the unseal key and root
#      token in the vault-data volume (/vault/data/init). Production would use a cloud KMS
#      auto-unseal and never store the root token;
#   2. unseal;
#   3. configure (idempotent): KV v2 at secret/, AppRole auth, and per service a read-only
#      policy for secret/smartconnect/<svc> + an AppRole whose role_id/secret_id are the ones
#      generated in the secrets volume;
#   4. load generated secrets (seed.sh).
# The healthcheck passes once all of this is done.
set -e

rm -f /tmp/configured
export VAULT_ADDR=http://127.0.0.1:8200
INIT=/vault/data/init
mkdir -p /vault/data/storage "$INIT"

vault server -config=/vault/config/vault.hcl &
VAULT_PID=$!
trap 'kill -TERM "$VAULT_PID"' TERM INT

# vault status: 0 = unsealed, 2 = sealed, 1 = not reachable yet
until vault status >/dev/null 2>&1 || [ $? -eq 2 ]; do
  sleep 1
done

if [ ! -s "$INIT/root-token" ]; then
  vault operator init -key-shares=1 -key-threshold=1 > "$INIT/init.txt"
  sed -n 's/^Unseal Key 1: //p' "$INIT/init.txt" > "$INIT/unseal-key"
  sed -n 's/^Initial Root Token: //p' "$INIT/init.txt" > "$INIT/root-token"
  rm "$INIT/init.txt"
  chmod 600 "$INIT/unseal-key" "$INIT/root-token"
  echo "vault initialized"
fi

vault operator unseal "$(cat "$INIT/unseal-key")" >/dev/null
export VAULT_TOKEN="$(cat "$INIT/root-token")"

vault secrets list | grep -q '^secret/' || vault secrets enable -path=secret -version=2 kv
vault auth list | grep -q '^approle/' || vault auth enable approle
# Keep brute-force lockout, but short: a service retrying with a just-fixed credential recovers
# within a minute instead of Vault's default 15.
vault auth tune -user-lockout-threshold=5 -user-lockout-duration=1m approle/ >/dev/null

for dir in /secrets/services/*/; do
  svc=$(basename "$dir")
  cat > /tmp/policy.hcl <<HCL
path "secret/data/smartconnect/$svc" { capabilities = ["read"] }
path "secret/data/smartconnect/$svc/*" { capabilities = ["read"] }
path "secret/data/application" { capabilities = ["read"] }
HCL
  vault policy write "$svc" /tmp/policy.hcl >/dev/null
  vault write "auth/approle/role/$svc" token_policies="$svc" token_ttl=1h token_max_ttl=24h \
    secret_id_ttl=0 secret_id_num_uses=0 >/dev/null
  vault write "auth/approle/role/$svc/role-id" role_id="$(cat "$dir/vault-role-id")" >/dev/null
  # The lookup succeeds (with empty output) for unknown ids, so check for actual data.
  if ! vault write "auth/approle/role/$svc/secret-id/lookup" secret_id="$(cat "$dir/vault-secret-id")" 2>/dev/null \
      | grep -q secret_id_accessor; then
    vault write "auth/approle/role/$svc/custom-secret-id" secret_id="$(cat "$dir/vault-secret-id")" >/dev/null
  fi
  echo "approle for $svc"
done

sh /seed.sh
touch /tmp/configured

wait "$VAULT_PID"
