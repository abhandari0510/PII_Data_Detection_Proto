# Sample Credit Application

For a client-facing implementation procedure, see
[`docs/PII-DETECTION-PIPELINE-SOP.md`](docs/PII-DETECTION-PIPELINE-SOP.md).

This repository contains a Java 21 compatible app with:

- `application/credit-ui`: frontend service serving a simple form
- `application/credit-input`: backend service receiving form data, storing it locally, and calling the policy generator
- `application/policy-generator`: backend service returning a deterministic application number for a given input
- `kafka-detector-forwarder-go`: Go Kafka consumer that sends body-event records to detector Envoy

No database is used. Application data and detector counter logs are written to
local files. Traffic body-event logs are published directly to Kafka.

## Ports

- `credit-ui`: http://localhost:8085
- `credit-input`: http://localhost:8081
- `policy-generator`: http://localhost:8082
- traffic Envoy from `credit-ui` to `credit-input`: http://localhost:8089
- traffic Envoy from `credit-input` to `policy-generator`: http://localhost:8090
- detector Envoy: http://localhost:8091/detect
- Kafka REST Proxy for Envoy publishing: http://localhost:18082

## Prerequisites

- Java 21 for this app. Kafka 4.2 itself requires Java 17 or newer.
- Maven, available as `mvn`.
- Go 1.23 or newer for `kafka-detector-forwarder-go`.
- Apache Kafka 4.2 from the Apache quickstart, for the broker and Kafka CLI tools.
- Confluent Platform ZIP/TAR install, for Kafka REST Proxy.
- The checked-in Envoy binary, or set `ENVOY_BIN` before running `start-envoys.sh`.
- Grafana Alloy only if you want to turn detector logs into metrics.

Kafka Connect is not required for the detector path in this repository. The
local `kafka-detector-forwarder-go` module is the Kafka consumer that sends topic
records to detector Envoy. The Aiven OpenSearch connector is also not part of
this path; it is for writing Kafka records to OpenSearch.

## Service URL environment variables

The service-to-service URLs are configurable through environment variables:

- `CREDIT_INPUT_APPLY_URL` (published by `credit-ui` to the browser, default: `/api/credit/apply`)
- `CREDIT_INPUT_UPSTREAM_APPLY_URL` (used by `credit-ui`, default: `http://[::1]:8089/api/credit/apply`)
- `POLICY_GENERATOR_GENERATE_URL` (used by `credit-input`, default: `http://[::1]:8090/api/policy/generate`)

`start-all.sh` sets these defaults automatically and exports them before booting the services.

## Run the services

Use the helper script from the repository root:

```bash
./start-all.sh
```

This will start all services in the background and write shell output to `logs/*.shell.log`.

To stop the services, run:

```bash
./stop-all.sh
```

To stop everything and remove generated Maven workspaces, application records,
and all existing logs, run:

```bash
./clear-workspace.sh
```

Then open the UI directly at:

```bash
http://localhost:8085
```

## Route Service Calls Through Envoy

To capture the `credit-ui` to `credit-input` payload and proxy both service hops:

1. Start services:

```bash
./start-all.sh
```

2. Start both Envoys:

```bash
./start-envoys.sh
```

3. Open the UI directly:

```bash
http://localhost:8085
```

With this setup:

- Browser -> `credit-ui`
- `credit-ui` -> traffic Envoy (captures and publishes the payload) -> `credit-input`
- `credit-input` -> traffic Envoy -> `policy-generator`

The traffic Envoy does not write request/response body events to a local file.
It publishes body-event records directly to Kafka through Kafka REST Proxy.

## Kafka Body Event Pipeline

The traffic Envoy publishes request/response body-event records directly to
Kafka through Confluent Kafka REST Proxy. The local `kafka-detector-forwarder-go`
then consumes those Kafka records and sends each record value to the detector
Envoy, which performs the in-memory PII detection and writes the derived
counter log consumed by Alloy.

```text
traffic Envoy -> Kafka REST Proxy -> Kafka topic -> kafka-detector-forwarder-go -> detector Envoy -> logs/envoy-pii-detection.log -> Alloy
```

### 1. Start Kafka

Run Kafka locally using the Apache Kafka 4.2 quickstart, or point the setup at
an existing cluster. The examples below assume:

- Kafka bootstrap server: `localhost:9092`
- Kafka topic: `envoy-body-events`
- Kafka REST Proxy: `http://[::1]:18082`

The traffic Envoy config has a `kafka-rest-proxy` cluster pointing at
`[::1]:18082`. If your REST Proxy listens elsewhere, update the
`kafka-rest-proxy` cluster in `envoy.yaml`.

The topic name is set in two places:

- `KAFKA_TOPIC` inside `envoy.yaml`
- `KAFKA_TOPIC` used by `start-detector-forwarder.sh`

The Kafka CLI tools are used only for topic administration and verification:

```bash
kafka-topics.sh
kafka-console-consumer.sh
```

