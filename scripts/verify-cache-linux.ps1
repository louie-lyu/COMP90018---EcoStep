param(
    [string]$Distribution = 'Ubuntu',
    [string]$LinuxJavaPath = 'java'
)

# Runs the unchanged compiled cache tests in Linux, where File.renameTo can replace
# an existing file. Requires WSL and Java 21 in the selected distribution.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Convert-ToLinuxPath([string]$Path) {
    $normalized = $Path.Replace('\', '/')
    if ($normalized -match '^([A-Za-z]):/(.*)$') {
        return '/mnt/' + $Matches[1].ToLowerInvariant() + '/' + $Matches[2]
    }
    throw "Expected a local drive path: $Path"
}

Push-Location $projectRoot
try {
    & .\gradlew.bat -I scripts/verify-cache-linux.init.gradle :app:exportCacheTestClasspath
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile and export the cache test classpath.' }
    $classpathFile = Join-Path $projectRoot 'build/cache-verification/classpath.txt'
    $classpath = (Get-Content -LiteralPath $classpathFile | ForEach-Object { Convert-ToLinuxPath $_ }) -join ':'
    $argumentFile = Join-Path $projectRoot 'build/cache-verification/cache-linux.args'
    $arguments = @('-Djava.io.tmpdir=/tmp', '-cp', ('"' + $classpath + '"'),
        'org.junit.runner.JUnitCore',
        'com.ecostep.app.data.cache.weather.DataStoreWeatherCacheTest',
        'com.ecostep.app.data.cache.route.DataStoreRouteCacheTest',
        'com.ecostep.app.data.cache.publictransport.DataStorePublicTransportCacheTest')
    [System.IO.File]::WriteAllLines($argumentFile, $arguments, [System.Text.UTF8Encoding]::new($false))
    & wsl -d $Distribution -- $LinuxJavaPath ('@' + (Convert-ToLinuxPath $argumentFile))
    if ($LASTEXITCODE -ne 0) { throw 'Linux cache tests failed.' }
} finally {
    Pop-Location
}
