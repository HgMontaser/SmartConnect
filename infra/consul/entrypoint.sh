#!/bin/sh
# Consul dev mode keeps everything in memory, like Vault's. On every start:
#   1. start with ACLs on and deny-by-default (root token: $CONSUL_HTTP_TOKEN);
#   2. create one least-privilege token per service from the token files - the plug-in point:
#        infra/consul/acl/<service>.token          platform services
#        examples/<app>/consul/<service>.token     example apps
#      A service's token may register only that service, discover the others, and read only
#      config/application and config/<service>;
#   3. load the KV config from infra/consul/config: <name>.yml -> config/<name>/data.
# Services re-register themselves via their heartbeat; the healthcheck passes once all is done.
set -e

rm -f /tmp/seeded
consul agent -dev -client=0.0.0.0 -log-level=warn -hcl "
acl {
  enabled = true
  default_policy = \"deny\"
  down_policy = \"extend-cache\"
  tokens {
    initial_management = \"$CONSUL_HTTP_TOKEN\"
    agent = \"$CONSUL_HTTP_TOKEN\"
  }
}" &
CONSUL_PID=$!
trap 'kill -TERM "$CONSUL_PID"' TERM INT

until consul members >/dev/null 2>&1; do
  sleep 1
done

for file in /consul-acl/*.token /examples/*/consul/*.token; do
  [ -f "$file" ] || continue
  name=$(basename "$file" .token)
  cat > /tmp/policy.hcl <<HCL
service "$name" { policy = "write" }
service_prefix "" { policy = "read" }
node_prefix "" { policy = "read" }
key_prefix "config/application/" { policy = "read" }
key_prefix "config/application," { policy = "read" }
key_prefix "config/$name/" { policy = "read" }
key_prefix "config/$name," { policy = "read" }
HCL
  consul acl policy create -name "service-$name" -rules @/tmp/policy.hcl >/dev/null
  consul acl token create -secret "$(tr -d ' \n' < "$file")" -policy-name "service-$name" \
    -description "$name (from $file)" >/dev/null
  echo "token for $name"
done

for file in /consul-config/*.yml; do
  name=$(basename "$file" .yml)
  consul kv put "config/$name/data" @"$file" >/dev/null
  echo "seeded config/$name/data"
done
touch /tmp/seeded

wait "$CONSUL_PID"
