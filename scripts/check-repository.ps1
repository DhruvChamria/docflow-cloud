$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

$requiredFiles = @(
    'README.md',
    'docs/ARCHITECTURE.md',
    'docs/TESTING.md',
    'docs/DEMO.md',
    '.editorconfig',
    '.gitignore',
    '.env.example',
    'compose.yaml',
    'backend/pom.xml',
    'frontend/package.json'
)

foreach ($relativePath in $requiredFiles) {
    $path = Join-Path $projectRoot $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing file: $relativePath"
    }
    if ((Get-Item -LiteralPath $path).Length -eq 0) {
        throw "Empty file: $relativePath"
    }
}

foreach ($sensitivePath in @('.env', '.env.local', '*.tfstate', '*.tfvars')) {
    & git -C $projectRoot check-ignore -q -- $sensitivePath
    if ($LASTEXITCODE -ne 0) {
        throw "Missing ignore protection: $sensitivePath"
    }
}

& git -C $projectRoot check-ignore -q -- '.env.example'
if ($LASTEXITCODE -eq 0) {
    throw '.env.example must remain trackable'
}

Write-Output 'PASS: required project files and ignore rules are present.'
