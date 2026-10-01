# EcoStep - route proxy test (Cloudflare Worker -> OpenRouteService)
# Usage (from repo root):
#   powershell -ExecutionPolicy Bypass -File scripts\test-route-proxy.ps1 -ProxyUrl https://ecostep-route-proxy.<your-subdomain>.workers.dev
# Signs in with Firebase (email/password) to get an ID token, then checks the proxy contract.

param([Parameter(Mandatory = $true)][string]$ProxyUrl)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$cfg  = Get-Content (Join-Path $root "app\google-services.json") -Raw | ConvertFrom-Json
$key  = $cfg.client[0].api_key[0].current_key
$endpoint = $ProxyUrl.TrimEnd("/") + "/v1/route"

$email = Read-Host "Test account email"
$sec   = Read-Host "Password" -AsSecureString
$pwd_  = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))

$auth = Invoke-RestMethod -Method Post `
    -Uri "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$key" `
    -Headers @{ "X-Android-Package" = "com.ecostep.app" } -ContentType "application/json" `
    -Body (@{ email = $email; password = $pwd_; returnSecureToken = $true } | ConvertTo-Json)
Write-Host "[INFO] signed in, uid = $($auth.localId)"

function Call($name, $headers, $body, [int]$expect) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        $r = Invoke-RestMethod -Method Post -Uri $endpoint -Headers $headers -ContentType "application/json" -Body $body
        $code = 200
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        $r = $_.ErrorDetails.Message
    }
    $sw.Stop()
    $ok = ($code -eq $expect)
    $color = if ($ok) { "Green" } else { "Red" }
    $tag = if ($ok) { "PASS" } else { "FAIL" }
    Write-Host "[$tag] $name -> HTTP $code (expected $expect, $($sw.ElapsedMilliseconds) ms)" -ForegroundColor $color
    return $r
}

$h = @{ Authorization = "Bearer $($auth.idToken)" }
$good = @{
    profile = "foot-walking"
    start   = @{ latitude = -37.7987; longitude = 144.9608 }   # University of Melbourne
    end     = @{ latitude = -37.8183; longitude = 144.9671 }   # Flinders Street Station
} | ConvertTo-Json -Depth 5

$r = Call "1. Valid request with token" $h $good 200
if ($r.routes) {
    $s = $r.routes[0].summary
    Write-Host ("    distance = {0} m, duration = {1} s" -f $s.distance, $s.duration)
}
Call "2. No token (should be 401)" @{} $good 401 | Out-Null
Call "3. Bad profile (should be 400)" $h (@{ profile = "rocket"; start = @{latitude=-37.8;longitude=144.96}; end = @{latitude=-37.81;longitude=144.97} } | ConvertTo-Json -Depth 5) 400 | Out-Null
Call "4. Bad coordinates (should be 400)" $h (@{ profile = "foot-walking"; start = @{latitude=999;longitude=144.96}; end = @{latitude=-37.81;longitude=144.97} } | ConvertTo-Json -Depth 5) 400 | Out-Null
