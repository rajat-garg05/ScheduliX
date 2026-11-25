$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $root "backend"
$frontendDir = Join-Path $root "frontend"

Write-Host "Starting PostgreSQL via Docker Compose..."
Push-Location $root
try {
    docker compose up -d
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed. Make sure Docker Desktop is running."
    }
} finally {
    Pop-Location
}

Write-Host "Starting backend in a new window..."
Start-Process powershell -WorkingDirectory $backendDir -ArgumentList "-NoExit", "-Command", "mvn spring-boot:run"

Write-Host "Starting frontend in a new window..."
Start-Process powershell -WorkingDirectory $frontendDir -ArgumentList "-NoExit", "-Command", "npm run dev"

Write-Host ""
Write-Host "ScheduliX is starting up:"
Write-Host "  Frontend : http://localhost:5173"
Write-Host "  Backend  : http://localhost:8080/api/health"
Write-Host "Close the backend/frontend windows (or press Ctrl+C inside them) to stop."