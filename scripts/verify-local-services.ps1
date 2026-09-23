$ErrorActionPreference = 'Stop'
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if ($dockerCommand) { $dockerExe = $dockerCommand.Source }
else {
    $dockerExe = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
    if (-not (Test-Path -LiteralPath $dockerExe)) { throw 'Docker CLI not found. Reopen your terminal after installing Docker Desktop.' }
}
$projectDirectory = Split-Path $PSScriptRoot -Parent
Push-Location $projectDirectory
try {
    & $dockerExe compose --profile app --profile observability config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Compose configuration is invalid.' }
    & $dockerExe compose exec -T postgres pg_isready -U docflow_dev -d docflow
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL is not ready.' }
    $runningServices = @(& $dockerExe compose --profile app --profile observability ps --status running --services)
    foreach ($service in @('api', 'pdf-worker', 'office-worker', 'postgres', 'azurite', 'prometheus', 'grafana')) {
        if ($runningServices -notcontains $service) { throw "Required service is not running: $service" }
    }
    Write-Output 'PASS: all seven long-running services are running.'
    & $dockerExe compose exec -T office-worker soffice --version
    if ($LASTEXITCODE -ne 0) { throw 'LibreOffice is unavailable in the Office worker.' }
    Write-Output 'PASS: isolated LibreOffice runtime is available.'
    foreach ($endpoint in @('http://127.0.0.1:8080/actuator/health/readiness', 'http://127.0.0.1:3000/api/health')) {
        $result = Invoke-WebRequest -UseBasicParsing -Uri $endpoint -TimeoutSec 15
        if ($result.StatusCode -ne 200) { throw "Health check failed: $endpoint" }
        Write-Output "PASS: $endpoint"
    }
    $targets = Invoke-RestMethod -Uri 'http://127.0.0.1:9090/api/v1/targets' -TimeoutSec 15
    $apiTarget = @($targets.data.activeTargets | Where-Object { $_.labels.job -eq 'docflow-api' })
    if ($apiTarget.Count -ne 1 -or $apiTarget[0].health -ne 'up') {
        throw 'Prometheus API target is not UP yet. Wait for the next scrape and retry.'
    }
    Write-Output 'PASS: Prometheus scrapes the API container.'
    foreach ($port in @(10000, 10001)) {
        if (-not (Test-NetConnection -ComputerName '127.0.0.1' -Port $port -InformationLevel Quiet)) {
            throw "Azurite listener failed: $port"
        }
        Write-Output "PASS: Azurite listener $port"
    }
    Write-Output 'PASS: local foundation service checks. This does not test document processing or authenticated storage operations.'
} finally { Pop-Location }
