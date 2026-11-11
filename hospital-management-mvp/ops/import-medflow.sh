#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"
if docker compose version >/dev/null 2>&1; then
  exec docker compose --profile seed run --rm populate
elif command -v docker-compose >/dev/null 2>&1; then
  exec docker-compose --profile seed run --rm populate
else
  echo 'Docker Compose nu este disponibil. Pornește Docker Desktop și rulează din proiect: docker compose --profile seed run --rm populate' >&2
  exit 1
fi
