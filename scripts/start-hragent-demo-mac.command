#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
N8N_DIR="$REPO_ROOT/n8nwork"
SAAS_DIR="$REPO_ROOT/hragentv1"

say_status() {
  printf '\n==> %s\n' "$1"
}

open_docker_desktop() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "Docker Desktop is not installed. Install it, then run this file again." >&2
    exit 1
  fi

  if ! docker info >/dev/null 2>&1; then
    say_status "Starting Docker Desktop"
    open -a Docker
    for _ in {1..60}; do
      if docker info >/dev/null 2>&1; then
        return
      fi
      sleep 2
    done
    echo "Docker Desktop did not become ready within 2 minutes. Open Docker Desktop and wait for Engine to start, then run this file again." >&2
    exit 1
  fi
}

wait_for_url() {
  local url="$1"
  local name="$2"
  for _ in {1..45}; do
    if curl --silent --fail --max-time 3 "$url" >/dev/null; then
      printf '[OK] %s: %s\n' "$name" "$url"
      return
    fi
    sleep 2
  done
  echo "[WARN] $name did not respond in time: $url" >&2
}

say_status "Preparing local configuration"
bash "$REPO_ROOT/scripts/initialize-hragent-config.sh"

open_docker_desktop

say_status "Starting n8n, knowledge base, OCR, and local services"
docker compose --env-file "$N8N_DIR/.env" -f "$N8N_DIR/docker-compose.yml" up -d postgres qdrant ollama pdf-parser ocr n8n

say_status "Starting employee relations SaaS and AI chat"
docker compose --env-file "$SAAS_DIR/.env" -f "$SAAS_DIR/docker-compose.yml" up -d

say_status "Checking services"
wait_for_url "http://localhost:5173" "SaaS management portal"
wait_for_url "http://localhost:5174" "AI chat portal"
wait_for_url "http://localhost:5678/healthz" "n8n"

say_status "Opening demo pages"
open "http://localhost:5173"
open "http://localhost:5174"

printf '\nDemo is ready.\n'
printf 'Management portal: http://localhost:5173\n'
printf 'AI chat portal:    http://localhost:5174\n'
printf 'n8n editor:        http://localhost:5678\n'
printf 'Demo login: zhangsan / 123456\n\n'
read -r -p "Press Enter to close this window..."
