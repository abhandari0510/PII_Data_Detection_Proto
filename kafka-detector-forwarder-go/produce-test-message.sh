#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="kafka-forwarder-redpanda"
TOPIC="${KAFKA_TOPIC:-envoy-body-events}"
MESSAGE="${1:-{\"test\":\"hello from kafka\"}}"

docker exec -i "$CONTAINER_NAME" rpk topic create "$TOPIC" >/dev/null 2>&1 || true
printf '%s\n' "$MESSAGE" | docker exec -i "$CONTAINER_NAME" rpk topic produce "$TOPIC"
