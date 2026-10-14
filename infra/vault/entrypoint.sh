#!/bin/sh
# Vault dev mode keeps everything in memory, so every container restart starts from an empty
# Vault. Seeding from inside the Vault container itself (instead of a one-shot sidecar that
# only ever runs once) means a restarted Vault is re-seeded before it reports healthy, and
# the services that depend on it never boot against missing secrets.
set -e

rm -f /tmp/seeded
vault server -dev &
VAULT_PID=$!
trap 'kill -TERM "$VAULT_PID"' TERM INT

export VAULT_TOKEN="$VAULT_DEV_ROOT_TOKEN_ID"
sh /seed.sh
touch /tmp/seeded

wait "$VAULT_PID"
