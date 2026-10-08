#!/usr/bin/env bash
set -euo pipefail

compose=(docker compose -p "smart-contracts-e2e-${BASHPID:-$$}-${RANDOM}" -f deployment/docker-compose.yml)
app_user="${APP_SECURITY_USERNAME:-local-dev}"
app_password="${APP_SECURITY_PASSWORD:-local-dev-only}"
export APP_HOST_PORT="${APP_HOST_PORT:-28080}"
export MONGODB_HOST_PORT="${MONGODB_HOST_PORT:-28017}"
export KAFKA_HOST_PORT="${KAFKA_HOST_PORT:-29092}"
export KAFKA_UI_HOST_PORT="${KAFKA_UI_HOST_PORT:-28084}"
export ANVIL_HOST_PORT="${ANVIL_HOST_PORT:-28545}"
export ANVIL_RPC_URL="http://127.0.0.1:${ANVIL_HOST_PORT}"

cleanup() {
  "${compose[@]}" down --volumes --remove-orphans
}
trap cleanup EXIT

"${compose[@]}" up --build -d

for attempt in $(seq 1 90); do
  if curl --fail --silent --user "$app_user:$app_password" "http://127.0.0.1:${APP_HOST_PORT}/actuator/health/readiness" >/dev/null; then
    break
  fi
  if [[ "$attempt" -eq 90 ]]; then
    "${compose[@]}" ps
    "${compose[@]}" logs --no-color app
    exit 1
  fi
  sleep 5
done

chain_status="$(curl --fail --silent --user "$app_user:$app_password" "http://127.0.0.1:${APP_HOST_PORT}/api/chain/status")"
[[ "$chain_status" == *'"chainId":31337'* ]]
[[ "$chain_status" == *'"latestBlock":'* ]]

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
