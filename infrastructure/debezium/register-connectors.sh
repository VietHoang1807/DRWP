#!/usr/bin/env sh
# Đăng ký connector outbox cho từng cụm PostgreSQL (ADR 020).
# Dùng: CONNECT_URL=http://localhost:8083 ./register-connectors.sh
set -eu
CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
DIR="$(cd "$(dirname "$0")" && pwd)"
for f in "$DIR"/outbox-cluster-*.json; do
  name=$(sed -n 's/.*"name": "\(.*\)".*/\1/p' "$f" | head -1)
  echo "Registering $name"
  curl -fsS -X PUT -H 'Content-Type: application/json' \
    --data "$(python3 -c 'import json,sys;print(json.dumps(json.load(open(sys.argv[1]))["config"]))' "$f")" \
    "$CONNECT_URL/connectors/$name/config" >/dev/null
done
curl -fsS "$CONNECT_URL/connectors?expand=status"; echo
