param(
    [string]$RepoRoot = '',
    [string]$SaasDir = '',
    [string]$N8nDir = '',
    [switch]$NoPause
)

$ErrorActionPreference = 'Continue'
if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent $PSScriptRoot }
$SaasDir = if ($SaasDir) { $SaasDir } else { Join-Path $RepoRoot 'hragentv1' }
$N8nDir = if ($N8nDir) { $N8nDir } else { Join-Path $RepoRoot 'n8nwork' }

function Wait-BeforeExit {
    if ($NoPause) {
        return
    }
    Write-Host ''
    [void](Read-Host '检查完成，按 Enter 键关闭窗口')
}

trap {
    Write-Host ''
    Write-Host 'Health-check script failed:' -ForegroundColor Red
    Write-Host ($_ | Out-String) -ForegroundColor Red
    Wait-BeforeExit
    exit 1
}

function Write-Check {
    param(
        [string]$Name,
        [bool]$Ok,
        [string]$Detail
    )

    $mark = if ($Ok) { '[OK]' } else { '[FAIL]' }
    $color = if ($Ok) { 'Green' } else { 'Red' }
    Write-Host "$mark $Name - $Detail" -ForegroundColor $color
}

function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Url
    )

    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 15
        Write-Check $Name $true ("HTTP " + [int]$response.StatusCode)
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        if ($status) {
            Write-Check $Name $false ("HTTP " + $status)
        } else {
            Write-Check $Name $false $_.Exception.Message
        }
    }
}

Write-Host 'HR Agent local health check' -ForegroundColor Cyan
Write-Host "SaaS directory: $SaasDir"
Write-Host "n8n directory:  $N8nDir"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Check 'Docker command' $false 'docker is not available in PATH'
    Wait-BeforeExit
    exit 1
}
Write-Check 'Docker command' $true 'available'

if (-not (Test-Path (Join-Path $SaasDir 'docker-compose.yml'))) {
    Write-Check 'SaaS project' $false 'docker-compose.yml not found'
} else {
    Write-Host "`nSaaS containers" -ForegroundColor Yellow
    & docker compose -f (Join-Path $SaasDir 'docker-compose.yml') ps
    Write-Host ''
}

if (-not (Test-Path (Join-Path $N8nDir 'docker-compose.yml'))) {
    Write-Check 'n8n project' $false 'docker-compose.yml not found'
} else {
    Write-Host 'n8n containers' -ForegroundColor Yellow
    & docker compose -f (Join-Path $N8nDir 'docker-compose.yml') ps
    Write-Host ''
}

Test-Endpoint 'SaaS frontend' 'http://localhost:5173/'
Test-Endpoint 'HRAgent AI web' 'http://localhost:5174/'
Test-Endpoint 'SaaS API' 'http://localhost:8080/api/health'
Test-Endpoint 'n8n editor' 'http://localhost:5678/healthz'

$backendAttachmentKey = (& docker exec hragent-backend printenv WEB_ATTACHMENT_INTERNAL_KEY 2>$null | Out-String).Trim()
$n8nAttachmentKey = (& docker exec hragent-n8n printenv WEB_ATTACHMENT_INTERNAL_KEY 2>$null | Out-String).Trim()
$webChatIdentitySecret = (& docker exec hragent-backend printenv APP_WEB_CHAT_IDENTITY_SECRET 2>$null | Out-String).Trim()
$attachmentKeysOk = $backendAttachmentKey.Length -ge 32 `
    -and $backendAttachmentKey -eq $n8nAttachmentKey `
    -and $backendAttachmentKey -ne 'local-demo-web-attachment-internal-key-change-me'
