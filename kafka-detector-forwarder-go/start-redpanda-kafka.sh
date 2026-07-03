#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="kafka-forwarder-redpanda"

if docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  echo "Kafka-compatible Redpanda container is already running."
  exit 0
fi

if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  docker start "$CONTAINER_NAME"
  echo "Started existing Kafka-compatible Redpanda container."
  exit 0
fi

docker run -d --name "$CONTAINER_NAME" \
  -p 9092:9092 \
  docker.redpanda.com/redpandadata/redpanda:v24.3.6 \
  redpanda start \
  --overprovisioned \
  --smp 1 \
  --memory 512M \
  --reserve-memory 0M \
  --node-id 0 \
  --check=false \
  --kafka-addr PLAINTEXT://0.0.0.0:9092 \
  --advertise-kafka-addr PLAINTEXT://127.0.0.1:9092

echo "Started Kafka-compatible Redpanda on 127.0.0.1:9092"
