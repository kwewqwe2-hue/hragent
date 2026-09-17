#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_HOST="${1:-}"
PUBLIC_DOMAIN="${2:-}"
SERVER_USER="${3:-root}"
REMOTE_DIR="${4:-/opt/hragent}"
SSH_KEY="${SSH_KEY:-}"

if [[ -z "$SERVER_HOST" || -z "$PUBLIC_DOMAIN" ]]; then
  echo "Usage: $0 SERVER_IP PUBLIC_DOMAIN [SSH_USER] [REMOTE_DIR]" >&2
  echo "Example: $0 124.222.81.207 demo.example.com root /opt/hragent" >&2
  echo "Before deploying, point app, chat, and automation subdomains at SERVER_IP." >&2
  exit 2
fi

if [[ ! "$PUBLIC_DOMAIN" =~ ^[A-Za-z0-9.-]+$ ]]; then
  echo "PUBLIC_DOMAIN must be a DNS name without a scheme or path." >&2
  exit 2
fi

if ! command -v ssh >/dev/null 2>&1 || ! command -v scp >/dev/null 2>&1; then
  echo "ssh and scp are required on this Mac." >&2
  exit 1
fi

TARGET="$SERVER_USER@$SERVER_HOST"
CONTROL_SOCKET="$(mktemp -u -t hragent-ssh.XXXXXX)"
SSH_OPTS=(
  -o ConnectTimeout=10
  -o ServerAliveInterval=30
  -o ServerAliveCountMax=3
  -o ControlMaster=auto
  -o ControlPersist=10m
  -o "ControlPath=$CONTROL_SOCKET"
)
if [[ -n "$SSH_KEY" ]]; then
  SSH_OPTS+=( -i "$SSH_KEY" )
fi
ARCHIVE="$(mktemp -t hragent-cloud.XXXXXX).tar.gz"
BACKEND_JAR="$REPO_ROOT/hragentv1/backend/target/hragentv1-0.0.1-SNAPSHOT.jar"

if [[ ! -f "$BACKEND_JAR" ]]; then
  echo "Missing backend JAR: $BACKEND_JAR" >&2
  echo "Build it locally with Java 21 before deploying." >&2
  exit 1
fi

cleanup() {
  ssh -S "$CONTROL_SOCKET" -O exit "$TARGET" >/dev/null 2>&1 || true
  rm -f "$CONTROL_SOCKET" "$ARCHIVE"
}
trap cleanup EXIT

echo "Checking SSH access to $TARGET..."
ssh "${SSH_OPTS[@]}" "$TARGET" 'printf "Connected to "; hostname; command -v docker || true'

REMOTE_SUDO=0
if [[ "$SERVER_USER" != "root" ]]; then
  REMOTE_SUDO=1
  echo "The server user needs sudo access to run Docker. Enter the server password in this terminal when prompted."
  ssh -tt "${SSH_OPTS[@]}" "$TARGET" 'sudo -v'
fi

echo "Creating a source archive without local credentials or runtime data..."
COPYFILE_DISABLE=1 tar -czf "$ARCHIVE" \
  --exclude='./.git' \
  --exclude='./**/.env' \
  --exclude='./**/saas-agent.env' \
  --exclude='./**/node_modules' \
  --exclude='./**/target' \
  --exclude='./**/dist' \
  --exclude='./**/.vite' \
  --exclude='./**/__pycache__' \
  --exclude='./**/data' \
  --exclude='./**/uploads' \
  --exclude='./**/backups' \
  --exclude='./**/.backups' \
  --exclude='./**/tmp' \
  --exclude='./**/*.local' \
  --exclude='./n8nwork/workflows/.*.json' \
  --exclude='./n8nwork/knowledge-files/*' \
  --exclude='./启动员工关系智能体.command' \
  -C "$REPO_ROOT" .

echo "Uploading source archive..."
scp "${SSH_OPTS[@]}" "$ARCHIVE" "$TARGET:/tmp/hragent-cloud.tar.gz"
echo "Uploading backend JAR..."
scp "${SSH_OPTS[@]}" "$BACKEND_JAR" "$TARGET:/tmp/hragent-backend.jar"

echo "Installing and starting the cloud demo..."
ssh "${SSH_OPTS[@]}" "$TARGET" "REMOTE_DIR='$REMOTE_DIR' SERVER_HOST='$SERVER_HOST' PUBLIC_DOMAIN='$PUBLIC_DOMAIN' SERVER_USER='$SERVER_USER' REMOTE_SUDO='$REMOTE_SUDO' bash -s" <<'REMOTE_SCRIPT'
set -euo pipefail

if [[ "${REMOTE_SUDO:-0}" == "1" ]]; then
  privileged=(sudo)
  docker_cmd=(sudo docker)
else
  privileged=()
  docker_cmd=(docker)
fi

if ! command -v docker >/dev/null 2>&1; then
  if ! command -v curl >/dev/null 2>&1; then
    if command -v apt-get >/dev/null 2>&1; then
      "${privileged[@]}" apt-get update
      "${privileged[@]}" apt-get install -y curl ca-certificates
    elif command -v dnf >/dev/null 2>&1; then
      "${privileged[@]}" dnf install -y curl ca-certificates
    elif command -v yum >/dev/null 2>&1; then
      "${privileged[@]}" yum install -y curl ca-certificates
    else
      echo "Cannot install curl automatically on this operating system." >&2
      exit 1
    fi
  fi
  curl -fsSL https://get.docker.com | "${privileged[@]}" sh
