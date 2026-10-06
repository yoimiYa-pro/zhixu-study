#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"
test_mode="${1:-native}"
if [[ "$test_mode" != "native" && "$test_mode" != "--docker" ]]; then
  printf 'Usage: bash scripts/test.sh [--docker]\n' >&2
  exit 2
fi
python3 scripts/check_structure.py
(cd frontend && npm ci --no-fund && npm test && npm run build)
if [[ "$test_mode" == "--docker" ]]; then
  docker_command=(docker)
  if ! docker info >/dev/null 2>&1; then docker_command=(sudo docker); fi
  mkdir -p .local/m2 .local/pip-cache
  "${docker_command[@]}" compose --progress quiet up -d postgres redis qdrant
  "${docker_command[@]}" compose --progress quiet run --rm java-test
  "${docker_command[@]}" run --rm -u "$(id -u):$(id -g)" --network host \
    -e PIP_CACHE_DIR=/work/.local/pip-cache -v "$project_root:/work" -w /work/ai-service \
    python:3.12-slim sh -c '
      python -m venv /work/.local/venv &&
      /work/.local/venv/bin/pip install -r requirements-dev.txt -c constraints.txt &&
      /work/.local/venv/bin/pip check &&
      python -m unittest discover -s /work/deploy/egress -q &&
      /work/.local/venv/bin/python -m pytest -q &&
      /work/.local/venv/bin/ruff check app tests'
else
  (cd deploy/egress && python3 -m unittest -q)
  (cd backend-java && mvn --batch-mode --no-transfer-progress verify)
  (cd ai-service && python -m pip check && python -m pytest -q && ruff check app tests)
fi
docker compose config --quiet
