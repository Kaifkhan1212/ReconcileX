# scripts/stop-all.ps1
# Gracefully stops ReconcileX processes (PostgreSQL, Spring Boot / Java, and Vite dev server).

$ErrorActionPreference = "Continue"

Write-Host "Stopping ReconcileX services..." -ForegroundColor Yellow

# Stop frontend (Node / Vite)
Get-Process -Name "node" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match "vite" } | Stop-Process -Force

# Stop Spring Boot (Java)
Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match "reconcilex" } | Stop-Process -Force

# Stop PostgreSQL
$projectRoot = Split-Path -Parent $PSScriptRoot
$pgBin = "C:\Program Files\PostgreSQL\18\bin"
$pgData = Join-Path $projectRoot ".pgdata"
if (Test-Path "$pgBin\pg_ctl.exe") {
    & "$pgBin\pg_ctl.exe" -D $pgData stop -m fast
}

Write-Host "All ReconcileX services stopped." -ForegroundColor Green

