$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$variables = @('E2E_URL','DB_URL','DB_USER','DB_PASSWORD','STORAGE_CONNECTION')
$original = @{}
foreach ($name in $variables) { $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
Push-Location $projectDirectory
try {
    $passwordLine = Get-Content -LiteralPath '.env' | Where-Object { $_ -like 'POSTGRES_PASSWORD=*' } | Select-Object -First 1
    if (-not $passwordLine) { throw 'POSTGRES_PASSWORD is missing from .env' }
    $env:E2E_URL = 'http://127.0.0.1:8080'
    $env:DB_URL = 'jdbc:postgresql://127.0.0.1:5433/docflow'
    $env:DB_USER = 'docflow_dev'
    $env:DB_PASSWORD = ($passwordLine -split '=',2)[1]
    # Published Azurite development key, not a cloud credential.
    $env:STORAGE_CONNECTION = 'DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;QueueEndpoint=http://127.0.0.1:10001/devstoreaccount1;'
    $cache = Join-Path $projectDirectory '.m2/repository'
    & mvn.cmd -B -ntp "-Dmaven.repo.local=$cache" -f backend/pom.xml verify
    if ($LASTEXITCODE -ne 0) { throw 'Pipeline tests failed.' }
} finally {
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $original[$name], 'Process') }
    Pop-Location
}
