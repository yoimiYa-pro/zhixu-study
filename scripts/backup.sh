#!/usr/bin/env bash
set -euo pipefail
umask 077
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"
docker_command=(docker)
if ! docker info >/dev/null 2>&1; then docker_command=(sudo -n docker); fi
backup_dir="${1:-$project_root/.local/backups/$(date -u +%Y%m%dT%H%M%SZ)-${RANDOM}}"
mkdir -p "$backup_dir"
test ! -e "$backup_dir/postgres.dump"
"${docker_command[@]}" compose exec -T postgres sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --schema=public --no-owner --no-privileges' > "$backup_dir/postgres.dump.tmp"
mv "$backup_dir/postgres.dump.tmp" "$backup_dir/postgres.dump"
cp .env "$backup_dir/env.private"
(cd "$backup_dir" && sha256sum postgres.dump > SHA256SUMS)
printf 'Backup created: %s\n' "$backup_dir"
