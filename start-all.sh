#!/usr/bin/env bash
set -e

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"
mkdir -p logs

: "${CREDIT_INPUT_APPLY_URL:=/api/credit/apply}"
: "${CREDIT_INPUT_UPSTREAM_APPLY_URL:=http://[::1]:8089/api/credit/apply}"
: "${POLICY_GENERATOR_GENERATE_URL:=http://[::1]:8090/api/policy/generate}"

export CREDIT_INPUT_APPLY_URL
export CREDIT_INPUT_UPSTREAM_APPLY_URL
export POLICY_GENERATOR_GENERATE_URL

ensure_port_free() {
  local port=$1
  if ss -ltn "sport = :${port}" | tail -n +2 | grep -q .; then
    echo "Port ${port} is already in use. Start aborted."
    echo "Port ownership:"
    ss -ltnp "sport = :${port}" || true
    echo "Run ./stop-all.sh before starting again."
    exit 1
  fi
}

ensure_port_free 8085
ensure_port_free 8081
ensure_port_free 8082

wait_for_port() {
  local name=$1
  local port=$2
  local timeout_seconds=${3:-60}
  local elapsed=0

  while [ "$elapsed" -lt "$timeout_seconds" ]; do
    if ss -ltn "sport = :${port}" | tail -n +2 | grep -q .; then
      echo "$name is listening on port ${port}."
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done

  echo "Timed out waiting for $name to listen on port ${port}."
  echo "Recent ${name} logs:"
  tail -n 80 "logs/${name}.shell.log" || true
  exit 1
}

start_service() {
  local module=$1
  local name=$2
  local port=$3
  echo "Starting $name..."
  nohup mvn -pl "$module" spring-boot:run > "logs/${name}.shell.log" 2>&1 &
  echo "$name PID: $!"
  wait_for_port "$name" "$port"
}

start_service application/policy-generator policy-generator 8082
start_service application/credit-input credit-input 8081
start_service application/credit-ui credit-ui 8085

echo "\nAll services started."
echo "credit-ui: http://localhost:8085"
echo "credit-input: http://localhost:8081"
echo "policy-generator: http://localhost:8082"
echo "Browser -> credit-ui URL: $CREDIT_INPUT_APPLY_URL"
echo "credit-ui -> Envoy -> credit-input URL: $CREDIT_INPUT_UPSTREAM_APPLY_URL"
echo "credit-input -> policy-generator URL: $POLICY_GENERATOR_GENERATE_URL"
echo "Logs are written to logs/*.shell.log and each service also writes Spring logs to logs/"
