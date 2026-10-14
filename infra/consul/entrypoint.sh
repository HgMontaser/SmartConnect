#!/bin/sh
# Consul dev mode keeps everything in memory, like Vault's. On every start, load the KV config
# from infra/consul/config (the source of truth, versioned in git) before reporting healthy:
# <name>.yml becomes config/<name>/data, which Spring Cloud Consul serves to the service named
# <name> ("application" = every service). Services re-register themselves via their heartbeat.
set -e

rm -f /tmp/seeded
consul agent -dev -client=0.0.0.0 -log-level=warn &
CONSUL_PID=$!
trap 'kill -TERM "$CONSUL_PID"' TERM INT

until consul members >/dev/null 2>&1; do
  sleep 1
done

for file in /consul-config/*.yml; do
  name=$(basename "$file" .yml)
  consul kv put "config/$name/data" @"$file" >/dev/null
  echo "seeded config/$name/data"
done
touch /tmp/seeded

wait "$CONSUL_PID"
