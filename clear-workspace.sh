#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "Stopping application, forwarder, and Envoy processes..."
"$ROOT_DIR/stop-all.sh"

paths_to_clear=(
  "$ROOT_DIR/target"
  "$ROOT_DIR/application/credit-ui/target"
  "$ROOT_DIR/application/credit-input/target"
  "$ROOT_DIR/application/policy-generator/target"
  "$ROOT_DIR/application/credit-ui/data"
  "$ROOT_DIR/application/credit-input/data"
  "$ROOT_DIR/application/policy-generator/data"
  "$ROOT_DIR/application/credit-ui/logs"
  "$ROOT_DIR/application/credit-input/logs"
  "$ROOT_DIR/application/policy-generator/logs"
  "$ROOT_DIR/kafka-detector-forwarder-go/logs"
  "$ROOT_DIR/kafka-detector-forwarder-go/.gocache"
  "$ROOT_DIR/kafka-detector-forwarder-go/.gomodcache"
  "$ROOT_DIR/data"
  "$ROOT_DIR/logs"
)

cleanup_failed=0
for path in "${paths_to_clear[@]}"; do
  if [[ ! -e "$path" ]]; then
    continue
  fi

  echo "Removing ${path#"$ROOT_DIR"/}..."
  if ! rm -rf -- "$path"; then
    cleanup_failed=1
  fi
done

if [[ "$cleanup_failed" -ne 0 ]]; then
  echo >&2
  echo "Cleanup could not remove files owned by another user." >&2
  echo "Run 'sudo ./clear-workspace.sh' once to clear those stale files." >&2
  exit 1
fi

workspace_owner="${SUDO_USER:-$(id -un)}"
workspace_group="$(id -gn "$workspace_owner")"

mkdir -p logs
if [[ "$(id -u)" -eq 0 ]]; then
  chown "$workspace_owner:$workspace_group" logs
fi

echo "Workspace cleared. Source files and configuration were preserved."
echo "Removed: Maven targets, application data, forwarder caches, and logs."
