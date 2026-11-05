#!/usr/bin/env bash
# Smoke test of the running local environment (docker compose up --build --wait): the main scenario and the key
# errors, with the demo clients and services. Every run uses a new externalId, so the script can be repeated.
#
#   scripts/smoke-test.sh
#   APP_URL=http://localhost:8080 OAUTH_URL=http://localhost:8090 scripts/smoke-test.sh
#
# Needs only bash and curl. Request bodies go through standard input, so Polish characters arrive intact on every
# platform (Git Bash on Windows would re-encode them in command-line arguments).
set -euo pipefail

APP_URL="${APP_URL:-http://localhost:8080}"
OAUTH_URL="${OAUTH_URL:-http://localhost:8090}"
API="$APP_URL/api/v1/troubleTicket"
EXTERNAL_ID="SMOKE-$(date +%s)-$RANDOM"
BODY="$(mktemp)"
HEADERS="$(mktemp)"
trap 'rm -f "$BODY" "$HEADERS"' EXIT
failures=0

token() {
    curl -fsS -X POST "$OAUTH_URL/netia/token" \
        -d grant_type=client_credentials -d "client_id=$1" -d client_secret=any \
        | sed -nE 's/.*"access_token" *: *"([^"]+)".*/\1/p'
}

# expect DESCRIPTION STATUS FRAGMENT CURL_ARGUMENTS...
# Passes when the response has the status and its body contains the fragment (an empty fragment matches any body).
expect() {
    local description="$1" expected_status="$2" fragment="$3"
    shift 3
    local status
    status="$(curl -sS -o "$BODY" -D "$HEADERS" -w '%{http_code}' "$@")"
    if [[ "$status" == "$expected_status" ]] && { [[ -z "$fragment" ]] || grep -qF -- "$fragment" "$BODY"; }; then
        printf 'PASS  %-58s %s\n' "$description" "$status"
    else
        printf 'FAIL  %-58s expected %s with %s, got %s: %s\n' \
            "$description" "$expected_status" "${fragment:-any body}" "$status" "$(cat "$BODY")"
        failures=$((failures + 1))
    fi
}

# create_request EXTERNAL_ID SERVICE_ID STATUS
create_request() {
    cat <<EOF
{
  "externalId": "$1",
  "serviceId": $2,
  "description": "Brak transmisji danych dla usługi klienta.",
  "status": "$3",
  "note": "Zgłoszenie utworzone przez konto API partnera."
}
EOF
}

json=(-H 'Content-Type: application/json' --data-binary @-)
partner_a="Authorization: Bearer $(token partner-a)"
partner_a_readonly="Authorization: Bearer $(token partner-a-readonly)"
partner_b="Authorization: Bearer $(token partner-b)"

echo "Smoke test of $APP_URL, externalId $EXTERNAL_ID"

expect "create a ticket" 201 '"status":"new"' \
    -X POST "$API" -H "$partner_a" "${json[@]}" <<< "$(create_request "$EXTERNAL_ID" 987654321 new)"
ticket_id="$(sed -nE 's/.*"id":"(TT-[^"]+)".*/\1/p' "$BODY")"
if grep -qi "^location: /api/v1/troubleTicket/$ticket_id" "$HEADERS"; then
    printf 'PASS  %-58s\n' "create returns the Location of the ticket"
else
    printf 'FAIL  %-58s\n' "create returns the Location of the ticket"
    failures=$((failures + 1))
fi

expect "create again returns the same ticket" 200 "\"id\":\"$ticket_id\"" \
    -X POST "$API" -H "$partner_a" "${json[@]}" <<< "$(create_request "$EXTERNAL_ID" 987654321 new)"
expect "list tickets of the tenant" 200 "\"externalId\":\"$EXTERNAL_ID\"" \
    "$API" -H "$partner_a"
expect "get the ticket" 200 "\"id\":\"$ticket_id\"" \
    "$API/$ticket_id" -H "$partner_a"
expect "add a note" 201 '"id":"NOTE-' \
    -X POST "$API/$ticket_id/note" -H "$partner_a" "${json[@]}" <<< '{ "text": "Klient prosi o potwierdzenie terminu." }'
expect "close the ticket" 200 '"status":"closed"' \
    -X PATCH "$API/$ticket_id" -H "$partner_a" "${json[@]}" <<< '{ "status": "closed" }'
expect "close it again" 200 '"status":"closed"' \
    -X PATCH "$API/$ticket_id" -H "$partner_a" "${json[@]}" <<< '{ "status": "closed" }'

expect "request without a token" 401 '"code":"UNAUTHORIZED"' \
    "$API"
# A new externalId for each create below: a known one would return the existing ticket before any other check.
expect "create with a read-only token" 403 '"code":"FORBIDDEN"' \
    -X POST "$API" -H "$partner_a_readonly" "${json[@]}" <<< "$(create_request "$EXTERNAL_ID-ro" 987654321 new)"
expect "create with a status other than new" 400 '"code":"VALIDATION_ERROR"' \
    -X POST "$API" -H "$partner_a" "${json[@]}" <<< "$(create_request "$EXTERNAL_ID-ack" 987654321 acknowledged)"
expect "create for an inactive service" 404 '"code":"SERVICE_NOT_FOUND"' \
    -X POST "$API" -H "$partner_a" "${json[@]}" <<< "$(create_request "$EXTERNAL_ID-inactive" 987654329 new)"
expect "create for a service of another tenant" 404 '"code":"SERVICE_NOT_FOUND"' \
    -X POST "$API" -H "$partner_a" "${json[@]}" <<< "$(create_request "$EXTERNAL_ID-foreign" 987654331 new)"
expect "get a ticket of another tenant" 404 '"code":"TROUBLE_TICKET_NOT_FOUND"' \
    "$API/$ticket_id" -H "$partner_b"

if ((failures > 0)); then
    echo "$failures check(s) failed"
    exit 1
fi
echo "All checks passed"