If you are using the Apache Kafka archive directly, start from your Kafka
directory:

```bash
cd /path/to/kafka_2.13-4.2.0

KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"
bin/kafka-storage.sh format --standalone -t "$KAFKA_CLUSTER_ID" -c config/server.properties
bin/kafka-server-start.sh config/server.properties
```

### 2. Configure The Topic

Set the bootstrap server and topic name:

```bash
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export KAFKA_TOPIC=envoy-body-events
```

Create the topic if it does not already exist:

```bash
kafka-topics.sh \
  --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
  --create \
  --if-not-exists \
  --topic "$KAFKA_TOPIC" \
  --partitions 1 \
  --replication-factor 1
```

Verify the topic exists:

```bash
kafka-topics.sh \
  --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
  --describe \
  --topic "$KAFKA_TOPIC"
```

### 3. Start Kafka REST Proxy

Start Kafka REST Proxy so Envoy can publish records to the topic. Configure it
to listen on `18082` to avoid this app's service ports:

```bash
kafka-rest-start /path/to/kafka-rest.properties
```

If you installed Confluent Platform from ZIP/TAR, create a small properties file
such as `/tmp/kafka-rest-envoy.properties`:

```properties
bootstrap.servers=PLAINTEXT://localhost:9092
listeners=http://[::1]:18082
```

Then start REST Proxy from your Confluent install:

```bash
/path/to/confluent/bin/kafka-rest-start /tmp/kafka-rest-envoy.properties
```

The traffic Envoy publishes to:

```text
POST http://[::1]:18082/topics/envoy-body-events
```

Each HTTP exchange produces two Kafka records:

- `direction=request`, containing the request body
- `direction=response`, containing the response body

Verify REST Proxy can see Kafka:

```bash
curl -i -sS http://[::1]:18082/topics
```

You should see `envoy-body-events` in the response after the topic has been
created.

### 4. Build This Project

From the repository root:

```bash
mvn -DskipTests package
```

This builds the three Java application modules. The Go forwarder is built when
`./start-detector-forwarder.sh` starts it.

### 5. Start The Application And Envoys

Start the application services:

```bash
./start-all.sh
```

Start both Envoys:

```bash
./start-envoys.sh
```

Verify the detector Envoy endpoint is reachable:

```bash
curl -i -sS \
  -X POST \
  -H "Content-Type: application/json" \
  --data '{"direction":"request","body":"Ravi Kumar 9876543210 ABCDE1234F"}' \
  http://[::1]:8091/detect
```

That request should return `204`, and `logs/envoy-pii-detection.log` should get
a derived counter log line.

### 6. Start The Detector Forwarder

Kafka itself is pull-based; it does not push records to HTTP endpoints without a
consumer. In this setup, the `kafka-detector-forwarder-go` module is that
consumer. It uses `segmentio/kafka-go`, consumes `envoy-body-events`, and POSTs
each record value to `http://[::1]:8091/detect` with
`Content-Type: application/json`.

Start the forwarder after Kafka and the detector Envoy are running:

```bash
./start-detector-forwarder.sh
```

The forwarder uses these defaults:

```text
KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092
KAFKA_TOPIC=envoy-body-events
KAFKA_DETECTOR_GROUP_ID=envoy-body-events-detector-forwarder
DETECTOR_ENVOY_URL=http://[::1]:8091/detect
```

Override them if your local ports differ:

```bash
KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092 \
KAFKA_TOPIC=envoy-body-events \
DETECTOR_ENVOY_URL='http://[::1]:8091/detect' \
./start-detector-forwarder.sh
```

The Aiven OpenSearch connector is not used in this detector path. It is a sink
for writing Kafka records to OpenSearch, not for POSTing records to arbitrary
HTTP endpoints such as detector Envoy.

Watch the forwarder log:

```bash
tail -f logs/kafka-detector-forwarder.shell.log
```

Stop the forwarder on its own with:

```bash
./stop-detector-forwarder.sh
```

### 7. Generate Traffic And Verify Logs

Open the UI directly and submit an application:

```text
http://localhost:8085
```

Or POST a test application directly:

```bash
curl -i -sS \
  -X POST \
  -H "Content-Type: application/json" \
  --data '{"name":"Ravi Kumar","phoneNumber":"9876543210","aadharNumber":"234567890123","panNumber":"ABCDE1234F","creditCardNumber":"4111111111111111","creditCardExpiry":"12/2028","cvc":"123"}' \
  http://localhost:8089/api/credit/apply
```

Verify traffic Envoy published to Kafka:

```bash
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic envoy-body-events \
  --from-beginning
```

The detector Envoy writes derived counter logs to:

```text
logs/envoy-pii-detection.log
```

Watch them with:

```bash
tail -f logs/envoy-pii-detection.log
```

### 8. Start Alloy

Alloy reads only the detector Envoy counter log:

