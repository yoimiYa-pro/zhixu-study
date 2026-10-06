#!/usr/bin/env bash
set -euo pipefail
umask 077
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"
if [[ "${2:-}" != "--confirm-replace" || ! -f "${1:-}/postgres.dump" ]]; then
  printf 'Usage: bash scripts/restore.sh BACKUP_DIRECTORY --confirm-replace\nReplaces public schema in one transaction after automatically backing up current data.\n' >&2
  exit 2
fi
backup_dir="$(cd "$1" && pwd)"
(cd "$backup_dir" && sha256sum -c SHA256SUMS)
docker_command=(docker)
if ! docker info >/dev/null 2>&1; then docker_command=(sudo -n docker); fi
mkdir -p .local
sql_file="$(mktemp "$project_root/.local/restore-XXXXXX.sql")"
services_stopped=false
cleanup() {
  status=$?
  trap - EXIT
  rm -f "$sql_file"
  if [[ "$services_stopped" == true ]]; then
    "${docker_command[@]}" compose --profile app up -d backend-java frontend || status=1
  fi
  exit "$status"
}
trap cleanup EXIT
# Render the entire archive successfully before stopping services or changing data.
# The schema replacement and import then share a single PostgreSQL transaction.
printf 'DROP SCHEMA public CASCADE;\n' > "$sql_file"
"${docker_command[@]}" compose exec -T postgres pg_restore --no-owner --no-privileges --file=- < "$backup_dir/postgres.dump" >> "$sql_file"
bash scripts/backup.sh
services_stopped=true
"${docker_command[@]}" compose --profile app stop frontend backend-java
"${docker_command[@]}" compose exec -T postgres sh -c 'exec psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB" --single-transaction -v ON_ERROR_STOP=1 -f -' < "$sql_file"
"${docker_command[@]}" compose --profile app up -d backend-java frontend
services_stopped=false
printf 'PostgreSQL restored. Rebuild vector indexes from the authenticated maintenance API.\n'
