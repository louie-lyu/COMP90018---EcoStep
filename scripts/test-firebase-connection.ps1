# EcoStep - Firebase connection test (Auth + Firestore + security rules)
# Usage (from repo root):  powershell -ExecutionPolicy Bypass -File scripts\test-firebase-connection.ps1 [-Keep]
# Reads the API key / project id from app\google-services.json, so no secrets are hard-coded.

param(
    [switch]$Keep
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$cfg  = Get-Content (Join-Path $root "app\google-services.json") -Raw | ConvertFrom-Json
$key  = $cfg.client[0].api_key[0].current_key
$pid_ = $cfg.project_info.project_id
$base = "https://firestore.googleapis.com/v1/projects/$pid_/databases/(default)/documents"

$email = Read-Host "Test account email"
$sec   = Read-Host "Password" -AsSecureString
$pwd_  = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))

function Step($name, [scriptblock]$body, [int]$expectStatus = 200) {
    try {
        $r = & $body
        if ($expectStatus -eq 200) { Write-Host "[PASS] $name" -ForegroundColor Green; return $r }
        Write-Host "[FAIL] $name (expected HTTP $expectStatus, got 200)" -ForegroundColor Red
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq $expectStatus) { Write-Host "[PASS] $name (HTTP $code as expected)" -ForegroundColor Green }
        else {
            Write-Host "[FAIL] $name (HTTP $code)" -ForegroundColor Red
            if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message -ForegroundColor DarkGray }
        }
    }
}

# 1. Sign in with Firebase Auth
$auth = Step "1. Auth sign-in" {
    Invoke-RestMethod -Method Post `
        -Uri "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$key" `
        -Headers @{ "X-Android-Package" = "com.ecostep.app" } `
        -ContentType "application/json" `
        -Body (@{ email = $email; password = $pwd_; returnSecureToken = $true } | ConvertTo-Json)
}
if (-not $auth) { Write-Host "Stop: sign-in failed, check email/password and that Email/Password provider is enabled."; exit 1 }
$uid = $auth.localId
$h   = @{ Authorization = "Bearer $($auth.idToken)" }
Write-Host "    uid = $uid"

$documentId = if ($Keep) { "test_1" } else { "conn_test" }

# 2. Write a JourneySummary-shaped document to users/{uid}/journeys/{documentId}
$now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$doc = @{ fields = @{
    journeyId       = @{ stringValue  = $documentId }
    userId          = @{ stringValue  = $uid }
    startLocation   = @{ mapValue = @{ fields = @{ latitude = @{ doubleValue = -37.7963 }; longitude = @{ doubleValue = 144.9614 } } } }
    endLocation     = @{ mapValue = @{ fields = @{ latitude = @{ doubleValue = -37.8183 }; longitude = @{ doubleValue = 144.9671 } } } }
    startTimeMillis = @{ integerValue = "$($now - 1200000)" }
    endTimeMillis   = @{ integerValue = "$now" }
    distanceMeters  = @{ doubleValue  = 2600 }
    transportMode   = @{ stringValue  = "CAR" }
} } | ConvertTo-Json -Depth 10
$own = "$base/users/$uid/journeys/$documentId"
Step "2. Write own journey (should be allowed)" {
    Invoke-RestMethod -Method Patch -Uri $own -Headers $h -ContentType "application/json" -Body $doc
} | Out-Null

# 3. Read it back
$read = Step "3. Read own journey (should be allowed)" { Invoke-RestMethod -Uri $own -Headers $h }
if ($read) { Write-Host "    transportMode = $($read.fields.transportMode.stringValue), distanceMeters = $($read.fields.distanceMeters.doubleValue)" }

# 4. Security rules: another user's data must be denied
Step "4. Read other user's data (should be denied)" {
    Invoke-RestMethod -Uri "$base/users/someone_else/journeys/x" -Headers $h
} 403

# 5. Security rules: unauthenticated access must be denied
Step "5. Read without login (should be denied)" { Invoke-RestMethod -Uri $own } 403

# 6. Clean up unless -Keep is preparing test_1 for app integration testing
if ($Keep) {
    Write-Host "[SKIP] 6. Keep test journey at users/$uid/journeys/$documentId" -ForegroundColor Yellow
} else {
    Step "6. Delete test journey" { Invoke-RestMethod -Method Delete -Uri $own -Headers $h } | Out-Null
}

Write-Host "`nDone. Auth, Firestore and security rules are working." -ForegroundColor Cyan
