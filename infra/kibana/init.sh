#!/bin/sh
# Imports SmartConnect's Kibana objects (infra/kibana/smartconnect.ndjson) on every start:
# data views for logs and metrics, the "SmartConnect overview" dashboard and the
# "Trace a correlation ID" saved search. Overwrites them, so the file stays the source of truth -
# to keep a changed copy, "Save as" under a new name in Kibana.
#
# To change them: edit in Kibana, then re-export into the file:
#   curl -s -X POST localhost:5601/api/saved_objects/_export -H 'kbn-xsrf: true' \
#     -H 'Content-Type: application/json' -d '{"objects":[{"type":"dashboard","id":"smartconnect-overview"}],
#     "includeReferencesDeep":true,"excludeExportDetails":true}' > infra/kibana/smartconnect.ndjson
set -e

RESULT=$(curl -sS -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
  -H 'kbn-xsrf: true' --form file=@/kibana/smartconnect.ndjson)
echo "$RESULT"
echo "$RESULT" | grep -q '"success":true' || { echo "kibana import failed" >&2; exit 1; }
echo "kibana objects imported"