```text
/home/akashbhandari/sample-java-app/logs/envoy-pii-detection.log
```

Start Alloy after the detector log exists:

```bash
alloy run --server.http.listen-addr=0.0.0.0:12345 alloy-pii-metrics.alloy
```

The metric endpoint remains:

```text
http://localhost:12345/metrics
```

### End-To-End Order

Use this order for a clean local run:

```text
Kafka -> Kafka REST Proxy -> mvn package -> ./start-all.sh -> ./start-envoys.sh -> ./start-detector-forwarder.sh -> Alloy
```

## Troubleshooting

If Envoy is not publishing to Kafka REST Proxy, check REST Proxy first:

```bash
curl -i -sS http://[::1]:18082/topics
curl -i -sS http://[::1]:9901/stats?filter=kafka-rest-proxy.upstream_rq
tail -n 80 logs/traffic-envoy.shell.log
```

If Kafka has records but detector logs are not being generated, check the
forwarder and detector:

```bash
tail -n 80 logs/kafka-detector-forwarder.shell.log
curl -i -sS http://[::1]:8091/detect
tail -n 80 logs/detector-envoy.shell.log
```

If `logs/envoy-pii-detection.log` cannot be written, fix file ownership or
remove the old log before restarting detector Envoy:

```bash
rm -f logs/envoy-pii-detection.log
./start-envoys.sh
```

If you see errors about Kafka Connect, `18083`, or
`io.aiven.kafka.connect.opensearch.OpenSearchSinkConnector`, you are on the
wrong path for detector delivery. This repository does not require Kafka
Connect for the detector pipeline.

## Local files

- `logs/credit-ui.log`
- `logs/credit-input.log`
- `logs/policy-generator.log`
- `logs/kafka-detector-forwarder.shell.log`
- `logs/envoy-pii-detection.log`
- `data/credit-input-records.jsonl`
- `data/policy-generator-applications.json`

## Notes

- The policy generator returns the same application number for the same input payload.
- Each service logs transaction details locally.
- Traffic Envoy timestamps request/response body events and publishes them directly to Kafka through Kafka REST Proxy.
- Detector Envoy preserves each event's `start_time`, records `detector_start_time`, converts PII-like strings into numeric counters, and writes only timestamped derived counts to `logs/envoy-pii-detection.log`.
- Alloy scrapes only the detector Envoy counter log. Raw request and response payload bodies are not read by Alloy or the OpenTelemetry Collector.
- PII metrics count sensitive-looking strings by route/service flow. Aadhaar detection includes valid 12-digit matches plus suspicious 11-14 digit near matches; credit card detection uses Luhn validation.
- No database is used.

## PII Detection Metrics

The proxy-layer PII flow is:

```text
traffic Envoy body event -> Kafka -> kafka-detector-forwarder-go -> detector Envoy Lua scan -> derived count fields -> Alloy metrics
```

The traffic Envoy is responsible only for normal HTTP proxying and body-event
logging. The detector Envoy is responsible for the existing in-memory PII
detection logic and counter log generation.

The detector access log contains fields such as:

```text
request_pii_aadhaar_total_count
request_pii_aadhaar_suspicious_count
request_pii_pan_count
request_pii_credit_card_count
request_pii_total_count
response_pii_total_count
```

Alloy remote-writes counters such as:

```text
pii_aadhar_number_transferred_total
pii_aadhar_suspicious_transferred_total
pii_pan_number_transferred_total
pii_credit_card_number_transferred_total
pii_response_payload_matches_total
```

On Alloy's local `http://localhost:12345/metrics` diagnostics endpoint, these
stage-generated metrics include the `loki_process_custom_` prefix. The Alloy
pipeline removes that prefix before remote-writing metrics to Grafana Cloud.

To send these metrics to Grafana Cloud, set the Prometheus remote-write
credentials before starting Alloy:

```bash
export GRAFANA_CLOUD_PROMETHEUS_REMOTE_WRITE_URL="https://prometheus-prod-<region>.grafana.net/api/prom/push"
export GRAFANA_CLOUD_PROMETHEUS_USERNAME="<grafana-cloud-prometheus-user-id>"
export GRAFANA_CLOUD_PROMETHEUS_PASSWORD="<grafana-cloud-api-token>"
```

Then start Alloy with:

```bash
alloy run --server.http.listen-addr=0.0.0.0:12345 alloy-pii-metrics.alloy
```

Or pass the variables inline:

```bash
GRAFANA_CLOUD_PROMETHEUS_REMOTE_WRITE_URL="https://prometheus-prod-<region>.grafana.net/api/prom/push" \
GRAFANA_CLOUD_PROMETHEUS_USERNAME="<grafana-cloud-prometheus-user-id>" \
GRAFANA_CLOUD_PROMETHEUS_PASSWORD="<grafana-cloud-api-token>" \
alloy run --server.http.listen-addr=0.0.0.0:12345 alloy-pii-metrics.alloy
```
