$taskRoot = Split-Path $PSScriptRoot -Parent
$pidPath = Join-Path $taskRoot '.local/server.pid'
if (Test-Path -LiteralPath $pidPath) {
    $serverPid = [int](Get-Content -LiteralPath $pidPath)
    $serverProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$serverPid" -ErrorAction SilentlyContinue
    if ($serverProcess -and $serverProcess.CommandLine -like '*tram-sach-1.0.0.jar*') { Stop-Process -Id $serverPid }
}
