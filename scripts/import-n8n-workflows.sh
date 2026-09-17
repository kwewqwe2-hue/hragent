#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
N8N_DIR="$REPO_ROOT/n8nwork"

if [[ ! -f "$N8N_DIR/.env" || ! -f "$N8N_DIR/saas-agent.env" ]]; then
  echo "Missing n8n configuration. Run initialize-hragent-config.sh first." >&2
  exit 1
fi

if [[ "${DOCKER_USE_SUDO:-0}" == "1" ]]; then
  docker_cmd=(sudo docker)
else
  docker_cmd=(docker)
fi

compose=("${docker_cmd[@]}" compose --env-file "$N8N_DIR/.env" -f "$N8N_DIR/docker-compose.yml" -f "$N8N_DIR/docker-compose.cloud.yml")

echo "Importing n8n workflows..."
"${compose[@]}" --profile tools run --rm n8n-import

echo "Checking required workflows..."
for workflow_id in \
  LLWdzAOEECp9eSIf \
  3RiI6nH28eRUuOaz \
  b83719c3-7b65-4d8a-9c48-6f5fa4b7f421 \
  c8d9e0f1-2a3b-4c5d-6e7f-8a9b0c1d2e3f \
  e8f1a4c2-7b90-4d35-9c61-2a5e8f0b3d17 \
  9f6f1e91-1d0e-4f5c-8fb5-7c2f4f3d9a01 \
  d5e6f7a8-9012-4b3c-8d5e-6f708192a3b4 \
  c4e6a8b0-2d1f-4c93-8e75-1a6b9d0f2c34 \
  f6b8d2a4-1c73-4e95-9a20-7d4c6b8e1f32; do
  if ! "${docker_cmd[@]}" exec hragent-n8n n8n export:workflow --id="$workflow_id" >/dev/null 2>&1; then
    echo "Workflow $workflow_id was not imported." >&2
    exit 1
  fi
done

workflow_ids=(
  LLWdzAOEECp9eSIf
  3RiI6nH28eRUuOaz
  b83719c3-7b65-4d8a-9c48-6f5fa4b7f421
  c8d9e0f1-2a3b-4c5d-6e7f-8a9b0c1d2e3f
  e8f1a4c2-7b90-4d35-9c61-2a5e8f0b3d17
  9f6f1e91-1d0e-4f5c-8fb5-7c2f4f3d9a01
  d5e6f7a8-9012-4b3c-8d5e-6f708192a3b4
  c4e6a8b0-2d1f-4c93-8e75-1a6b9d0f2c34
  f6b8d2a4-1c73-4e95-9a20-7d4c6b8e1f32
)

for workflow_id in "${workflow_ids[@]}"; do
  "${docker_cmd[@]}" exec hragent-n8n n8n publish:workflow --id="$workflow_id"
done

echo "Nine workflows were imported and published."
echo "Create the remote DeepSeek credential in n8n, then test the AI chat page."
