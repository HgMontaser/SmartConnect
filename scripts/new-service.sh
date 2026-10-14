#!/usr/bin/env bash
# Scaffolds a backend service already plugged into SmartConnect: Vault + Consul config, Consul
# registration, a gateway route, its own permission catalog, JWT security and a compose entry.
# It contains no business logic - just a protected status endpoint to start from.
#
#   scripts/new-service.sh <name> [--example <app>] [--port <port>] [--routes <patterns>]
#
#   <name>             lowercase, e.g. billing-service. Its permission resource is the name
#                      without "-service" (billing -> billing:read, billing:write).
#   --example <app>    put it under examples/<app>/ (built with -Pexamples, started only with
#                      `--profile <app>`) instead of services/.
#   --port <port>      HTTP port (default: next free one from 8083).
#   --routes <list>    gateway paths, comma-separated (default: /api/<resource>/**).
#
# Then: docker compose up -d --build <name>   (add --profile <app> for examples)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

die() { echo "error: $*" >&2; exit 1; }

NAME="${1:-}"
[ -n "$NAME" ] || die "usage: scripts/new-service.sh <name> [--example <app>] [--port <port>] [--routes <patterns>]"
shift
[[ "$NAME" =~ ^[a-z][a-z0-9-]{1,40}$ ]] || die "name must be lowercase letters, digits and '-' (e.g. billing-service)"

EXAMPLE=""
PORT=""
ROUTES=""
while [ $# -gt 0 ]; do
  case "$1" in
    --example) EXAMPLE="${2:-}"; shift 2 ;;
    --port) PORT="${2:-}"; shift 2 ;;
    --routes) ROUTES="${2:-}"; shift 2 ;;
    *) die "unknown option $1" ;;
  esac
done
[ -z "$EXAMPLE" ] || [[ "$EXAMPLE" =~ ^[a-z][a-z0-9-]{1,40}$ ]] || die "--example must be lowercase letters, digits and '-'"

RESOURCE="${NAME%-service}"
[[ "$RESOURCE" =~ ^[a-z][a-z0-9-]{0,29}$ ]] || die "permission resource '$RESOURCE' is too long (max 30 chars)"
case "$RESOURCE" in users|roles|permissions|platform|sessions|events|audit) die "'$RESOURCE' is reserved by SmartConnect" ;; esac
ROUTES="${ROUTES:-/api/$RESOURCE/**}"

# Java package / class names: billing-service -> com.smartconnect.billingservice / BillingService
PACKAGE="com.smartconnect.$(echo "$NAME" | tr -d '-')"
CLASS="$(echo "$NAME" | awk -F- '{ for (i = 1; i <= NF; i++) printf "%s%s", toupper(substr($i, 1, 1)), substr($i, 2) }')"
PACKAGE_DIR="$(echo "$PACKAGE" | tr '.' '/')"

if [ -n "$EXAMPLE" ]; then
  MODULE="examples/$EXAMPLE/$NAME"
  PARENT_PATH="../../../pom.xml"
else
  MODULE="services/$NAME"
  PARENT_PATH="../../pom.xml"
fi
[ ! -e "$MODULE" ] || die "$MODULE already exists"
grep -rqs --include=application.yml "^    name: $NAME\$" services examples 2>/dev/null && die "a service named $NAME already exists"

if [ -z "$PORT" ]; then
  PORT=8083
  USED="$(grep -rhs --include=application.yml -A1 '^server:' services examples 2>/dev/null | sed -n 's/^  port: \([0-9]*\).*/\1/p' | sort -n)"
  while echo "$USED" | grep -qx "$PORT"; do PORT=$((PORT + 1)); done
fi
[[ "$PORT" =~ ^[0-9]+$ ]] || die "--port must be a number"

echo "Scaffolding $NAME in $MODULE (port $PORT, routes $ROUTES, permissions $RESOURCE:*)"

mkdir -p "$MODULE/src/main/java/$PACKAGE_DIR" "$MODULE/src/main/resources"

