[CmdletBinding()]
param(
    [string]$RepoRoot = '',
    [switch]$Publish,
    [switch]$PublishOnly,
    [switch]$NoPause
)

$ErrorActionPreference = 'Stop'
if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent $PSScriptRoot }

function Wait-BeforeExit {
    if (-not $NoPause) {
        Write-Host ''
        [void](Read-Host 'Workflow import finished. Press Enter to close')
    }
}

trap {
    Write-Host ''
    Write-Host 'Workflow import failed:' -ForegroundColor Red
    Write-Host ($_ | Out-String) -ForegroundColor Red
    Wait-BeforeExit
    exit 1
}

function Assert-NativeSuccess {
    param([string]$Action)
    if ($LASTEXITCODE -ne 0) { throw "$Action failed with exit code $LASTEXITCODE." }
}

$n8nDir = Join-Path $RepoRoot 'n8nwork'
& (Join-Path $PSScriptRoot 'initialize-hragent-config.ps1') -RepoRoot $RepoRoot -NoPause

Push-Location $n8nDir
try {
    docker compose up -d postgres n8n
    Assert-NativeSuccess 'Starting n8n'
    if (-not $PublishOnly) {
        docker compose --profile tools run --rm n8n-import
        Assert-NativeSuccess 'Importing workflows'
    }

    if ($Publish -or $PublishOnly) {
        $ids = @(
            'LLWdzAOEECp9eSIf',
            '3RiI6nH28eRUuOaz',
            'b83719c3-7b65-4d8a-9c48-6f5fa4b7f421',
            'c8d9e0f1-2a3b-4c5d-6e7f-8a9b0c1d2e3f',
            'e8f1a4c2-7b90-4d35-9c61-2a5e8f0b3d17',
            '9f6f1e91-1d0e-4f5c-8fb5-7c2f4f3d9a01',
            'd5e6f7a8-9012-4b3c-8d5e-6f708192a3b4',
            'c4e6a8b0-2d1f-4c93-8e75-1a6b9d0f2c34',
            'f6b8d2a4-1c73-4e95-9a20-7d4c6b8e1f32'
        )
        foreach ($id in $ids) {
            docker exec hragent-n8n n8n publish:workflow --id=$id
            Assert-NativeSuccess "Publishing workflow $id"
        }
        docker compose up -d --force-recreate n8n
        Assert-NativeSuccess 'Restarting n8n'
    }
} finally {
    Pop-Location
}

if ($PublishOnly) {
    Write-Host 'Nine workflows were published.' -ForegroundColor Green
} elseif ($Publish) {
    Write-Host 'Nine workflows were imported and published.' -ForegroundColor Green
} else {
    Write-Host 'Nine workflows were imported but not published.' -ForegroundColor Green
    Write-Host 'Assign the DeepSeek, Qdrant, and Ollama credentials in n8n, then rerun with -Publish.' -ForegroundColor Yellow
}
Wait-BeforeExit
