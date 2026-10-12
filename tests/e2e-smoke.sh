#!/usr/bin/env bash
set -euo pipefail

compose=(docker compose -p "smart-contracts-e2e-${BASHPID:-$$}-${RANDOM}" -f deployment/docker-compose.yml)
export APP_HOST_PORT="${APP_HOST_PORT:-28443}"
export MONGODB_HOST_PORT="${MONGODB_HOST_PORT:-28017}"
export KAFKA_HOST_PORT="${KAFKA_HOST_PORT:-29092}"
export KAFKA_UI_HOST_PORT="${KAFKA_UI_HOST_PORT:-28084}"
export ANVIL_HOST_PORT="${ANVIL_HOST_PORT:-28545}"
export ANVIL_RPC_URL="http://127.0.0.1:${ANVIL_HOST_PORT}"

cleanup() {
  "${compose[@]}" down --volumes --remove-orphans
}
trap cleanup EXIT

services="$("${compose[@]}" config --services)"
if grep -Fxq "zookeeper" <<<"$services"; then
  echo "The local Compose stack must not include ZooKeeper" >&2
  exit 1
fi

"${compose[@]}" up --build -d

for attempt in $(seq 1 90); do
  if curl --fail --silent "http://127.0.0.1:${APP_HOST_PORT}/actuator/health/readiness" >/dev/null; then
    break
  fi
  if [[ "$attempt" -eq 90 ]]; then
    "${compose[@]}" ps
    "${compose[@]}" logs --no-color app
    exit 1
  fi
  sleep 5
done

health_headers="$(curl --silent --show-error --dump-header - --output /dev/null \
  "http://127.0.0.1:${APP_HOST_PORT}/actuator/health")"
grep -Fiq "Content-Security-Policy: default-src 'none'; base-uri 'none'; frame-ancestors 'none'" \
  <<<"$health_headers"

quorum_status="$("${compose[@]}" exec -T kafka kafka-metadata-quorum --bootstrap-server kafka:29092 describe --status)"
grep -Eq '^LeaderId:[[:space:]]*1$' <<<"$quorum_status"

denied_response="$(curl --silent --show-error --write-out '\n%{http_code}' \
  "http://127.0.0.1:${APP_HOST_PORT}/api/chain/status")"
[[ "${denied_response##*$'\n'}" == "401" ]]
[[ "${denied_response%$'\n'*}" == *'"error":"unauthorized"'* ]]

secure_test_vars=(
  APP_SECURITY_E2E_URL
  APP_SECURITY_E2E_BEARER_TOKEN
  APP_SECURITY_E2E_CLIENT_CERT
  APP_SECURITY_E2E_CLIENT_KEY
  APP_SECURITY_E2E_UNTRUSTED_CLIENT_CERT
  APP_SECURITY_E2E_UNTRUSTED_CLIENT_KEY
  APP_SECURITY_E2E_CA
)
configured_secure_test_vars=0
for variable in "${secure_test_vars[@]}"; do
  if [[ -n "${!variable:-}" ]]; then
    configured_secure_test_vars=$((configured_secure_test_vars + 1))
  fi
done
if [[ "$configured_secure_test_vars" -ne 0 && "$configured_secure_test_vars" -ne "${#secure_test_vars[@]}" ]]; then
  echo "Set all APP_SECURITY_E2E_* TLS/JWT variables to run the protected TLS check" >&2
  exit 1
