#!/bin/sh
# One-shot, idempotent Keycloak setup that realm-export.json can't do for an existing database
# (--import-realm skips realms that already exist). Runs on every `docker compose up`.
#
# 1. The `gateway` client (whose roles are SmartConnect's permissions) and the confidential
#    `user-service` client whose service account user-service uses for the Admin API - scoped
#    to the smartconnect realm, and via fine-grained admin permissions to the gateway client
#    among clients. Plus audit events (login + admin, 30 days).
# 2. Every client declared as a file - the plugin point for new frontends and service accounts:
#      infra/keycloak/clients/<clientId>.json     platform clients
#      examples/<example>/keycloak/<clientId>.json example clients
#    Each file is a Keycloak client representation and is the source of truth (re-applied on
#    every run). An optional <clientId>.permissions next to it lists SmartConnect permissions,
#    one per line, granted directly to that client's service account.
# The master admin credentials are used here, in this short-lived job, and nowhere else.
set -e
# Only POSIX sh + sed/grep/cut/tr/head: the Keycloak image ships nothing else (no awk, jq).

KCADM=/opt/keycloak/bin/kcadm.sh
REALM=smartconnect
CLIENT_ID=user-service

$KCADM config credentials --server "$KEYCLOAK_URL" --realm master \
  --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

client_id_of() {
  $KCADM get clients -r $REALM -q clientId="$1" --fields id --format csv --noquotes
}

# --- The permission client: SmartConnect's permissions are its client roles. user-service can
# manage this one client but not create clients, so it's guaranteed here.
if [ -z "$(client_id_of gateway)" ]; then
  $KCADM create clients -r $REALM -s clientId=gateway -s name="api-gateway resource server" \
    -s bearerOnly=true -s publicClient=false -s standardFlowEnabled=false >/dev/null
  echo "created client gateway"
fi

# --- user-service's own client (service account for the Admin API)
ID=$(client_id_of $CLIENT_ID)
if [ -z "$ID" ]; then
  $KCADM create clients -r $REALM \
    -s clientId=$CLIENT_ID -s name="user-service admin API access" -s enabled=true \
    -s publicClient=false -s serviceAccountsEnabled=true \
    -s standardFlowEnabled=false -s directAccessGrantsEnabled=false >/dev/null
  ID=$(client_id_of $CLIENT_ID)
  echo "created client $CLIENT_ID"
fi
# Keep the secret in step with the one Vault hands to user-service.
$KCADM update clients/"$ID" -r $REALM -s secret="$USER_SERVICE_CLIENT_SECRET"

# Realm-wide roles user-service needs: users + their role mappings, realm roles (roles CRUD -
# Keycloak has no finer grain for creating realm roles), reading clients, and reading audit
# events. Deliberately NOT manage-clients: that would let it edit every app's settings.
$KCADM add-roles -r $REALM --uusername service-account-$CLIENT_ID --cclientid realm-management \
  --rolename query-users --rolename view-users --rolename manage-users \
  --rolename view-realm --rolename manage-realm \
  --rolename query-clients --rolename view-clients --rolename view-events
$KCADM remove-roles -r $REALM --uusername service-account-$CLIENT_ID --cclientid realm-management \
  --rolename manage-clients 2>/dev/null || true

# --- Fine-grained admin permissions (Keycloak 26.2+): user-service may manage ONLY the gateway
# client - its roles are SmartConnect's permissions - and nothing else.
$KCADM update realms/$REALM -s adminPermissionsEnabled=true
APC=$(client_id_of admin-permissions)
SA=$($KCADM get clients/"$ID"/service-account-user -r $REALM --fields id --format csv --noquotes)
AUTHZ="clients/$APC/authz/resource-server"
POLICY=$($KCADM get "$AUTHZ/policy" -r $REALM -q name=user-service-account --fields id,name --format csv --noquotes \
  | grep ',user-service-account$' | cut -d, -f1)
if [ -z "$POLICY" ]; then
  $KCADM create "$AUTHZ/policy/user" -r $REALM -s name=user-service-account \
    -s description="The user-service service account" -s "users=[\"$SA\"]" >/dev/null
  POLICY=$($KCADM get "$AUTHZ/policy" -r $REALM -q name=user-service-account --fields id,name --format csv --noquotes \
    | grep ',user-service-account$' | cut -d, -f1)
fi
GATEWAY_ID=$(client_id_of gateway)
PERMISSION=$($KCADM get "$AUTHZ/permission" -r $REALM -q name=user-service-manages-gateway --fields id,name --format csv --noquotes \
  | grep ',user-service-manages-gateway$' | cut -d, -f1)
if [ -z "$PERMISSION" ]; then
  $KCADM create "$AUTHZ/permission/scope" -r $REALM -s name=user-service-manages-gateway \
    -s description="user-service manages SmartConnect permissions (client roles of gateway)" \
    -s resourceType=Clients -s "resources=[\"$GATEWAY_ID\"]" \
    -s 'scopes=["view","manage","map-roles-composite"]' -s "policies=[\"$POLICY\"]" >/dev/null
  echo "granted user-service manage on client gateway only"
fi

# --- Audit: store login and admin events for 30 days (with full change details) and log them
# (jboss-logging at INFO, see compose) so they also land in Kibana.
$KCADM update events/config -r $REALM -s eventsEnabled=true -s eventsExpiration=2592000 \
  -s adminEventsEnabled=true -s adminEventsDetailsEnabled=true -s 'eventsListeners=["jboss-logging"]'
$KCADM update realms/$REALM -s attributes.adminEventsExpiration=2592000

GATEWAY=$(client_id_of gateway)

for file in /clients/*.json /examples/*/keycloak/*.json; do
  [ -f "$file" ] || continue
  cid=$(sed -n 's/.*"clientId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$file" | head -1)
  id=$($KCADM get clients -r $REALM -q clientId="$cid" --fields id --format csv --noquotes)
  if [ -z "$id" ]; then
    $KCADM create clients -r $REALM -f "$file" >/dev/null
    echo "registered client $cid ($file)"
  else
    $KCADM update clients/"$id" -r $REALM -f "$file"
  fi

  perms="${file%.json}.permissions"
  [ -f "$perms" ] || continue
  grep -v '^[[:space:]]*\(#\|$\)' "$perms" | while read -r perm; do
    # Create the permission if user-service hasn't yet (fresh Keycloak); its bootstrap or the
    # owning service's catalog sync fills in the description and ownership afterwards.
    $KCADM get clients/"$GATEWAY"/roles/"$perm" -r $REALM >/dev/null 2>&1 \
      || $KCADM create clients/"$GATEWAY"/roles -r $REALM -s name="$perm" >/dev/null
    $KCADM add-roles -r $REALM --uusername service-account-"$cid" --cclientid gateway --rolename "$perm"
    echo "granted $perm to service account $cid"
  done
done

echo "keycloak init complete"
