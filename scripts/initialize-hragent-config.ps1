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
        [void](Read-Host 'Configuration initialized. Press Enter to close')
    }
}

trap {
    Write-Host ''
    Write-Host 'Configuration initialization failed:' -ForegroundColor Red
    Write-Host ($_ | Out-String) -ForegroundColor Red
    Wait-BeforeExit
    exit 1
}

function New-RandomSecret {
    $bytes = New-Object byte[] 48
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Get-EnvValue {
    param([string]$Path, [string]$Key)
    $line = Get-Content -LiteralPath $Path -Encoding UTF8 |
        Where-Object { $_ -match ('^' + [regex]::Escape($Key) + '=') } |
        Select-Object -First 1
    if (-not $line) { return '' }
    return ($line -split '=', 2)[1].Trim()
}

function Set-EnvValue {
    param([string]$Path, [string]$Key, [string]$Value)
    $lines = @(Get-Content -LiteralPath $Path -Encoding UTF8)
    $updated = $false
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match ('^' + [regex]::Escape($Key) + '=')) {
            $lines[$index] = "$Key=$Value"
            $updated = $true
            break
        }
    }
    if (-not $updated) { $lines += "$Key=$Value" }
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllLines($Path, $lines, $utf8NoBom)
}

function Ensure-Secret {
    param([string]$Path, [string]$Key)
    $value = Get-EnvValue -Path $Path -Key $Key
    if ([string]::IsNullOrWhiteSpace($value) -or $value -match '^(replace-|local-demo)') {
        Set-EnvValue -Path $Path -Key $Key -Value (New-RandomSecret)
    }
}

$saasDir = Join-Path $RepoRoot 'hragentv1'
$n8nDir = Join-Path $RepoRoot 'n8nwork'
$saasEnv = Join-Path $saasDir '.env'
$n8nEnv = Join-Path $n8nDir '.env'
$agentEnv = Join-Path $n8nDir 'saas-agent.env'

foreach ($required in @(
    (Join-Path $saasDir '.env.example'),
    (Join-Path $n8nDir '.env.example'),
    (Join-Path $n8nDir 'saas-agent.env.example')
)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Missing configuration template: $required"
    }
}

if (-not (Test-Path -LiteralPath $saasEnv)) {
    Copy-Item -LiteralPath (Join-Path $saasDir '.env.example') -Destination $saasEnv
}
if (-not (Test-Path -LiteralPath $n8nEnv)) {
    Copy-Item -LiteralPath (Join-Path $n8nDir '.env.example') -Destination $n8nEnv
}
if (-not (Test-Path -LiteralPath $agentEnv)) {
    Copy-Item -LiteralPath (Join-Path $n8nDir 'saas-agent.env.example') -Destination $agentEnv
}

foreach ($key in @('MYSQL_ROOT_PASSWORD', 'MYSQL_PASSWORD', 'APP_ENCRYPTION_KEY', 'HRAGENT_WEB_CHAT_IDENTITY_SECRET')) {
    Ensure-Secret -Path $saasEnv -Key $key
}
foreach ($key in @('N8N_POSTGRES_PASSWORD', 'N8N_ENCRYPTION_KEY')) {
    Ensure-Secret -Path $n8nEnv -Key $key
}

$attachmentKey = Get-EnvValue -Path $saasEnv -Key 'HRAGENT_WEB_ATTACHMENT_INTERNAL_KEY'
if ([string]::IsNullOrWhiteSpace($attachmentKey) -or $attachmentKey -match '^(replace-|local-demo)') {
    $attachmentKey = New-RandomSecret
}
Set-EnvValue -Path $saasEnv -Key 'HRAGENT_WEB_ATTACHMENT_INTERNAL_KEY' -Value $attachmentKey
Set-EnvValue -Path $n8nEnv -Key 'HRAGENT_WEB_ATTACHMENT_INTERNAL_KEY' -Value $attachmentKey

Write-Host 'Local configuration is ready.' -ForegroundColor Green
Write-Host "SaaS: $saasEnv"
Write-Host "n8n:  $n8nEnv"
Write-Host 'Real credentials remain untracked by Git.' -ForegroundColor Yellow
Wait-BeforeExit
