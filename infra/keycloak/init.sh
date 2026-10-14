#!/bin/sh
# One-shot, idempotent Keycloak setup that realm-export.json can't do for an existing database
# (--import-realm skips realms that already exist). Runs on every `docker compose up`.
#
# 1. The confidential `user-service` client whose service account user-service uses for the
#    Keycloak Admin API - scoped to the smartconnect realm only, instead of the master admin.
# 2. Every client declared as a file - the plugin point for new frontends and service accounts:
#      infra/keycloak/clients/<clientId>.json     platform clients
#      examples/<example>/keycloak/<clientId>.json example clients
#    Each file is a Keycloak client representation and is the source of truth (re-applied on
#    every run). An optional <clientId>.permissions next to it lists SmartConnect permissions,
#    one per line, granted directly to that client's service account.
# The master admin credentials are used here, in this short-lived job, and nowhere else.
set -e

KCADM=/opt/keycloak/bin/kcadm.sh
REALM=smartconnect
CLIENT_ID=user-service

$KCADM config credentials --server "$KEYCLOAK_URL" --realm master \
  --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

ID=$($KCADM get clients -r $REALM -q clientId=$CLIENT_ID --fields id --format csv --noquotes)
if [ -z "$ID" ]; then
  $KCADM create clients -r $REALM \
    -s clientId=$CLIENT_ID -s name="user-service admin API access" -s enabled=true \
    -s publicClient=false -s serviceAccountsEnabled=true \
    -s standardFlowEnabled=false -s directAccessGrantsEnabled=false >/dev/null
  ID=$($KCADM get clients -r $REALM -q clientId=$CLIENT_ID --fields id --format csv --noquotes)
  echo "created client $CLIENT_ID"
fi
# Keep the secret in step with the one Vault hands to user-service.
$KCADM update clients/"$ID" -r $REALM -s secret="$USER_SERVICE_CLIENT_SECRET"

# Exactly what user-service does: users + role mappings, realm roles (roles CRUD), and client
# roles of the permission client (permissions CRUD, bootstrap). Keycloak has no finer grain.
$KCADM add-roles -r $REALM --uusername service-account-$CLIENT_ID --cclientid realm-management \
  --rolename query-users --rolename view-users --rolename manage-users \
  --rolename view-realm --rolename manage-realm \
  --rolename query-clients --rolename view-clients --rolename manage-clients

GATEWAY=$($KCADM get clients -r $REALM -q clientId=gateway --fields id --format csv --noquotes)

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
