# scripts/start-all.ps1
# Starts PostgreSQL (port 5433), Spring Boot backend (port 8081), and React frontend (port 8080).

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$pgBin = "C:\Program Files\PostgreSQL\18\bin"
$pgData = Join-Path $projectRoot ".pgdata"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "         Starting ReconcileX             " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. PostgreSQL Check & Start
$pgRunning = $false
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect("127.0.0.1", 5433)
    $tcp.Close()
    $pgRunning = $true
} catch {
    $pgRunning = $false
}

if (-not $pgRunning) {
    Write-Host "[1/3] Starting PostgreSQL on port 5433..." -ForegroundColor Yellow
    if (-not (Test-Path $pgData)) {
        & "$pgBin\initdb.exe" -D $pgData -U reconcilex_app -E UTF8 --locale=C --auth=trust
    }
    Start-Process -FilePath "$pgBin\postgres.exe" -ArgumentList "-D `"$pgData`"" -WindowStyle Hidden
    Start-Sleep -Seconds 3
    Write-Host "  -> PostgreSQL started successfully." -ForegroundColor Green
} else {
    Write-Host "[1/3] PostgreSQL is already running on port 5433." -ForegroundColor Green
}

# 2. Backend Check & Start
$backendRunning = $false
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8081/api/health" -TimeoutSec 2 -ErrorAction SilentlyContinue
    if ($res.status -eq "UP") { $backendRunning = $true }
} catch {
    $backendRunning = $false
}

if (-not $backendRunning) {
    Write-Host "[2/3] Starting Spring Boot Backend on port 8081..." -ForegroundColor Yellow
    Start-Process -FilePath "powershell.exe" -ArgumentList "-ExecutionPolicy Bypass -NoExit -Command `"Set-Location '$projectRoot'; .\scripts\run-local.ps1`""
    Write-Host "  -> Backend process initiated." -ForegroundColor Green
} else {
    Write-Host "[2/3] Backend is already running on port 8081." -ForegroundColor Green
}

# 3. Frontend Check & Start
$frontendRunning = $false
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect("127.0.0.1", 8080)
    $tcp.Close()
    $frontendRunning = $true
} catch {
    $frontendRunning = $false
}

if (-not $frontendRunning) {
    Write-Host "[3/3] Starting React / Vite Frontend Studio on port 8080..." -ForegroundColor Yellow
    $frontendDir = Join-Path $projectRoot "frontend"
    Start-Process -FilePath "powershell.exe" -ArgumentList "-ExecutionPolicy Bypass -NoExit -Command `"Set-Location '$frontendDir'; pnpm run dev`""
    Write-Host "  -> Frontend process initiated." -ForegroundColor Green
} else {
    Write-Host "[3/3] Frontend is already running on port 8080." -ForegroundColor Green
}

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host "ReconcileX is ready!" -ForegroundColor Green
Write-Host "Frontend Studio: http://localhost:8080" -ForegroundColor Cyan
Write-Host "Backend API:     http://localhost:8081" -ForegroundColor Cyan
Write-Host "Swagger UI:      http://localhost:8081/swagger-ui.html" -ForegroundColor Cyan
Write-Host "Credentials:     admin / AdminPass123456!" -ForegroundColor Yellow
Write-Host "==========================================" -ForegroundColor Cyan

