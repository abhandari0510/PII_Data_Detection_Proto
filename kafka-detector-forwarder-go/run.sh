#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"
mkdir -p logs .gocache .gomodcache
PID_FILE="logs/kafka-detector-forwarder.pid"

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Kafka detector forwarder is already running with PID $(cat "$PID_FILE")"
  echo "Stop it with: ./stop.sh"
  exit 0
fi

: "${KAFKA_BOOTSTRAP_SERVERS:=127.0.0.1:9092}"
: "${KAFKA_TOPIC:=envoy-body-events}"
: "${KAFKA_DETECTOR_GROUP_ID:=envoy-body-events-detector-forwarder}"
: "${DETECTOR_ENVOY_URL:=http://127.0.0.1:8091/detect}"

export KAFKA_BOOTSTRAP_SERVERS
export KAFKA_TOPIC
export KAFKA_DETECTOR_GROUP_ID
export DETECTOR_ENVOY_URL
export GOCACHE="$ROOT_DIR/.gocache"
export GOMODCACHE="$ROOT_DIR/.gomodcache"

echo "Starting Kafka detector forwarder..."
echo "Kafka bootstrap: $KAFKA_BOOTSTRAP_SERVERS"
echo "Kafka topic: $KAFKA_TOPIC"
echo "Detector Envoy: $DETECTOR_ENVOY_URL"

nohup go run . > logs/kafka-detector-forwarder.shell.log 2>&1 &
echo "$!" > "$PID_FILE"
echo "Kafka detector forwarder PID: $!"
echo "Log: logs/kafka-detector-forwarder.shell.log"
