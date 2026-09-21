param([switch]$AllowServiceInterruptions)
$ErrorActionPreference = 'Stop'
if (-not $AllowServiceInterruptions) { throw 'This test briefly stops local project services. Re-run with -AllowServiceInterruptions when no demo/upload is in progress.' }
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
$dockerExe = if ($dockerCommand) { $dockerCommand.Source } else { Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe' }
function Invoke-Compose([string[]]$Arguments) {
    & $dockerExe compose @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Compose command failed: $($Arguments[0])" }
}
function Get-HttpStatus([string]$Path) {
    try { return (Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:8080$Path" -TimeoutSec 20).StatusCode }
    catch { if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }; throw }
}
function Wait-Ready {
    for ($attempt=0; $attempt -lt 20; $attempt++) {
        if ((Get-HttpStatus '/actuator/health/readiness') -eq 200) { return }
        Start-Sleep -Seconds 1
    }
    throw 'API dependencies did not recover.'
}
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $active = & $dockerExe compose exec -T postgres psql -U docflow_dev -d docflow -Atc "SELECT count(*) FROM merge_jobs WHERE status IN ('CREATED','PROCESSING') AND expires_at>now()"
    if ($LASTEXITCODE -ne 0 -or [int]$active -ne 0) { throw 'An upload/processing job may be active. Retry when the local stack is idle.' }
    Wait-Ready
    foreach ($service in @('postgres','azurite')) {
        try {
            Invoke-Compose -Arguments @('stop',$service)
            if ((Get-HttpStatus '/actuator/health/readiness') -ne 503) { throw "Readiness did not report $service outage." }
            if ((Get-HttpStatus '/actuator/health/liveness') -ne 200) { throw 'Dependency outage must not fail API liveness.' }
            Write-Output "PASS: $service outage -> readiness 503, liveness 200"
        } finally { Invoke-Compose -Arguments @('start',$service) }
        Wait-Ready
        Write-Output "PASS: $service recovery -> readiness 200"
    }
} finally { Pop-Location }