cat > "$MODULE/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.smartconnect</groupId>
    <artifactId>smartconnect-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>$PARENT_PATH</relativePath>
  </parent>

  <artifactId>$NAME</artifactId>
  <packaging>jar</packaging>
  <name>$NAME</name>

  <dependencies>
    <!-- JWT security, permissions, correlation ids, error format, Consul defaults -->
    <dependency>
      <groupId>com.smartconnect</groupId>
      <artifactId>smartconnect-commons</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Secrets (Vault) and shared config + discovery + gateway route (Consul) -->
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-vault-config</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-consul-discovery</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-consul-config</artifactId>
    </dependency>

    <!-- Publishes this service's permission catalog to SmartConnect -->
    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- Service token for ServiceClients.asService() - needs a registration, see application.yml -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
EOF

cat > "$MODULE/src/main/resources/application.yml" <<EOF
server:
  port: $PORT

spring:
  application:
    name: $NAME

  # Secrets from Vault (secret/smartconnect/$NAME - add to infra/vault/seed.sh only if this
  # service has any), shared config from Consul KV (infra/consul/config). Fail fast on either.
  config:
    import: "vault://,consul:"

  cloud:
    vault:
      uri: \${VAULT_URI:http://vault:8200}
      # Logs in with its own AppRole (generated, mounted) - set up by commons.
      fail-fast: true
      kv:
        enabled: true
        backend: secret
        application-name: smartconnect/$NAME

  kafka:
    bootstrap-servers: \${KAFKA_BOOTSTRAP_SERVERS:kafka:19092}

  # To call other services as itself (ServiceClients.asService()), register a confidential
  # Keycloak client (infra/keycloak/clients/$NAME.json + $NAME.permissions), add
  # "client-secret" to this service's line in the services.list, and uncomment:
  # security:
  #   oauth2:
  #     client:
  #       registration:
  #         smartconnect:
  #           client-id: $NAME
  #           client-secret: \${smartconnect.client-secret}
  #           authorization-grant-type: client_credentials

smartconnect:
  # Paths api-gateway routes to this service (discovered via Consul).
  gateway:
    routes: $ROUTES
    # timeout: 30s   # only if some calls legitimately take longer than the gateway default (10s)
  # This service's permissions, synced into SmartConnect; grant them to roles in the backoffice.
  # Every permission used in @PreAuthorize must be listed here or startup fails.
  permissions:
    service: $NAME
    catalog:
      - name: $RESOURCE:read
        description: View $RESOURCE
      - name: $RESOURCE:write
        description: Change $RESOURCE

management:
  endpoints:
    web:
      exposure:
        include: health,info

EOF

cat > "$MODULE/src/main/java/$PACKAGE_DIR/${CLASS}Application.java" <<EOF
package $PACKAGE;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableMethodSecurity
public class ${CLASS}Application {

    public static void main(String[] args) {
        SpringApplication.run(${CLASS}Application.class, args);
    }
}
EOF

cat > "$MODULE/src/main/java/$PACKAGE_DIR/StatusController.java" <<EOF
package $PACKAGE;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Starting point: proves routing, authentication and permissions work end to end. */
@RestController
@RequestMapping("/api/$RESOURCE")
public class StatusController {

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('$RESOURCE:read')")
    public Map<String, String> status(@AuthenticationPrincipal Jwt caller) {
        return Map.of("service", "$NAME", "status", "UP", "caller", caller.getClaimAsString("preferred_username"));
    }
}
EOF

# --- register the Maven module
if [ -n "$EXAMPLE" ]; then MARKER="new-service:examples"; INDENT="        "; else MARKER="new-service:platform"; INDENT="    "; fi
awk -v line="$INDENT<module>$MODULE</module>" -v marker="$MARKER" \
  'index($0, marker) { print line } { print }' pom.xml > pom.xml.tmp && mv pom.xml.tmp pom.xml

# --- Credentials: secrets-init generates a Consul token and a Vault AppRole for every listed service
if [ -n "$EXAMPLE" ]; then SERVICES_LIST="examples/$EXAMPLE/services.list"; else SERVICES_LIST="infra/secrets/services.list"; fi
[ -f "$SERVICES_LIST" ] || echo "# Services of the $EXAMPLE example that get generated credentials (see infra/secrets/init.sh)." > "$SERVICES_LIST"
echo "$NAME" >> "$SERVICES_LIST"

# --- compose entry (an example's compose file lives two levels down, so its context is ../..)
BUILD_CONTEXT="."
PROFILE_LINE=""
if [ -n "$EXAMPLE" ]; then
  BUILD_CONTEXT="../.."
  PROFILE_LINE=$'\n'"    profiles: [\"$EXAMPLE\"]"
fi
SERVICE_BLOCK="  $NAME:
    build:
      context: $BUILD_CONTEXT
      dockerfile: services/Dockerfile
      args:
        MODULE: $MODULE
    container_name: smartconnect-$NAME
    logging: {driver: json-file, options: {max-size: 10m, max-file: \"3\"}}
    restart: unless-stopped$PROFILE_LINE
    environment:
      VAULT_URI: http://vault:8200
      VAULT_ROLE_ID_FILE: /run/secrets/smartconnect/vault-role-id
      VAULT_SECRET_ID_FILE: /run/secrets/smartconnect/vault-secret-id
      CONSUL_HOST: consul
      CONSUL_TOKEN_FILE: /run/secrets/smartconnect/consul-token
      KAFKA_BOOTSTRAP_SERVERS: kafka:19092
    depends_on:
      vault:
        condition: service_healthy
      consul:
        condition: service_healthy
      kafka:
        condition: service_healthy
    volumes:
      # Only this service's own credentials.
      - type: volume
        source: secrets
        target: /run/secrets/smartconnect
        read_only: true
        volume:
          subpath: services/$NAME
    networks:
      - smartconnect
"
if [ -n "$EXAMPLE" ]; then
  COMPOSE="examples/$EXAMPLE/compose.yml"
  if [ ! -f "$COMPOSE" ]; then
    cat > "$COMPOSE" <<EOF
# Example app "$EXAMPLE" - included from the root docker-compose.yml, started only with:
#   docker compose --profile $EXAMPLE up -d --build
# Paths below are relative to this file. Keycloak clients for it go in ./keycloak/.
networks:
  smartconnect:
    driver: bridge

volumes:
  secrets:

services:
EOF
    awk -v line="  - path: $COMPOSE" 'index($0, "new-service:includes") { print line } { print }' \
      docker-compose.yml > docker-compose.yml.tmp && mv docker-compose.yml.tmp docker-compose.yml
  fi
  printf '%s' "$SERVICE_BLOCK" >> "$COMPOSE"
else
  COMPOSE="docker-compose.yml"
  BLOCK_FILE="$(mktemp)"
  printf '%s\n' "$SERVICE_BLOCK" > "$BLOCK_FILE"
  awk -v blockfile="$BLOCK_FILE" \
    'index($0, "new-service:services") { while ((getline l < blockfile) > 0) print l } { print }' \
    docker-compose.yml > docker-compose.yml.tmp && mv docker-compose.yml.tmp docker-compose.yml
  rm -f "$BLOCK_FILE"
fi

echo
echo "Created $MODULE and registered it in pom.xml, $COMPOSE and $SERVICES_LIST."
echo "Its Consul token and Vault AppRole are generated and applied on the next:"
echo "  docker compose up -d secrets-init && docker compose restart consul vault"
echo "Next:"
if [ -n "$EXAMPLE" ]; then
  echo "  docker compose --profile $EXAMPLE up -d --build $NAME"
else
  echo "  docker compose up -d --build $NAME"
fi
echo "  then grant $RESOURCE:read to a role in the backoffice and call GET http://localhost:8090/api/$RESOURCE/status"
