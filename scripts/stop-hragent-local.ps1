[CmdletBinding()]
param(
    [string]$RepoRoot = '',
    [switch]$NoPause
)

$ErrorActionPreference = 'Stop'
if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent $PSScriptRoot }

function Wait-BeforeExit {
    if (-not $NoPause) {
        Write-Host ''
        [void](Read-Host 'Shutdown finished. Press Enter to close')
    }
}

trap {
    Write-Host ''
    Write-Host 'Shutdown failed:' -ForegroundColor Red
    Write-Host ($_ | Out-String) -ForegroundColor Red
    Wait-BeforeExit
    exit 1
}

$saasCompose = Join-Path $RepoRoot 'hragentv1\docker-compose.yml'
$n8nCompose = Join-Path $RepoRoot 'n8nwork\docker-compose.yml'

docker compose -f $saasCompose down
if ($LASTEXITCODE -ne 0) { throw 'Stopping SaaS containers failed.' }
docker compose -f $n8nCompose --profile quick-tunnel down
if ($LASTEXITCODE -ne 0) { throw 'Stopping automation containers failed.' }

Write-Host 'HR Agent containers stopped. Docker volumes were preserved.' -ForegroundColor Green
Write-Host 'Do not add -v unless you intentionally want to delete all local data.' -ForegroundColor Yellow
Wait-BeforeExit
