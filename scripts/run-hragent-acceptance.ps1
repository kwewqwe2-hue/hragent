param(
    [string]$RepoRoot = '',
    [string]$N8nDir = '',
    [string]$SaasBaseUrl = 'http://localhost:8080/api',
    [string]$EmployeeDingTalkUserId = '',
    [string]$ManagerDingTalkUserId = '',
    [switch]$NoPause
)

$ErrorActionPreference = 'Stop'
if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent $PSScriptRoot }
$N8nDir = if ($N8nDir) { $N8nDir } else { Join-Path $RepoRoot 'n8nwork' }
$passed = 0
$failed = 0
$skipped = 0

function Wait-BeforeExit {
    if ($NoPause) {
        return
    }
    Write-Host ''
    [void](Read-Host '验收完成，按 Enter 键关闭窗口')
}

trap {
    Write-Host ''
    Write-Host 'Acceptance script failed:' -ForegroundColor Red
    Write-Host ($_ | Out-String) -ForegroundColor Red
    Wait-BeforeExit
    exit 1
}

function Result {
    param([string]$Name, [bool]$Ok, [string]$Detail)
    if ($Ok) {
        $script:passed++
        Write-Host "[PASS] $Name - $Detail" -ForegroundColor Green
    } else {
        $script:failed++
        Write-Host "[FAIL] $Name - $Detail" -ForegroundColor Red
    }
}

function Skip {
    param([string]$Name, [string]$Detail)
    $script:skipped++
    Write-Host "[SKIP] $Name - $Detail" -ForegroundColor Yellow
}

function Invoke-Agent {
    param(
        [string]$Method,
        [string]$Path,
        [string]$UserId,
        [object]$Body = $null,
        [string]$ApiKeyOverride = ''
    )
    $keyLine = Get-Content (Join-Path $N8nDir 'saas-agent.env') |
        Where-Object { $_ -match '^SAAS_AGENT_API_KEY=' } |
        Select-Object -First 1
    $apiKey = if ($ApiKeyOverride) { $ApiKeyOverride } else { ($keyLine -split '=', 2)[1].Trim() }
    $headers = @{ 'X-API-Key' = $apiKey }
    if ($UserId) { $headers['X-DingTalk-User-Id'] = $UserId }
    $uri = "$SaasBaseUrl/internal/agent/v1$Path"
    try {
        if ($null -eq $Body) {
            $response = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri $uri -Headers $headers -ErrorAction Stop
        } else {
            $response = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri $uri -Headers $headers `
                -ContentType 'application/json' -Body ($Body | ConvertTo-Json -Compress) -ErrorAction Stop
        }
        return [pscustomobject]@{
            Http = [int]$response.StatusCode
            Json = ($response.Content | ConvertFrom-Json)
        }
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        $content = ''
        if ($_.Exception.Response) {
            $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
            $content = $reader.ReadToEnd()
        }
        return [pscustomobject]@{
            Http = $status
            Json = if ($content) { $content | ConvertFrom-Json } else { $null }
        }
    }
}

Write-Host 'HR Agent boundary acceptance' -ForegroundColor Cyan

if ([string]::IsNullOrWhiteSpace($EmployeeDingTalkUserId) -or [string]::IsNullOrWhiteSpace($ManagerDingTalkUserId)) {
    throw 'Pass -EmployeeDingTalkUserId and -ManagerDingTalkUserId. Real DingTalk identifiers are never stored in Git.'
}

$me = Invoke-Agent GET '/me' $EmployeeDingTalkUserId
Result 'Employee identity' ($me.Http -eq 200 -and $me.Json.success -and $me.Json.data.employeeNo -eq 'E001') "HTTP $($me.Http)"

$balances = Invoke-Agent GET '/balances' $EmployeeDingTalkUserId
Result 'Employee balance query' ($balances.Http -eq 200 -and $balances.Json.success) "HTTP $($balances.Http)"

$weekend = Invoke-Agent -Method POST -Path '/leave/preview' -UserId $EmployeeDingTalkUserId -Body @{
    leaveType = 'ANNUAL'; startDate = '2026-07-25'; endDate = '2026-07-26'; reason = 'boundary test'
}
Result 'Weekend-only request rejected' ($weekend.Http -eq 400) "HTTP $($weekend.Http)"

$mixedWeekend = Invoke-Agent -Method POST -Path '/leave/preview' -UserId $EmployeeDingTalkUserId -Body @{
    leaveType = 'PERSONAL'; startDate = '2026-09-18'; endDate = '2026-09-21'; reason = 'boundary test'
}
Result 'Weekend is not charged' ($mixedWeekend.Http -eq 200 -and $mixedWeekend.Json.data.workingDays -eq 2) "HTTP $($mixedWeekend.Http)"

$annual = Invoke-Agent -Method POST -Path '/leave/preview' -UserId $EmployeeDingTalkUserId -Body @{
    leaveType = 'ANNUAL'; startDate = '2026-09-16'; endDate = '2026-09-16'; reason = 'boundary test'
}
if ($balances.Json.data.balances | Where-Object { $_.leaveType -eq 'ANNUAL' -and $_.availableDays -le 0 }) {
    Result 'Insufficient annual balance rejected' ($annual.Http -eq 400) "HTTP $($annual.Http)"
} else {
    Skip 'Insufficient annual balance' 'current annual balance is not zero'
}

$requests = Invoke-Agent GET '/leave/requests' $EmployeeDingTalkUserId
$approved = @($requests.Json.data | Where-Object { $_.status -eq 'APPROVED' } | Select-Object -First 1)
if ($approved.Count -gt 0) {
    $date = $approved[0].startDate
    $dateText = if ($date -is [string]) {
        $date
    } else {
        '{0:D4}-{1:D2}-{2:D2}' -f $date[0], $date[1], $date[2]
    }
    $duplicate = Invoke-Agent -Method POST -Path '/leave/preview' -UserId $EmployeeDingTalkUserId -Body @{
        leaveType = $approved[0].leaveType; startDate = $dateText; endDate = $dateText; reason = 'boundary test'
    }
    Result 'Duplicate approved date rejected' ($duplicate.Http -eq 400) "HTTP $($duplicate.Http)"
} else {
    Skip 'Duplicate approved date' 'no approved request exists'
}

$unknown = Invoke-Agent GET '/me' 'not-bound-user'
Result 'Unknown DingTalk identity denied' ($unknown.Http -eq 403) "HTTP $($unknown.Http)"

$invalid = Invoke-Agent GET '/me' $EmployeeDingTalkUserId $null 'invalid-test-key'
Result 'Invalid API key denied' ($invalid.Http -eq 401) "HTTP $($invalid.Http)"

$managerPending = Invoke-Agent GET '/leave/pending' $ManagerDingTalkUserId
Result 'Manager pending endpoint available' ($managerPending.Http -eq 200 -and $managerPending.Json.success) "HTTP $($managerPending.Http)"

$employeePending = Invoke-Agent GET '/leave/pending' $EmployeeDingTalkUserId
Result 'Employee cannot see manager data' ($employeePending.Http -eq 200 -and @($employeePending.Json.data).Count -eq 0) "HTTP $($employeePending.Http), empty result"

Write-Host ''
Write-Host "Passed: $passed  Failed: $failed  Skipped: $skipped" -ForegroundColor Cyan
$exitCode = if ($failed -eq 0) { 0 } else { 1 }
Wait-BeforeExit
exit $exitCode
