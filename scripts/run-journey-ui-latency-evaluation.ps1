param(
    [switch]$TestAccount,
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [ValidateRange(1, 100)][int]$Samples = 20,
    [ValidateRange(5000, 120000)][int]$TimeoutMs = 30000,
    [ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$NetworkConditions = 'unspecified'
)

$ErrorActionPreference = 'Stop'
if (-not $TestAccount) {
    throw 'Sign in to a dedicated test account, then pass -TestAccount. This test creates and confirms journeys that remain in that account.'
}
$projectRoot = Split-Path -Parent $PSScriptRoot
$package = 'com.ecostep.app'
$remoteDirectory = 'files/evaluation'
$outputDirectory = Join-Path $projectRoot 'evaluation_results'
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'adb.exe not found. Supply -AdbPath.' }
$deviceOutput = & $AdbPath devices
if ($LASTEXITCODE -ne 0) { throw 'Could not list devices.' }
$devices = @($deviceOutput | Where-Object { $_ -match '^\S+\s+device$' })
if ($devices.Count -ne 1) { throw 'Connect exactly one unlocked Android device or emulator.' }
$serial = ($devices[0] -split '\s+')[0]
function Get-ReportNames {
    & $AdbPath -s $serial shell run-as $package test -d $remoteDirectory
    if ($LASTEXITCODE -eq 1) { return }
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the report directory.' }
    $listing = & $AdbPath -s $serial shell run-as $package ls $remoteDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Cannot list reports.' }
    $listing | Where-Object { $_ -match '^journey-ui-latency-\d+\.json$' }
}
Push-Location $projectRoot
try {
    $gitCommit = & git rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git commit.' }
    $gitStatus = & git status --porcelain
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git status.' }
    $workingTreeDirty = if ($gitStatus) { 'true' } else { 'false' }
    & .\gradlew.bat installDebug installDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'Build or installation failed.' }
    $previousReports = @(Get-ReportNames)
    Write-Host "Live UI test: $Samples journeys will remain in the signed-in test account. Keep this device unlocked and do not interact with it."
    $lines = [System.Collections.Generic.List[string]]::new()
    & $AdbPath -s $serial shell am instrument -w -r `
        -e class com.ecostep.app.evaluation.JourneyUiLatencyEvaluationTest `
        -e live true -e testAccount true -e samples $Samples -e timeoutMs $TimeoutMs `
        -e gitCommit $gitCommit -e workingTreeDirty $workingTreeDirty `
        -e networkConditions $NetworkConditions `
        com.ecostep.app.test/androidx.test.runner.AndroidJUnitRunner | ForEach-Object {
            $lines.Add([string]$_)
            Write-Host $_
        }
    $instrumentExitCode = $LASTEXITCODE
    $newReports = @(Get-ReportNames | Where-Object { $_ -notin $previousReports })
    if ($newReports.Count -eq 0) { throw 'No report produced. Check the instrumentation output.' }
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    $failed = $false
    foreach ($name in $newReports) {
        foreach ($file in @($name, ($name -replace '\.json$', '.csv'))) {
            $content = & $AdbPath -s $serial exec-out run-as $package cat "$remoteDirectory/$file"
            if ($LASTEXITCODE -ne 0) { throw "Could not export $file" }
            [IO.File]::WriteAllText((Join-Path $outputDirectory $file), ($content -join "`n"), [Text.UTF8Encoding]::new($false))
        }
        $report = Get-Content -Raw -LiteralPath (Join-Path $outputDirectory $name) | ConvertFrom-Json
        Write-Host "JSON saved: $(Join-Path $outputDirectory $name)"
        foreach ($summary in $report.summaries) {
            Write-Host "$($summary.scenario): $($summary.successful)/$($summary.attempted) successful; skipped=$($summary.skipped); P50=$($summary.p50Ms) ms; P95=$($summary.p95Ms) ms; max=$($summary.maxMs) ms"
        }
        if ($report.passed -ne $true) { $failed = $true }
    }
    if ($failed -or $instrumentExitCode -ne 0 -or ($lines -join "`n") -notmatch 'OK \(\d+ tests?\)') {
        throw 'UI evaluation failed; exported reports include partial results and the failure stage.'
    }
} finally { Pop-Location }