$identitySecretOk = $webChatIdentitySecret.Length -ge 32 `
    -and $webChatIdentitySecret -ne 'local-demo-web-chat-identity-secret'
Write-Check 'Web attachment internal key' $attachmentKeysOk 'configured, non-default, and synchronized'
Write-Check 'Web chat identity secret' $identitySecretOk 'configured and non-default'

$tunnelLog = & docker compose -f (Join-Path $N8nDir 'docker-compose.yml') logs cloudflared-quick --no-color 2>$null | Out-String
$matches = [regex]::Matches($tunnelLog, 'https://[a-z0-9-]+\.trycloudflare\.com')
$publicBase = if ($matches.Count -gt 0) { $matches[$matches.Count - 1].Value.TrimEnd('/') } else { '' }

if ($publicBase) {
    Write-Check 'Quick Tunnel' $true $publicBase
    Write-Host "`nCopy these addresses to the DingTalk developer console when the tunnel URL changes:" -ForegroundColor Yellow
    Write-Host ($publicBase + '/webhook/hragent-dingtalk-8a2d811d-0fe5-4198-8e61-288ec417fc04')
    Write-Host ($publicBase + '/webhook/hragent-dingtalk-leave-card')
} else {
    Write-Check 'Quick Tunnel' $false 'public trycloudflare.com URL not found in container logs'
}

$envPath = Join-Path $N8nDir '.env'
if (Test-Path $envPath) {
    $envLines = Get-Content $envPath
    $configured = ($envLines | Where-Object { $_ -match '^WEBHOOK_URL=' } | Select-Object -First 1) -replace '^WEBHOOK_URL=', ''
    $configured = $configured.Trim().TrimEnd('/')
    if ($publicBase -and $configured -eq $publicBase) {
        Write-Check 'n8n WEBHOOK_URL' $true $configured
    } elseif ($configured) {
        Write-Check 'n8n WEBHOOK_URL' $false ("configured=$configured, tunnel=$publicBase")
    } else {
        Write-Check 'n8n WEBHOOK_URL' $false 'WEBHOOK_URL is missing from .env'
    }

    $approvalBase = ($envLines | Where-Object { $_ -match '^DINGTALK_APPROVAL_BASE_URL=' } | Select-Object -First 1) -replace '^DINGTALK_APPROVAL_BASE_URL=', ''
    $approvalBase = $approvalBase.Trim().TrimEnd('/')
    if ($publicBase -and $approvalBase -eq $publicBase) {
        Write-Check 'Approval base URL' $true $approvalBase
    } elseif ($approvalBase) {
        Write-Check 'Approval base URL' $false ("configured=$approvalBase, tunnel=$publicBase")
    } else {
        Write-Check 'Approval base URL' $false 'DINGTALK_APPROVAL_BASE_URL is missing from .env'
    }
} else {
    Write-Check 'n8n .env' $false 'file not found'
}

$activeWorkflowCount = & docker exec hragent-n8n-postgres psql -U n8n -d n8n -At -c "SELECT count(*) FROM workflow_entity WHERE active=true AND id IN ('LLWdzAOEECp9eSIf','3RiI6nH28eRUuOaz','b83719c3-7b65-4d8a-9c48-6f5fa4b7f421','c8d9e0f1-2a3b-4c5d-6e7f-8a9b0c1d2e3f','e8f1a4c2-7b90-4d35-9c61-2a5e8f0b3d17','9f6f1e91-1d0e-4f5c-8fb5-7c2f4f3d9a01','d5e6f7a8-9012-4b3c-8d5e-6f708192a3b4','c4e6a8b0-2d1f-4c93-8e75-1a6b9d0f2c34')" 2>$null
Write-Check 'Required n8n workflows' ($activeWorkflowCount.Trim() -eq '8') ("active=" + $activeWorkflowCount.Trim() + '/8')

$errorBindingCount = & docker exec hragent-n8n-postgres psql -U n8n -d n8n -At -c "SELECT count(*) FROM workflow_entity WHERE id IN ('LLWdzAOEECp9eSIf','3RiI6nH28eRUuOaz','b83719c3-7b65-4d8a-9c48-6f5fa4b7f421','c8d9e0f1-2a3b-4c5d-6e7f-8a9b0c1d2e3f','9f6f1e91-1d0e-4f5c-8fb5-7c2f4f3d9a01','d5e6f7a8-9012-4b3c-8d5e-6f708192a3b4','c4e6a8b0-2d1f-4c93-8e75-1a6b9d0f2c34') AND settings->>'errorWorkflow'='e8f1a4c2-7b90-4d35-9c61-2a5e8f0b3d17'" 2>$null
Write-Check 'n8n error workflow bindings' ($errorBindingCount.Trim() -eq '7') ("bound=" + $errorBindingCount.Trim() + '/7')

Write-Host "`nThis script only checks status. It does not restart containers or modify .env." -ForegroundColor Cyan
Wait-BeforeExit
