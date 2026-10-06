#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"
python3 - <<'PY'
import socket
for port in (8081, 5190):
    with socket.socket() as probe:
        probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            probe.bind(('127.0.0.1', port))
        except OSError:
            raise SystemExit(f'E2E port {port} is occupied; stop that test service before continuing.')
PY
if [[ -d .local/browser-runtime/usr/lib/x86_64-linux-gnu ]]; then
  export LD_LIBRARY_PATH="$project_root/.local/browser-runtime/usr/lib/x86_64-linux-gnu:${LD_LIBRARY_PATH:-}"
  export FONTCONFIG_FILE="$project_root/.local/browser-fonts.conf"
fi
docker_command=(docker)
if ! docker info >/dev/null 2>&1; then docker_command=(sudo -n docker); fi
test_schema="e2e_$(date +%s)_${RANDOM}"
test_container="civil-study-${test_schema}"
vite_pid=""
ai_pid=""
ai_port_file="$project_root/.local/${test_schema}-ai-port"
cleanup() {
  cd "$project_root"
  [[ -z "$vite_pid" ]] || kill "$vite_pid" 2>/dev/null || true
  [[ -z "$ai_pid" ]] || kill "$ai_pid" 2>/dev/null || true
  rm -f "$ai_port_file"
  "${docker_command[@]}" rm -f "$test_container" >/dev/null 2>&1 || true
  if [[ "$test_schema" =~ ^e2e_[0-9]+_[0-9]+$ ]]; then
    "${docker_command[@]}" compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "$1"' sh "DROP SCHEMA IF EXISTS $test_schema CASCADE" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
python3 frontend/e2e/ai-fixture.py "$ai_port_file" > "$project_root/.local/e2e-ai.log" 2>&1 &
ai_pid=$!
for attempt in $(seq 1 30); do
  [[ ! -s "$ai_port_file" ]] || break
  kill -0 "$ai_pid"
  sleep 0.1
done
ai_port="$(cat "$ai_port_file")"
[[ "$ai_port" =~ ^[0-9]{1,5}$ ]]
test_ai_url="http://127.0.0.1:$ai_port"
"${docker_command[@]}" compose --progress quiet run -d --name "$test_container" \
  -e SERVER_PORT=8081 -e SPRING_FLYWAY_SCHEMAS="$test_schema" -e SPRING_FLYWAY_DEFAULT_SCHEMA="$test_schema" \
  -e SPRING_DATASOURCE_HIKARI_SCHEMA="$test_schema" -e AI_WORKER_ENABLED=false -e SCHEDULER_ENABLED=false -e AI_SERVICE_URL="$test_ai_url" \
  java-test java -jar /work/backend-java/target/civil-study-1.0.0.jar >/dev/null
cd frontend
npm run build > "$project_root/.local/e2e-build.log" 2>&1
VITE_JAVA_PROXY_TARGET=http://127.0.0.1:8081 node node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 5190 --strictPort > "$project_root/.local/e2e-vite.log" 2>&1 &
vite_pid=$!
cd "$project_root"
for attempt in $(seq 1 45); do
  if curl -fsS http://127.0.0.1:8081/api/health >/dev/null 2>&1 && curl -fsS http://127.0.0.1:5190/ >/dev/null 2>&1; then break; fi
  sleep 1
done
kill -0 "$vite_pid"
test "$("${docker_command[@]}" inspect --format '{{.State.Running}}' "$test_container")" = true
(cd frontend && E2E_SCHEMA="$test_schema" E2E_AI_URL="$test_ai_url" npx playwright test "$@")
