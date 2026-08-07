[CmdletBinding()]
param(
    [string]$RepoRoot = '',
    [string]$N8nDir = '',
    [switch]$NewTunnel,
    [switch]$CopyCallback,
    [switch]$NoPause
)

$ErrorActionPreference = 'Stop'
if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent $PSScriptRoot }
$N8nDir = if ($N8nDir) { $N8nDir } else { Join-Path $RepoRoot 'n8nwork' }

function Wait-BeforeExit {
    if ($NoPause) {
        return
    }
    Write-Host ''
    [void](Read-Host '脚本执行结束，按 Enter 键关闭窗口')
}

trap {
    Write-Host ''
    Write-Host 'Tunnel refresh script failed:' -ForegroundColor Red
    Write-Host ($_ | Out-String) -ForegroundColor Red
    Wait-BeforeExit
    exit 1
}

function Test-DockerReady {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $version = docker info --format '{{.ServerVersion}}' 2>$null
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    return ($exitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($version))
}

function Wait-Docker {
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        if (Test-DockerReady) {
            return
        }
        Start-Sleep -Seconds 5
    }
    throw 'Docker Desktop did not become ready within 120 seconds.'
}

function Assert-NativeSuccess {
    param([string]$Action)
    if ($LASTEXITCODE -ne 0) {
        throw "$Action failed with exit code $LASTEXITCODE."
    }
}

function Set-EnvValues {
    param(
        [string]$Path,
        [hashtable]$Values
    )

    $lines = @(Get-Content -LiteralPath $Path -Encoding UTF8)
    $changed = $false

    foreach ($key in $Values.Keys) {
        $newLine = "$key=$($Values[$key])"
        $found = $false
        for ($index = 0; $index -lt $lines.Count; $index++) {
            if ($lines[$index] -match ('^' + [regex]::Escape($key) + '=')) {
                $found = $true
                if ($lines[$index] -ne $newLine) {
                    $lines[$index] = $newLine
                    $changed = $true
                }
                break
            }
        }
        if (-not $found) {
            $lines += $newLine
            $changed = $true
        }
    }

    if ($changed) {
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllLines($Path, $lines, $utf8NoBom)
    }
    return $changed
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'docker is not available in PATH. Start Docker Desktop first.'
}

if (-not (Test-DockerReady)) {
    $dockerDesktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
    if (Test-Path -LiteralPath $dockerDesktop) {
        Write-Host 'Docker Desktop is not ready. Starting it and waiting for the engine...' -ForegroundColor Yellow
        Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
        Wait-Docker
    } else {
        throw "Docker Desktop was not found at $dockerDesktop."
    }
}

$composePath = Join-Path $N8nDir 'docker-compose.yml'
$envPath = Join-Path $N8nDir '.env'
if (-not (Test-Path -LiteralPath $composePath)) {
    throw "docker-compose.yml was not found in $N8nDir."
}
if (-not (Test-Path -LiteralPath $envPath)) {
    throw ".env was not found in $N8nDir."
}

Write-Host 'Starting n8n and PostgreSQL...' -ForegroundColor Yellow
Push-Location $N8nDir
try {
    docker compose up -d postgres n8n
    Assert-NativeSuccess 'Starting n8n'

    if ($NewTunnel) {
        Write-Host 'Creating a new Quick Tunnel...' -ForegroundColor Yellow
        docker compose --profile quick-tunnel up -d --force-recreate --no-deps cloudflared-quick
        Assert-NativeSuccess 'Recreating Quick Tunnel'
    } else {
        Write-Host 'Starting or reusing the current Quick Tunnel...' -ForegroundColor Yellow
        docker compose --profile quick-tunnel up -d cloudflared-quick
        Assert-NativeSuccess 'Starting Quick Tunnel'
    }
} finally {
    Pop-Location
}

$publicBase = ''
$lastCandidate = ''
$stableReads = 0
$deadline = (Get-Date).AddSeconds(90)
while ((Get-Date) -lt $deadline -and [string]::IsNullOrWhiteSpace($publicBase)) {
    Push-Location $N8nDir
    try {
        $log = docker compose logs cloudflared-quick --no-color 2>$null | Out-String
    } finally {
        Pop-Location
    }
    $matches = [regex]::Matches($log, 'https://[a-z0-9-]+\.trycloudflare\.com')
    if ($matches.Count -gt 0) {
        $candidate = $matches[$matches.Count - 1].Value.TrimEnd('/')
        if ($candidate -eq $lastCandidate) {
            $stableReads++
        } else {
            $lastCandidate = $candidate
            $stableReads = 1
        }
        if ($stableReads -ge 2) {
            $publicBase = $candidate
        } else {
            Start-Sleep -Seconds 3
        }
    } else {
        Start-Sleep -Seconds 3
    }
}

if ([string]::IsNullOrWhiteSpace($publicBase)) {
    throw 'The Quick Tunnel URL was not found within 90 seconds. Check cloudflared-quick logs.'
}

$changed = Set-EnvValues -Path $envPath -Values @{
    WEBHOOK_URL = "$publicBase/"
    DINGTALK_APPROVAL_BASE_URL = $publicBase
}

Push-Location $N8nDir
try {
    if ($changed) {
        Write-Host 'Tunnel URL changed. Recreating n8n with the new configuration...' -ForegroundColor Yellow
        docker compose up -d --force-recreate n8n
        Assert-NativeSuccess 'Recreating n8n'
    } else {
        Write-Host 'n8n already uses the current tunnel URL.' -ForegroundColor Green
    }
} finally {
    Pop-Location
}

$n8nReady = $false
$healthDeadline = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $healthDeadline) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:5678/healthz' -TimeoutSec 5
        if ([int]$response.StatusCode -eq 200) {
            $n8nReady = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 3
    }
}
if (-not $n8nReady) {
    throw 'n8n did not become healthy within 60 seconds.'
}

$callbackUrl = "$publicBase/webhook/hragent-dingtalk-8a2d811d-0fe5-4198-8e61-288ec417fc04"
$approvalUrl = "$publicBase/webhook/hragent-dingtalk-leave-card"
if ($CopyCallback) {
    Set-Clipboard -Value $callbackUrl
}

Write-Host ''
Write-Host "Quick Tunnel:            $publicBase" -ForegroundColor Green
Write-Host "DingTalk robot callback: $callbackUrl" -ForegroundColor Cyan
Write-Host "Approval callback:       $approvalUrl" -ForegroundColor Cyan
Write-Host ''
if ($CopyCallback) {
    Write-Host 'The DingTalk robot callback was copied to the clipboard.' -ForegroundColor Green
}
Write-Host 'Update the DingTalk developer console and republish the app when this URL changes.' -ForegroundColor Yellow
Wait-BeforeExit
