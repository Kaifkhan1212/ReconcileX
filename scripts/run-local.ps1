# run-local.ps1
# Loads KEY=value lines from .env into the current PowerShell session
# (values are never printed to the console) then starts the Spring Boot app.

$envFile = Join-Path $PSScriptRoot "..\.env"

if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*[^#\s][^=]+=.+' } | ForEach-Object {
        $parts = $_ -split '=', 2
        $key = $parts[0].Trim()
        $value = $parts[1].Trim()
        [System.Environment]::SetEnvironmentVariable($key, $value, 'Process')
    }
    Write-Host "Environment loaded from .env" -ForegroundColor Green
} else {
    Write-Warning ".env file not found at $envFile - using application.yml defaults."
}

# PostgreSQL 16 on Linux requires modern IANA timezone (Asia/Kolkata instead of legacy Asia/Calcutta)
if ([System.TimeZoneInfo]::Local.Id -match "India" -or [System.TimeZoneInfo]::Local.DisplayName -match "Kolkata|Calcutta|Chennai|Mumbai") {
    $existing = [System.Environment]::GetEnvironmentVariable("JAVA_TOOL_OPTIONS", "Process")
    if (-not $existing -or $existing -notmatch "user\.timezone") {
        [System.Environment]::SetEnvironmentVariable("JAVA_TOOL_OPTIONS", "$existing -Duser.timezone=Asia/Kolkata".Trim(), "Process")
    }
}

Push-Location (Join-Path $PSScriptRoot "..")
try {
    .\mvnw.cmd spring-boot:run
} finally {
    Pop-Location
}
