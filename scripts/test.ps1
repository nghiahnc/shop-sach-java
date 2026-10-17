$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
Push-Location $taskRoot
try {
    if (-not $env:JAVA_HOME) {
        $taskJdk = Get-ChildItem .tools -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($taskJdk) { $env:JAVA_HOME = $taskJdk.FullName }
    }
    $env:TRAM_SACH_TEST = 'isolated'
    & ./mvnw.cmd -B verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed' }
} finally { Remove-Item Env:TRAM_SACH_TEST -ErrorAction SilentlyContinue; Pop-Location }
