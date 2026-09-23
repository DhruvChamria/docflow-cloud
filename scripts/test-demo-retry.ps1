param([switch]$AllowServiceInterruption)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Net.Http
if(-not $AllowServiceInterruption){throw 'Pass -AllowServiceInterruption to recreate the PDF worker for this deterministic demo.'}
$projectDirectory=Split-Path $PSScriptRoot -Parent
$docker=Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
if(-not(Test-Path -LiteralPath $docker)){$docker='docker'}
$original=[Environment]::GetEnvironmentVariable('DEMO_FAIL_FIRST_ATTEMPT','Process')
$client=$null
Push-Location $projectDirectory
try {
    $active=& $docker compose exec -T postgres psql -U docflow_dev -d docflow -tAc "SELECT count(*) FROM merge_jobs WHERE status IN ('CREATED','PROCESSING')"
    if($LASTEXITCODE -ne 0 -or [int]$active -ne 0){throw 'Refusing to restart the worker while uploads or processing jobs are active.'}
    $env:DEMO_FAIL_FIRST_ATTEMPT='true'
    & $docker compose --profile app up -d --force-recreate pdf-worker
    if($LASTEXITCODE -ne 0){throw 'Could not start failure-injection worker.'}
    $cookies=[Net.CookieContainer]::new();$handler=[Net.Http.HttpClientHandler]::new();$handler.CookieContainer=$cookies
    $client=[Net.Http.HttpClient]::new($handler);$client.Timeout=[TimeSpan]::FromSeconds(20)
    $client.DefaultRequestHeaders.Add('X-Requested-With','docflow')
    $form=[Net.Http.MultipartFormDataContent]::new()
    foreach($name in @('document-a.pdf','document-b.pdf')) {
        $path=Join-Path $projectDirectory "backend\pdf-worker\target\demo-inputs\$name"
        $content=[Net.Http.ByteArrayContent]::new([IO.File]::ReadAllBytes($path));$content.Headers.ContentType=[Net.Http.Headers.MediaTypeHeaderValue]::new('application/pdf')
        $form.Add($content,'files',$name)
    }
    $form.Add([Net.Http.StringContent]::new('pdf.merge'),'operation')
    $response=$client.PostAsync('http://127.0.0.1:8080/api/v1/jobs',$form).GetAwaiter().GetResult()
    if([int]$response.StatusCode -ne 202){throw "Submission failed with HTTP $([int]$response.StatusCode)."}
    $job=($response.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json)
    $deadline=[DateTime]::UtcNow.AddSeconds(35)
    do {
        Start-Sleep -Milliseconds 500
        $status=$client.GetStringAsync("http://127.0.0.1:8080/api/v1/jobs/$($job.id)").GetAwaiter().GetResult()|ConvertFrom-Json
        if($status.status -eq 'FAILED'){throw "Injected job failed permanently: $($status.error)"}
    } while($status.status -ne 'COMPLETED' -and [DateTime]::UtcNow -lt $deadline)
    if($status.status -ne 'COMPLETED' -or $status.attempts -ne 2){throw "Expected completion on attempt 2; got $($status.status) attempt $($status.attempts)."}
    "PASS: deterministic first-attempt failure retried and completed on attempt 2 (job $($job.id))."
} finally {
    if($client){$client.Dispose()}
    [Environment]::SetEnvironmentVariable('DEMO_FAIL_FIRST_ATTEMPT',$original,'Process')
    & $docker compose --profile app up -d --force-recreate pdf-worker | Out-Null
    Pop-Location
}