fi

"${privileged[@]}" systemctl enable --now docker 2>/dev/null || true
"${docker_cmd[@]}" compose version >/dev/null

mkdir -p "$REMOTE_DIR"
tar -xzf /tmp/hragent-cloud.tar.gz -C "$REMOTE_DIR"
rm -f /tmp/hragent-cloud.tar.gz
mkdir -p "$REMOTE_DIR/hragentv1/backend/target"
mv /tmp/hragent-backend.jar "$REMOTE_DIR/hragentv1/backend/target/hragentv1-0.0.1-SNAPSHOT.jar"

set_env_value() {
  local path="$1" key="$2" value="$3" temp
  temp="$(mktemp)"
  awk -v key="$key" -v value="$value" '
    BEGIN { updated = 0 }
    index($0, key "=") == 1 { print key "=" value; updated = 1; next }
    { print }
    END { if (!updated) print key "=" value }
  ' "$path" > "$temp"
  mv "$temp" "$path"
}

bash "$REMOTE_DIR/scripts/initialize-hragent-config.sh"

set_env_value "$REMOTE_DIR/n8nwork/.env" N8N_HOST "automation.$PUBLIC_DOMAIN"
set_env_value "$REMOTE_DIR/n8nwork/.env" N8N_PROTOCOL https
set_env_value "$REMOTE_DIR/n8nwork/.env" N8N_EDITOR_BASE_URL "https://automation.$PUBLIC_DOMAIN"
set_env_value "$REMOTE_DIR/n8nwork/.env" WEBHOOK_URL "https://automation.$PUBLIC_DOMAIN/"
set_env_value "$REMOTE_DIR/n8nwork/.env" N8N_SECURE_COOKIE true
set_env_value "$REMOTE_DIR/n8nwork/.env" N8N_HTTP_PROXY ""
set_env_value "$REMOTE_DIR/n8nwork/.env" N8N_HTTPS_PROXY ""
set_env_value "$REMOTE_DIR/n8nwork/.env" OLLAMA_HTTP_PROXY ""
set_env_value "$REMOTE_DIR/n8nwork/.env" OLLAMA_HTTPS_PROXY ""
set_env_value "$REMOTE_DIR/hragentv1/.env" APP_DOMAIN "app.$PUBLIC_DOMAIN"
set_env_value "$REMOTE_DIR/hragentv1/.env" CHAT_DOMAIN "chat.$PUBLIC_DOMAIN"
set_env_value "$REMOTE_DIR/hragentv1/.env" N8N_DOMAIN "automation.$PUBLIC_DOMAIN"
set_env_value "$REMOTE_DIR/hragentv1/.env" CADDY_EMAIL "${CADDY_EMAIL:-admin@$PUBLIC_DOMAIN}"
set_env_value "$REMOTE_DIR/hragentv1/.env" APP_CORS_ALLOWED_ORIGINS "https://app.$PUBLIC_DOMAIN,https://chat.$PUBLIC_DOMAIN"

# The exported workflow JSON uses local URLs for the demo. Keep service-to-service
# calls on the Docker network and make the public onboarding link usable remotely.
find "$REMOTE_DIR/n8nwork/workflows" -type f -name '*.json' -exec sed -i \
  -e 's#http://localhost:5678#http://n8n:5678#g' \
  -e "s#http://localhost:5173#https://app.$PUBLIC_DOMAIN#g" {} +

N8N_COMPOSE=("${docker_cmd[@]}" compose --env-file "$REMOTE_DIR/n8nwork/.env" -f "$REMOTE_DIR/n8nwork/docker-compose.yml" -f "$REMOTE_DIR/n8nwork/docker-compose.cloud.yml")
APP_COMPOSE=("${docker_cmd[@]}" compose --env-file "$REMOTE_DIR/hragentv1/.env" -f "$REMOTE_DIR/hragentv1/docker-compose.yml" -f "$REMOTE_DIR/hragentv1/docker-compose.cloud.yml")

"${N8N_COMPOSE[@]}" up -d postgres qdrant ollama pdf-parser ocr n8n
"${APP_COMPOSE[@]}" up -d --build

echo "Waiting for application containers..."
for url in "http://127.0.0.1:5173" "http://127.0.0.1:5174" "http://127.0.0.1:5678/healthz"; do
  for attempt in $(seq 1 60); do
    if curl -fsS --max-time 3 "$url" >/dev/null 2>&1; then
      echo "[OK] $url"
      break
    fi
    if [[ "$attempt" == 60 ]]; then
      echo "[WARN] $url did not respond in time"
    fi
    sleep 2
  done
done

echo "Importing and publishing workflows..."
DOCKER_USE_SUDO="$REMOTE_SUDO" bash "$REMOTE_DIR/scripts/import-n8n-workflows.sh"

echo
echo "Cloud demo is running."
echo "Management portal: https://app.$PUBLIC_DOMAIN"
echo "AI chat portal:    https://chat.$PUBLIC_DOMAIN"
echo "Automation webhook: https://automation.$PUBLIC_DOMAIN/webhook/..."
echo "n8n editor:        ssh -L 5678:127.0.0.1:5678 $SERVER_USER@$SERVER_HOST"
echo "Next: create the remote SaaS Agent API key and DeepSeek credential."
REMOTE_SCRIPT

echo "Deployment finished."