fi
if [[ "$configured_secure_test_vars" -eq "${#secure_test_vars[@]}" ]]; then
  for file in "$APP_SECURITY_E2E_CLIENT_CERT" "$APP_SECURITY_E2E_CLIENT_KEY" \
      "$APP_SECURITY_E2E_UNTRUSTED_CLIENT_CERT" "$APP_SECURITY_E2E_UNTRUSTED_CLIENT_KEY" \
      "$APP_SECURITY_E2E_CA"; do
    if [[ ! -r "$file" ]]; then
      echo "Security E2E input file is missing or unreadable" >&2
      exit 1
    fi
  done

  secure_url="${APP_SECURITY_E2E_URL%/}/api/chain/status"
  allowed_response="$(curl --silent --show-error --cacert "$APP_SECURITY_E2E_CA" \
    --cert "$APP_SECURITY_E2E_CLIENT_CERT" --key "$APP_SECURITY_E2E_CLIENT_KEY" \
    --header "Authorization: Bearer $APP_SECURITY_E2E_BEARER_TOKEN" \
    --write-out '\n%{http_code}' "$secure_url")"
  [[ "${allowed_response##*$'\n'}" == "200" ]]
  [[ "${allowed_response%$'\n'*}" == *'"chainId":'* ]]

  missing_certificate_response="$(curl --silent --show-error --cacert "$APP_SECURITY_E2E_CA" \
    --header "Authorization: Bearer $APP_SECURITY_E2E_BEARER_TOKEN" \
    --write-out '\n%{http_code}' "$secure_url")"
  [[ "${missing_certificate_response##*$'\n'}" == "403" ]]

  if curl --silent --show-error --cacert "$APP_SECURITY_E2E_CA" \
      --cert "$APP_SECURITY_E2E_UNTRUSTED_CLIENT_CERT" --key "$APP_SECURITY_E2E_UNTRUSTED_CLIENT_KEY" \
      --header "Authorization: Bearer $APP_SECURITY_E2E_BEARER_TOKEN" "$secure_url" >/dev/null 2>&1; then
    echo "The protected TLS route accepted an untrusted client certificate" >&2
    exit 1
  fi

  invalid_token_response="$(curl --silent --show-error --cacert "$APP_SECURITY_E2E_CA" \
    --cert "$APP_SECURITY_E2E_CLIENT_CERT" --key "$APP_SECURITY_E2E_CLIENT_KEY" \
    --header 'Authorization: Bearer invalid-e2e-token' \
    --write-out '\n%{http_code}' "$secure_url")"
  [[ "${invalid_token_response##*$'\n'}" == "401" ]]
  [[ "${invalid_token_response%$'\n'*}" == *'"error":"unauthorized"'* ]]
fi

balance_response="$(curl --fail --silent --header 'Content-Type: application/json' \
  --data '{"jsonrpc":"2.0","method":"eth_getBalance","params":["0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266","latest"],"id":1}' \
  "$ANVIL_RPC_URL")"
node -e 'if (BigInt(JSON.parse(process.argv[1]).result) <= 0n) process.exit(1)' "$balance_response"

for attempt in $(seq 1 30); do
  if curl --location --fail --silent "http://127.0.0.1:${KAFKA_UI_HOST_PORT}/" >/dev/null; then
    break
  fi
  if [[ "$attempt" -eq 30 ]]; then
    "${compose[@]}" logs --no-color kafka-ui
    exit 1
  fi
  sleep 2
done

npm run contracts:deploy:local

topics="$("${compose[@]}" exec -T kafka kafka-topics --bootstrap-server kafka:29092 --list)"
for topic in tokenization.events transfer.events compliance.events settlement.events blockchain.events \
  tokenization.events.dlt transfer.events.dlt compliance.events.dlt settlement.events.dlt blockchain.events.dlt \
  chain.tx.confirmed chain.tx.confirmed.dlt; do
  grep -Fxq "$topic" <<<"$topics"
done
"${compose[@]}" exec -T mongodb mongosh --quiet "mongodb://mongodb:27017/general-contracts?replicaSet=rs0" --eval \
  'if (!db.getCollectionNames().includes("processed_events")) quit(1)'

printf '%s\n' 'e2e-event:{"eventId":"e2e-event","eventType":"ChainTxConfirmed","businessId":"e2e-business","occurredAt":"2026-10-07T00:00:00Z","chainId":31337,"txHash":null,"blockNumber":1,"schemaVersion":1,"payload":{}}' \
  | "${compose[@]}" exec -T kafka kafka-console-producer --bootstrap-server kafka:29092 --topic chain.tx.confirmed --property parse.key=true --property key.separator=:

for attempt in $(seq 1 30); do
  if "${compose[@]}" logs --no-color app | grep -Fq "Received chain confirmation event id=e2e-event"; then
    exit 0
  fi
  sleep 2
done

"${compose[@]}" logs --no-color app
echo "Kafka consumer did not receive the smoke-test event" >&2
exit 1
