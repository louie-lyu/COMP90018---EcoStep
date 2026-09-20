param(
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [switch]$NetworkRecovery
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$package = 'com.ecostep.app'
$remoteDirectory = 'files/evaluation'
$outputDirectory = Join-Path $projectRoot 'evaluation_results'
$testClass = if ($NetworkRecovery) { 'WeatherNetworkRecoveryTest' } else { 'WeatherLatencyEvaluationTest' }
$reportPattern = if ($NetworkRecovery) { '^weather-recovery-\d+\.json$' } else { '^weather-latency-\d+\.json$' }

if (-not (Test-Path -LiteralPath $AdbPath)) {
    throw 'adb.exe not found. Supply -AdbPath with the full path to your Android SDK platform-tools\adb.exe.'
}

$deviceOutput = & $AdbPath devices
if ($LASTEXITCODE -ne 0) { throw 'Could not list Android devices.' }
$devices = @($deviceOutput | Where-Object { $_ -match '^\S+\s+device$' })
if ($devices.Count -ne 1) {
    throw 'Start exactly one emulator (or connect one authorized device) before running this script.'
}
$serial = ($devices[0] -split '\s+')[0]

function Get-ReportNames {
    # test -d returns 1 without stderr when the first run has not created the directory.
    # Suppressing ls stderr alone does not prevent NativeCommandError in Windows PowerShell.
    & $AdbPath -s $serial shell run-as $package test -d $remoteDirectory
    if ($LASTEXITCODE -eq 1) { return }
    if ($LASTEXITCODE -ne 0) { throw 'Could not check the device report directory.' }

    $listing = & $AdbPath -s $serial shell run-as $package ls $remoteDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Could not list reports on the device.' }
    $listing | Where-Object { $_ -match $reportPattern }
}

Push-Location $projectRoot
try {
    # Install without running Gradle's connected-test cleanup, so reports stay available.
    & .\gradlew.bat installDebug installDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'Build or installation failed.' }

    $previousReports = @(Get-ReportNames)
    $testOutput = & $AdbPath -s $serial shell am instrument -w -r `
        -e class "com.ecostep.app.evaluation.$testClass" `
        com.ecostep.app.test/androidx.test.runner.AndroidJUnitRunner
    $instrumentExitCode = $LASTEXITCODE
    $testOutput | Write-Host

    # Export even when request failures cause the test assertion to fail.
    $newReports = @(Get-ReportNames | Where-Object { $_ -notin $previousReports })
    if ($newReports.Count -eq 0) {
        throw 'No new JSON report was produced. Check the instrumentation output above.'
    }
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    foreach ($name in $newReports) {
        $content = & $AdbPath -s $serial exec-out run-as $package cat "$remoteDirectory/$name"
        if ($LASTEXITCODE -ne 0) { throw "Could not read $name from the device." }
        $json = $content -join "`n"
        $null = $json | ConvertFrom-Json
        $destination = Join-Path $outputDirectory $name
        [System.IO.File]::WriteAllText($destination, $json, [System.Text.UTF8Encoding]::new($false))
        Write-Host "JSON saved on computer: $destination"
    }

    # adb can return zero even when AndroidJUnitRunner reports a failed test.
    if ($instrumentExitCode -ne 0 -or ($testOutput -join "`n") -notmatch 'OK \(\d+ tests?\)') {
        throw 'The test did not pass. Its JSON results were exported for inspection.'
    }
} finally {
    Pop-Location
}
