param(
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [ValidateRange(1, 1000)][int]$Samples = 50,
    [ValidateRange(1, 100)][int]$Warmups = 10,
    [ValidateRange(1, 1000)][int]$BatchSize = 10
)

& (Join-Path $PSScriptRoot 'run-local-evaluation.ps1') `
    -EvaluationName 'weekly-coach' -TestClass 'WeeklyCoachEvaluationTest' @PSBoundParameters
