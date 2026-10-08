#!/usr/bin/env bash
set -euo pipefail

mongosh "mongodb://mongodb:27017/admin" --quiet --eval '
try {
  rs.status();
} catch (error) {
  rs.initiate({
    _id: "rs0",
    members: [{ _id: 0, host: "mongodb:27017" }]
  });
}'

for attempt in $(seq 1 60); do
  if mongosh "mongodb://mongodb:27017/admin" --quiet --eval 'quit(rs.status().myState === 1 ? 0 : 1)' >/dev/null 2>&1; then
    exit 0
  fi
  sleep 1
done

echo "MongoDB replica set did not become primary within 60 seconds" >&2
exit 1
