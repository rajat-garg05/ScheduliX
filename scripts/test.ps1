$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $root "backend"
$frontendDir = Join-Path $root "frontend"

Write-Host "Running backend tests (requires Docker Desktop)..."
Push-Location $backendDir
try {
    mvn test
    if ($LASTEXITCODE -ne 0) {
        throw "Backend tests failed."
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Running frontend tests..."
Push-Location $frontendDir
try {
    npm test
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend tests failed."
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "All backend and frontend tests passed."