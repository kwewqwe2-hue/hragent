#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SAAS_DIR="$REPO_ROOT/hragentv1"
N8N_DIR="$REPO_ROOT/n8nwork"
SAAS_ENV="$SAAS_DIR/.env"
N8N_ENV="$N8N_DIR/.env"
AGENT_ENV="$N8N_DIR/saas-agent.env"

for required in "$SAAS_DIR/.env.example" "$N8N_DIR/.env.example" "$N8N_DIR/saas-agent.env.example"; do
  if [[ ! -f "$required" ]]; then
    echo "Missing configuration template: $required" >&2
    exit 1
  fi
done

[[ -f "$SAAS_ENV" ]] || cp "$SAAS_DIR/.env.example" "$SAAS_ENV"
[[ -f "$N8N_ENV" ]] || cp "$N8N_DIR/.env.example" "$N8N_ENV"
[[ -f "$AGENT_ENV" ]] || cp "$N8N_DIR/saas-agent.env.example" "$AGENT_ENV"

random_secret() {
  openssl rand -hex 48
}

get_env_value() {
  local path="$1"
  local key="$2"
  awk -v key="$key" 'index($0, key "=") == 1 {sub("^[^=]*=", ""); print; exit}' "$path"
}

set_env_value() {
  local path="$1"
  local key="$2"
  local value="$3"
  local temp
  temp="$(mktemp)"
  awk -v key="$key" -v value="$value" '
    BEGIN { updated = 0 }
    index($0, key "=") == 1 { print key "=" value; updated = 1; next }
    { print }
    END { if (!updated) print key "=" value }
  ' "$path" > "$temp"
  mv "$temp" "$path"
}

ensure_secret() {
  local path="$1"
  local key="$2"
  local value
  value="$(get_env_value "$path" "$key" || true)"
  if [[ -z "$value" || "$value" == replace-* || "$value" == local-demo* ]]; then
    set_env_value "$path" "$key" "$(random_secret)"
  fi
}

for key in MYSQL_ROOT_PASSWORD MYSQL_PASSWORD APP_ENCRYPTION_KEY HRAGENT_WEB_CHAT_IDENTITY_SECRET; do
  ensure_secret "$SAAS_ENV" "$key"
done

for key in N8N_POSTGRES_PASSWORD N8N_ENCRYPTION_KEY; do
  ensure_secret "$N8N_ENV" "$key"
done

attachment_key="$(get_env_value "$SAAS_ENV" HRAGENT_WEB_ATTACHMENT_INTERNAL_KEY || true)"
if [[ -z "$attachment_key" || "$attachment_key" == replace-* || "$attachment_key" == local-demo* ]]; then
  attachment_key="$(random_secret)"
fi
set_env_value "$SAAS_ENV" HRAGENT_WEB_ATTACHMENT_INTERNAL_KEY "$attachment_key"
set_env_value "$N8N_ENV" HRAGENT_WEB_ATTACHMENT_INTERNAL_KEY "$attachment_key"

echo "Local configuration is ready."
echo "SaaS: $SAAS_ENV"
echo "n8n:  $N8N_ENV"
echo "Real credentials remain untracked by Git."
