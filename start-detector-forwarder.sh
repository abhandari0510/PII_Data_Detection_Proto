#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"
mkdir -p logs

PID_FILE="logs/kafka-detector-forwarder.pid"
BINARY="logs/kafka-detector-forwarder-go"

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Kafka detector forwarder is already running with PID $(cat "$PID_FILE")"
  echo "Stop it with: ./stop-detector-forwarder.sh"
  exit 0
fi

if [[ -f "$PID_FILE" ]]; then
  echo "Removing stale Kafka detector forwarder PID file."
  rm -f "$PID_FILE"
fi

if ! command -v go >/dev/null 2>&1; then
  echo "Go is required to build the Kafka detector forwarder but was not found on PATH." >&2
  exit 1
fi

: "${KAFKA_BOOTSTRAP_SERVERS:=127.0.0.1:9092}"
: "${KAFKA_TOPIC:=envoy-body-events}"
: "${KAFKA_DETECTOR_GROUP_ID:=envoy-body-events-detector-forwarder}"
: "${DETECTOR_ENVOY_URL:=http://[::1]:8091/detect}"
: "${DETECTOR_ENVOY_WAIT_SECONDS:=30}"

export KAFKA_BOOTSTRAP_SERVERS
export KAFKA_TOPIC
export KAFKA_DETECTOR_GROUP_ID
export DETECTOR_ENVOY_URL

echo "Waiting for detector Envoy at $DETECTOR_ENVOY_URL..."
elapsed=0
until curl -g -fsS --max-time 2 "$DETECTOR_ENVOY_URL" >/dev/null; do
  if [[ "$elapsed" -ge "$DETECTOR_ENVOY_WAIT_SECONDS" ]]; then
    echo "Detector Envoy is not reachable at $DETECTOR_ENVOY_URL." >&2
    echo "Start it with: ./start-envoys.sh" >&2
    exit 1
  fi
  sleep 1
  elapsed=$((elapsed + 1))
done

echo "Building Kafka detector forwarder..."
(cd kafka-detector-forwarder-go && go build -buildvcs=false -o "$ROOT_DIR/$BINARY" .)

echo "Starting Kafka detector forwarder..."
echo "Kafka bootstrap: $KAFKA_BOOTSTRAP_SERVERS"
echo "Kafka topic: $KAFKA_TOPIC"
echo "Detector Envoy: $DETECTOR_ENVOY_URL"

nohup "$BINARY" > logs/kafka-detector-forwarder.shell.log 2>&1 &
echo "$!" > "$PID_FILE"
echo "Kafka detector forwarder PID: $!"
echo "Log: logs/kafka-detector-forwarder.shell.log"
