#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"
mkdir -p logs

if [[ -z "${ENVOY_BIN:-}" ]]; then
  mapfile -t envoy_candidates < <(
    find "$ROOT_DIR" -maxdepth 1 -type f -name "envoy-*-linux-x86_64" -executable | sort -V
  )

  if [[ "${#envoy_candidates[@]}" -gt 0 ]]; then
    last_candidate=$((${#envoy_candidates[@]} - 1))
    ENVOY_BIN="${envoy_candidates[$last_candidate]}"
  elif command -v envoy >/dev/null 2>&1; then
    ENVOY_BIN="$(command -v envoy)"
  else
    echo "Envoy binary not found." >&2
    echo "Download an envoy-*-linux-x86_64 binary into $ROOT_DIR, make it executable, or set ENVOY_BIN." >&2
    exit 1
  fi
fi

if [[ ! -x "$ENVOY_BIN" ]]; then
  echo "Envoy binary is not executable: $ENVOY_BIN" >&2
  echo "Run: chmod +x \"$ENVOY_BIN\"" >&2
  exit 1
fi

echo "Using Envoy binary: $ENVOY_BIN"

start_envoy() {
  local name=$1
  local config=$2
  shift 2
  local ports=("$@")

  for port in "${ports[@]}"; do
    if ss -ltn "sport = :${port}" | tail -n +2 | grep -q .; then
      echo "Port ${port} is already in use. Start aborted for ${name}."
      ss -ltnp "sport = :${port}" || true
      exit 1
    fi
  done

  echo "Starting ${name} with ${config}..."
  nohup "$ENVOY_BIN" --disable-hot-restart --local-address-ip-version v6 -c "$config" --base-id "${ports[0]}" > "logs/${name}.shell.log" 2>&1 &
  echo "${name} PID: $!"
}

start_envoy traffic-envoy envoy.yaml 8089 8090
start_envoy detector-envoy envoy-pii-detector.yaml 8091

echo "Traffic Envoy UI -> Input: http://localhost:8089"
echo "Traffic Envoy Input -> Policy: http://localhost:8090"
echo "Detector Envoy: http://localhost:8091/detect"
