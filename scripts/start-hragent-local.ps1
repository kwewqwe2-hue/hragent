[CmdletBinding()]
param(
    [string]$RepoRoot = '',
    [switch]$Build,
    [switch]$PullEmbeddingModel,
    [switch]$NewTunnel,
    [switch]$NoTunnel,
    [switch]$NoPause
)

$ErrorActionPreference = 'Stop'
if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent $PSScriptRoot }

function Wait-BeforeExit {
    if (-not $NoPause) {
        Write-Host ''
        [void](Read-Host 'Startup finished. Press Enter to close')
    }
}

trap {
    Write-Host ''
    Write-Host 'Startup failed:' -ForegroundColor Red
    Write-Host ($_ | Out-String) -ForegroundColor Red
    Wait-BeforeExit
    exit 1
}

function Assert-NativeSuccess {
    param([string]$Action)
    if ($LASTEXITCODE -ne 0) { throw "$Action failed with exit code $LASTEXITCODE." }
}

function Test-DockerReady {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $version = docker info --format '{{.ServerVersion}}' 2>$null
        return ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($version))
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Wait-Docker {
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        if (Test-DockerReady) { return }
        Start-Sleep -Seconds 5
    }
    throw 'Docker Desktop did not become ready within 120 seconds.'
}

function Wait-Endpoint {
    param([string]$Name, [string]$Url, [int]$TimeoutSeconds = 120)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
            if ([int]$response.StatusCode -ge 200 -and [int]$response.StatusCode -lt 500) { return }
        } catch {
            Start-Sleep -Seconds 3
        }
    }
    throw "$Name did not become ready within $TimeoutSeconds seconds."
}

function Set-EnvValue {
    param([string]$Path, [string]$Key, [string]$Value)
    $lines = @(Get-Content -LiteralPath $Path -Encoding UTF8)
    $found = $false
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match ('^' + [regex]::Escape($Key) + '=')) {
            $found = $true
            $lines[$index] = "$Key=$Value"
            break
        }
    }
    if (-not $found) { $lines += "$Key=$Value" }
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllLines($Path, $lines, $utf8NoBom)
}

Write-Host 'Starting HR Agent local environment' -ForegroundColor Cyan

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'docker is not available in PATH. Install Docker Desktop first.'
}

$saasDir = Join-Path $RepoRoot 'hragentv1'
$n8nDir = Join-Path $RepoRoot 'n8nwork'
& (Join-Path $PSScriptRoot 'initialize-hragent-config.ps1') -RepoRoot $RepoRoot -NoPause

if (-not (Test-DockerReady)) {
    $dockerDesktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path -LiteralPath $dockerDesktop)) {
        throw "Docker Desktop was not found at $dockerDesktop."
    }
    Write-Host 'Starting Docker Desktop...' -ForegroundColor Yellow
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
    Wait-Docker
}

Write-Host 'Starting n8n, PostgreSQL, Qdrant, Ollama, OCR, and PDF parser...' -ForegroundColor Yellow
Push-Location $n8nDir
try {
    docker compose up -d postgres qdrant ollama pdf-parser ocr n8n
    Assert-NativeSuccess 'Starting automation services'
    if ($PullEmbeddingModel) {
        docker exec hragent-n8n-ollama ollama pull bge-m3
        Assert-NativeSuccess 'Pulling bge-m3'
    }
} finally {
    Pop-Location
}

Write-Host 'Starting SaaS, administration UI, and AI web client...' -ForegroundColor Yellow
Push-Location $saasDir
try {
    if ($Build) {
        docker compose up -d --build
    } else {
        docker compose up -d
    }
    Assert-NativeSuccess 'Starting SaaS services'
} finally {
    Pop-Location
}

$publicBase = ''
if (-not $NoTunnel) {
    Push-Location $n8nDir
    try {
        if ($NewTunnel) {
            docker compose --profile quick-tunnel up -d --force-recreate --no-deps cloudflared-quick
        } else {
            docker compose --profile quick-tunnel up -d cloudflared-quick
        }
        Assert-NativeSuccess 'Starting Quick Tunnel'
    } finally {
        Pop-Location
    }

    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline -and [string]::IsNullOrWhiteSpace($publicBase)) {
        $log = docker compose -f (Join-Path $n8nDir 'docker-compose.yml') logs cloudflared-quick --no-color 2>$null | Out-String
        $matches = [regex]::Matches($log, 'https://[a-z0-9-]+\.trycloudflare\.com')
        if ($matches.Count -gt 0) {
            $publicBase = $matches[$matches.Count - 1].Value.TrimEnd('/')
        } else {
            Start-Sleep -Seconds 3
        }
    }
    if ([string]::IsNullOrWhiteSpace($publicBase)) {
        throw 'Quick Tunnel URL was not found. Check proxy settings and cloudflared logs.'
    }

    $n8nEnv = Join-Path $n8nDir '.env'
    Set-EnvValue -Path $n8nEnv -Key 'WEBHOOK_URL' -Value "$publicBase/"
    Set-EnvValue -Path $n8nEnv -Key 'DINGTALK_APPROVAL_BASE_URL' -Value $publicBase
    Push-Location $n8nDir
    try {
        docker compose up -d --force-recreate n8n
        Assert-NativeSuccess 'Applying tunnel URL to n8n'
    } finally {
        Pop-Location
    }
}

Write-Host ''
Write-Host 'SaaS:       http://localhost:5173' -ForegroundColor Green
Write-Host 'AI web:     http://localhost:5174' -ForegroundColor Green
Write-Host 'SaaS API:   http://localhost:8080/api' -ForegroundColor Green
Write-Host 'n8n editor: http://localhost:5678' -ForegroundColor Green
if ($publicBase) {
    Write-Host "Tunnel:     $publicBase" -ForegroundColor Green
    Write-Host "DingTalk:   $publicBase/webhook/hragent-dingtalk-8a2d811d-0fe5-4198-8e61-288ec417fc04" -ForegroundColor Yellow
    Write-Host "Approval:   $publicBase/webhook/hragent-dingtalk-leave-card" -ForegroundColor Yellow
}

Write-Host 'Waiting for local services...' -ForegroundColor Yellow
Wait-Endpoint -Name 'n8n' -Url 'http://localhost:5678/healthz'
Wait-Endpoint -Name 'SaaS API' -Url 'http://localhost:8080/api/health'
Wait-Endpoint -Name 'SaaS frontend' -Url 'http://localhost:5173/'
Wait-Endpoint -Name 'AI web' -Url 'http://localhost:5174/'

& (Join-Path $PSScriptRoot 'check-hragent-local.ps1') -RepoRoot $RepoRoot -NoPause
Wait-BeforeExit
