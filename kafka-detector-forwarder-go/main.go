package main

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/segmentio/kafka-go"
)

const (
	defaultBootstrapServers = "127.0.0.1:9092"
	defaultTopic            = "envoy-body-events"
	defaultGroupID          = "envoy-body-events-detector-forwarder"
	defaultDetectorEnvoyURL = "http://127.0.0.1:8091/detect"
)

func main() {
	if err := run(); err != nil {
		slog.Error("Kafka detector forwarder stopped", "error", err)
		os.Exit(1)
	}
}

func run() error {
	bootstrapServers := env("KAFKA_BOOTSTRAP_SERVERS", defaultBootstrapServers)
	topic := env("KAFKA_TOPIC", defaultTopic)
	groupID := env("KAFKA_DETECTOR_GROUP_ID", defaultGroupID)
	detectorEnvoyURL := env("DETECTOR_ENVOY_URL", defaultDetectorEnvoyURL)

	detectorURL, err := url.Parse(detectorEnvoyURL)
	if err != nil {
		return fmt.Errorf("parse DETECTOR_ENVOY_URL: %w", err)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:        splitCSV(bootstrapServers),
		Topic:          topic,
		GroupID:        groupID,
		StartOffset:    kafka.FirstOffset,
		CommitInterval: 0,
	})
	defer func() {
		if err := reader.Close(); err != nil {
			slog.Warn("Failed to close Kafka reader", "error", err)
		}
	}()

	httpClient := &http.Client{Timeout: 5 * time.Second}

	slog.Info(
		"Forwarding Kafka topic to detector Envoy",
		"topic", topic,
		"bootstrap_servers", bootstrapServers,
		"detector_envoy_url", detectorURL.String(),
	)

	for {
		message, err := reader.FetchMessage(ctx)
		if err != nil {
			if errors.Is(err, context.Canceled) {
				return nil
			}
			return fmt.Errorf("fetch Kafka message: %w", err)
		}

		if err := forwardRecord(ctx, httpClient, detectorURL.String(), message); err != nil {
			return err
		}

		if err := reader.CommitMessages(ctx, message); err != nil {
			return fmt.Errorf("commit Kafka message topic=%s partition=%d offset=%d: %w",
				message.Topic, message.Partition, message.Offset, err)
		}
	}
}

func forwardRecord(ctx context.Context, httpClient *http.Client, detectorURL string, message kafka.Message) error {
	body := message.Value
	if body == nil {
		body = []byte{}
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, detectorURL, bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("build detector Envoy request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to send Kafka record to detector Envoy: %w", err)
	}
	defer closeBody(resp.Body)

	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
		return fmt.Errorf("detector Envoy returned HTTP %d", resp.StatusCode)
	}

	slog.Info(
		"Forwarded Kafka record",
		"topic", message.Topic,
		"partition", message.Partition,
		"offset", message.Offset,
		"bytes", len(body),
	)
	return nil
}

func env(name, defaultValue string) string {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		return defaultValue
	}
	return value
}

func splitCSV(value string) []string {
	parts := strings.Split(value, ",")
	brokers := make([]string, 0, len(parts))
	for _, part := range parts {
		if broker := strings.TrimSpace(part); broker != "" {
			brokers = append(brokers, broker)
		}
	}
	if len(brokers) == 0 {
		return []string{defaultBootstrapServers}
	}
	return brokers
}

func closeBody(body io.Closer) {
	if err := body.Close(); err != nil {
		slog.Warn("Failed to close response body", "error", err)
	}
}
