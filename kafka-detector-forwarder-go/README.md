# Kafka Detector Forwarder Go

Small Go version of the Java `KafkaDetectorForwarder`.

It consumes JSON payloads from Kafka and forwards each message body to the detector Envoy endpoint with `Content-Type: application/json`. Offsets are committed only after the HTTP request succeeds.

## Environment

| Name | Default |
| --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `127.0.0.1:9092` |
| `KAFKA_TOPIC` | `envoy-body-events` |
| `KAFKA_DETECTOR_GROUP_ID` | `envoy-body-events-detector-forwarder` |
| `DETECTOR_ENVOY_URL` | `http://127.0.0.1:8091/detect` |

`KAFKA_BOOTSTRAP_SERVERS` can contain multiple comma-separated brokers.

## Run

```bash
GOCACHE="$PWD/.gocache" GOMODCACHE="$PWD/.gomodcache" go mod tidy
GOCACHE="$PWD/.gocache" GOMODCACHE="$PWD/.gomodcache" go run .
```

Or start it in the background with logs:

```bash
chmod +x run.sh
./run.sh
```

Logs are written to `logs/kafka-detector-forwarder.shell.log` when using `run.sh`.

Stop the background forwarder:

```bash
./stop.sh
```

## Local End-to-End Test

Terminal 1:

```bash
./start-detector-test-server.sh
```

Terminal 2:

```bash
./start-redpanda-kafka.sh
./run.sh
tail -f logs/kafka-detector-forwarder.shell.log
```

Terminal 3:

```bash
./produce-test-message.sh '{"test":"hello from kafka"}'
```

The detector test server should print the JSON body, and the forwarder log should show `Forwarded Kafka record`.

Stop local test Kafka:

```bash
./stop-redpanda-kafka.sh
```
