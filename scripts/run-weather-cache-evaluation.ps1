param(
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string]$NetworkConditions = 'controlled-provider'
)

$ErrorActionPreference = 'Stop'

# Reuse installation, device selection, metadata and report export from the weather runner.
& "$PSScriptRoot\run-weather-evaluation.ps1" -CacheEvaluation `
    -AdbPath $AdbPath -NetworkConditions $NetworkConditions
