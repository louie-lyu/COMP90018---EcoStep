param(
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [ValidateRange(1, 1000)][int]$Samples = 50,
    [ValidateRange(1, 100)][int]$Warmups = 10
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$package = 'com.ecostep.app'
$remoteDirectory = 'files/evaluation'
$outputDirectory = Join-Path $projectRoot 'evaluation_results'

if (-not (Test-Path -LiteralPath $AdbPath)) {
    throw 'adb.exe not found. Supply -AdbPath with its full path.'
}
$deviceOutput = & $AdbPath devices
if ($LASTEXITCODE -ne 0) { throw 'Could not list Android devices.' }
$devices = @($deviceOutput | Where-Object { $_ -match '^\S+\s+device$' })
if ($devices.Count -ne 1) { throw 'Start exactly one emulator or connect one authorized device.' }
$serial = ($devices[0] -split '\s+')[0]

function Get-ReportNames {
    & $AdbPath -s $serial shell run-as $package test -d $remoteDirectory
    if ($LASTEXITCODE -eq 1) { return }
    if ($LASTEXITCODE -ne 0) { throw 'Could not check the device report directory.' }
    $listing = & $AdbPath -s $serial shell run-as $package ls $remoteDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Could not list device reports.' }
    $listing | Where-Object { $_ -match '^recurring-evaluation-\d+\.json$' }
}

Push-Location $projectRoot
try {
    $gitCommit = & git -c "safe.directory=$($projectRoot.Replace('\', '/'))" rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the Git commit for the evaluation report.' }
    $gitStatus = & git -c "safe.directory=$($projectRoot.Replace('\', '/'))" status --porcelain
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the Git working tree status.' }
    $workingTreeDirty = if ($gitStatus) { 'true' } else { 'false' }

    & .\gradlew.bat installDebug installDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'Build or installation failed.' }

    $previousReports = @(Get-ReportNames)
    $testOutput = & $AdbPath -s $serial shell am instrument -w -r `
        -e class com.ecostep.app.evaluation.RecurringJourneyEvaluationTest `
        -e gitCommit $gitCommit -e workingTreeDirty $workingTreeDirty `
        -e samples $Samples -e warmups $Warmups `
        com.ecostep.app.test/androidx.test.runner.AndroidJUnitRunner
    $instrumentExitCode = $LASTEXITCODE
    $testOutput | Write-Host

    # Export failures as evidence before returning a nonzero exit code.
    $newReports = @(Get-ReportNames | Where-Object { $_ -notin $previousReports })
    if ($newReports.Count -eq 0) { throw 'No new recurring journey JSON report was produced. Check the test output.' }
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    $evaluationFailed = $false
    foreach ($name in $newReports) {
        $content = & $AdbPath -s $serial exec-out run-as $package cat "$remoteDirectory/$name"
        if ($LASTEXITCODE -ne 0) { throw "Could not read $name from the device." }
        $json = $content -join "`n"
        $report = $json | ConvertFrom-Json
        $destination = Join-Path $outputDirectory $name
        [System.IO.File]::WriteAllText($destination, $json, [System.Text.UTF8Encoding]::new($false))
        Write-Host "JSON saved: $destination"
        Write-Host "Cases: $($report.totalCases); passed: $($report.passedCases); failed: $($report.failedCases)"
        Write-Host "Synthetic scenarios: false positives=$($report.classification.falsePositives); false negatives=$($report.classification.falseNegatives); unclassified=$($report.classification.unclassifiedCases)"
        foreach ($benchmark in $report.benchmarks) {
            Write-Host "History=$($benchmark.historySize), $($benchmark.distribution): $($benchmark.successfulSamples)/$($benchmark.recordedSamples) successful; skipped=$($benchmark.skippedSamples); P50=$($benchmark.p50Ms) ms; P95=$($benchmark.p95Ms) ms"
        }
        if ($report.passed -ne $true) { $evaluationFailed = $true }
    }

    if ($evaluationFailed -or $instrumentExitCode -ne 0 -or ($testOutput -join "`n") -notmatch 'OK \(\d+ tests?\)') {
        throw 'Recurring journey evaluation did not pass. JSON results were exported for inspection.'
    }
} finally {
    Pop-Location
}
