#!/usr/bin/env bash
set -e

echo "Stopping credit-ui, credit-input, policy-generator, Kafka detector forwarder, and Envoy processes..."
"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/stop-detector-forwarder.sh" || true
"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/kafka-detector-forwarder-go/stop-redpanda-kafka.sh" || true
pkill -f "mvn -pl application/policy-generator spring-boot:run" || true
pkill -f "mvn -pl application/credit-input spring-boot:run" || true
pkill -f "mvn -pl application/credit-ui spring-boot:run" || true
pkill -f "com.example.policygenerator.PolicyGeneratorApplication" || true
pkill -f "com.example.creditinput.CreditInputApplication" || true
pkill -f "com.example.creditui.CreditUiApplication" || true
pkill -f "envoy.* -c envoy.yaml" || true
pkill -f "envoy.* -c envoy-pii-detector.yaml" || true

echo "Stop command issued. Verify with 'ss -ltnp | grep -E \":8081|:8082|:8085|:8089|:8090|:8091\"' if needed."
