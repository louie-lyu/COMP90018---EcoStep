param(
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$DatasetPath = '',
    [ValidateRange(1, 10000)][int]$SamplesPerMode = 10000,
    [ValidateRange(0, 2147483647)][int]$Seed = 42,
    # Optional motion variant: supply both to compare speed-only and speed + accelerometer rules.
    [double]$MotionSteadyAccelStd = 0,
    [ValidateRange(0, 2147483647)][int]$MotionMinAccelSamples = 0
)

$ErrorActionPreference = 'Stop'
$motionArguments = @()
if ($MotionSteadyAccelStd -gt 0 -and $MotionMinAccelSamples -gt 0) {
    $motionArguments = @(
        '-e', 'motionSteadyAccelStd', $MotionSteadyAccelStd.ToString([System.Globalization.CultureInfo]::InvariantCulture),
        '-e', 'motionMinAccelSamples', $MotionMinAccelSamples
    )
} elseif ($MotionSteadyAccelStd -ne 0 -or $MotionMinAccelSamples -ne 0) {
    throw 'Supply both -MotionSteadyAccelStd and -MotionMinAccelSamples with positive values, or neither.'
}
$projectRoot = Split-Path -Parent $PSScriptRoot
$package = 'com.ecostep.app'
$remoteDirectory = 'files/evaluation'
$datasetDirectory = "/sdcard/Android/data/$package/files/evaluation"
$outputDirectory = Join-Path $projectRoot 'evaluation_results'
if (-not $DatasetPath) {
    $DatasetPath = Join-Path $projectRoot 'app\src\androidTest\java\com\ecostep\app\evaluation\data\SHL-preview-evaluation.zip'
}

if (-not (Test-Path -LiteralPath $DatasetPath -PathType Leaf)) {
    throw 'SHL preview ZIP not found. Download the evaluation archive into evaluation/data or supply -DatasetPath.'
}
$DatasetPath = (Resolve-Path -LiteralPath $DatasetPath).ProviderPath
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'adb.exe not found. Supply -AdbPath with its full path.' }
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
    $listing | Where-Object { $_ -match '^activity-hint-transport-evaluation-\d+\.json$' }
}

Push-Location $projectRoot
try {
    $gitCommit = & git -c "safe.directory=$($projectRoot.Replace('\', '/'))" rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the Git commit for the evaluation report.' }
    $gitStatus = & git -c "safe.directory=$($projectRoot.Replace('\', '/'))" status --porcelain
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the Git working tree status.' }
    $workingTreeDirty = if ($gitStatus) { 'true' } else { 'false' }
    $datasetSha256 = (Get-FileHash -LiteralPath $DatasetPath -Algorithm SHA256).Hash.ToLowerInvariant()

    & .\gradlew.bat installDebug installDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'Build or installation failed.' }
    & $AdbPath -s $serial shell mkdir -p $datasetDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the device dataset directory.' }
    & $AdbPath -s $serial push $DatasetPath "$datasetDirectory/shl-preview.zip"
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy the SHL preview ZIP to the device.' }

    Write-Host 'Live evaluation: sends SHL endpoints to Transitous, omits query time, and uses current timetables.'
    $previousReports = @(Get-ReportNames)
    $testOutput = & $AdbPath -s $serial shell am instrument -w -r `
        -e class com.ecostep.app.evaluation.ActivityHintTransportEvaluationTest `
        -e gitCommit $gitCommit -e workingTreeDirty $workingTreeDirty `
        -e live true -e samplesPerMode $SamplesPerMode -e seed $Seed @motionArguments `
        com.ecostep.app.test/androidx.test.runner.AndroidJUnitRunner
    $instrumentExitCode = $LASTEXITCODE
    $testOutput | Write-Host

    # Export results even when execution fails. Low accuracy is a result, not a runner failure.
    $newReports = @(Get-ReportNames | Where-Object { $_ -notin $previousReports })
    if ($newReports.Count -eq 0) { throw 'No new activity hint transport JSON report was produced. Check the test output.' }
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
        Write-Host "Samples: $($report.totalSamples)/$($report.plannedSamples); correct: $($report.summary.correctSamples); runtime errors: $($report.summary.runtimeErrors)"
        Write-Host "Online current-timetable accuracy=$($report.summary.accuracy); UNKNOWN rate=$($report.summary.unknownRate); coverage=$($report.summary.coverage); macro F1=$($report.summary.macroF1)"
        Write-Host "Classification P50=$($report.summary.p50Ms) ms; P95=$($report.summary.p95Ms) ms"
        Write-Host "Planner statuses: $($report.plannerStatusCounts | ConvertTo-Json -Compress)"
        Write-Host "With transit candidates=$($report.withTransitCandidates); timeouts=$($report.timeouts)"
        Write-Host "Successful online subset accuracy=$($report.successfulOnlineSummary.accuracy) (n=$($report.successfulOnlineSummary.totalSamples))"
        foreach ($mode in $report.perMode) {
            Write-Host "$($mode.mode): n=$($mode.totalSamples); precision=$($mode.precision); recall=$($mode.recall); F1=$($mode.f1); UNKNOWN rate=$($mode.unknownRate)"
        }
        Write-Host "Motion replay: $($report.dataset.motionReplay | ConvertTo-Json -Compress)"
        Write-Host "Sensor-fallback subset (no Activity hint >= 60): accuracy=$($report.sensorFallbackSummary.accuracy) (n=$($report.sensorFallbackSummary.totalSamples))"
        if ($report.motionVariant) {
            Write-Host "Motion variant $($report.motionVariantThresholds | ConvertTo-Json -Compress): accuracy=$($report.motionVariant.summary.accuracy); macro F1=$($report.motionVariant.summary.macroF1); UNKNOWN rate=$($report.motionVariant.summary.unknownRate)"
            Write-Host "Motion variant sensor-fallback subset: accuracy=$($report.motionVariant.sensorFallbackSummary.accuracy)"
            foreach ($mode in $report.motionVariant.perMode) {
                Write-Host "  $($mode.mode): precision=$($mode.precision); recall=$($mode.recall); F1=$($mode.f1); UNKNOWN rate=$($mode.unknownRate)"
            }
        }
        if ($report.passed -ne $true -or $report.datasetSha256 -ne $datasetSha256 -or $report.schemaVersion -ne 5 -or $report.timetableTime -ne "current") { $evaluationFailed = $true }
    }
    if ($evaluationFailed -or $instrumentExitCode -ne 0 -or ($testOutput -join "`n") -notmatch 'OK \(\d+ tests?\)') {
        throw 'Activity hint transport evaluation did not complete or a dataset hash/schema differs. JSON results were exported for inspection.'
    }
} finally {
    Pop-Location
}
