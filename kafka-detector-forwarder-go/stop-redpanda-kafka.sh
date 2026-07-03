#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="kafka-forwarder-redpanda"

if docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  docker stop "$CONTAINER_NAME" >/dev/null
  echo "Stopped Kafka-compatible Redpanda container."
else
  echo "Kafka-compatible Redpanda container is not running."
fi
