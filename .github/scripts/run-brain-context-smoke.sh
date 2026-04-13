#!/usr/bin/env bash
set -euo pipefail

port="${BRAIN_SMOKE_PORT:-18080}"
context_path="${BRAIN_SMOKE_CONTEXT_PATH:-/brain}"
base_url="http://127.0.0.1:${port}${context_path}"
runner_temp="${RUNNER_TEMP:-$(pwd)/target}"
log_file="${runner_temp}/brain-context-smoke.log"
jar_path="${BRAIN_SMOKE_JAR_PATH:-}"
remove_storage_root="false"

if [[ -z "${jar_path}" ]]; then
  jar_path="$(find target -maxdepth 1 -type f -name 'golemcore-brain-*.jar' ! -name '*.jar.original' | sort | head -n 1)"
fi

if [[ -z "${jar_path}" ]]; then
  echo "Could not find packaged golemcore-brain jar under target/." >&2
  exit 1
fi

mkdir -p "${runner_temp}"

if [[ -n "${BRAIN_STORAGE_ROOT:-}" ]]; then
  storage_root="${BRAIN_STORAGE_ROOT}"
  mkdir -p "${storage_root}"
else
  storage_root="$(mktemp -d "${runner_temp}/brain-context-smoke-data.XXXXXX")"
  remove_storage_root="true"
fi

java -jar "${jar_path}" \
  --server.port="${port}" \
  --server.servlet.context-path="${context_path}" \
  --brain.storage-root="${storage_root}" \
  >"${log_file}" 2>&1 &
app_pid="$!"

cleanup() {
  status="$?"
  kill "${app_pid}" >/dev/null 2>&1 || true
  wait "${app_pid}" >/dev/null 2>&1 || true
  if [[ "${remove_storage_root}" == "true" ]]; then
    rm -rf "${storage_root}"
  fi
  if [[ "${status}" -ne 0 ]]; then
    echo "--- brain context smoke log ---" >&2
    cat "${log_file}" >&2 || true
  fi
}
trap cleanup EXIT

ready="false"
for _ in $(seq 1 90); do
  if curl -fsS "${base_url}/api/auth/config" >/dev/null 2>&1; then
    ready="true"
    break
  fi
  sleep 1
done

if [[ "${ready}" != "true" ]]; then
  echo "Brain app did not become ready at ${base_url}." >&2
  exit 1
fi

(
  cd frontend
  PLAYWRIGHT_BASE_URL="${base_url}" npx playwright test playwright/ui-smoke.spec.ts
)
