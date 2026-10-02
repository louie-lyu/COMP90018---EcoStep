param(
    [ValidateRange(1, 20)][int]$SamplesPerCase = 3,
    [ValidateRange(0, 60000)][int]$IntervalMs = 1000,
    [ValidateRange(1000, 120000)][int]$TimeoutMs = 35000,
    [string]$Model,
    [string]$Case,
    [string]$NetworkConditions = 'unspecified',
    [switch]$Smoke
)

$ErrorActionPreference = 'Stop'
if (-not $env:GEMINI_API_KEY) { throw 'Set GEMINI_API_KEY locally before running. Do not commit it or pass it as a script argument.' }
$nodeCommand = Get-Command node -ErrorAction Stop
$arguments = @((Join-Path $PSScriptRoot 'run-gemini-live-evaluation.mjs'),
    '--samples-per-case', $SamplesPerCase, '--interval-ms', $IntervalMs,
    '--timeout-ms', $TimeoutMs, '--network-conditions', $NetworkConditions)
if ($Model) { $arguments += @('--model', $Model) }
if ($Case) { $arguments += @('--case', $Case) }
if ($Smoke) { $arguments += '--smoke' }
& $nodeCommand.Source @arguments
exit $LASTEXITCODE
