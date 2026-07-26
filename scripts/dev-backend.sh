#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:5432/balanced}"
export DATABASE_USERNAME="${DATABASE_USERNAME:-balanced}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-balanced}"
export SEED_DEMO_DATA="${SEED_DEMO_DATA:-true}"
cd "$ROOT/backend"
mvn spring-boot:run
