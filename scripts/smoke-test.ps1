# scripts/smoke-test.ps1
# End-to-end smoke test runner for ReconcileX Payment Reconciliation Engine.
# Verifies authentication, idempotent ingestion, multi-pass reconciliation,
# CSV streaming report, role-based access control (RBAC), and security defaults.

[CmdletBinding()]
param(
    [string]$BaseUrl = "",
    [string]$AdminUsername = "",
    [System.Security.SecureString]$AdminPassword = $null
)

$ErrorActionPreference = "Stop"

function Write-Pass {
    param([string]$Message)
    Write-Host "[PASS] " -ForegroundColor Green -NoNewline
    Write-Host $Message
}

function Write-Fail {
    param([string]$Message)
    Write-Host "[FAIL] " -ForegroundColor Red -NoNewline
    Write-Host $Message
}

function Write-Step {
    param([string]$Message)
    Write-Host "`n--- $Message ---" -ForegroundColor Cyan
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir

# -----------------------------------------------------------------------------
# STEP 1: Prompt for connection parameters and credentials
# -----------------------------------------------------------------------------
Write-Step "Step 1: Configuration & Credentials"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $inputUrl = Read-Host "Enter base URL [http://localhost:8081]"
    $BaseUrl = if ([string]::IsNullOrWhiteSpace($inputUrl)) { "http://localhost:8081" } else { $inputUrl }
}
$BaseUrl = $BaseUrl.TrimEnd('/')

if ([string]::IsNullOrWhiteSpace($AdminUsername)) {
    $inputUser = Read-Host "Enter admin username [admin]"
    $AdminUsername = if ([string]::IsNullOrWhiteSpace($inputUser)) { "admin" } else { $inputUser }
}

if ($null -eq $AdminPassword) {
    $AdminPassword = Read-Host -Prompt "Enter admin password" -AsSecureString
}

# Unpack secure string in memory strictly for HTTP payload transmission (never logged/printed)
$bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($AdminPassword)
$plainAdminPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

if ([string]::IsNullOrWhiteSpace($plainAdminPassword)) {
    Write-Fail "Admin password cannot be blank."
    exit 1
}

Write-Pass "Step 1: Target configured ($BaseUrl) and credentials acquired securely."

# -----------------------------------------------------------------------------
# STEP 2: POST /api/auth/login (Admin Token)
# -----------------------------------------------------------------------------
Write-Step "Step 2: Admin Authentication (POST /api/auth/login)"

$adminToken = $null
try {
    $loginPayload = @{
        username = $AdminUsername
        password = $plainAdminPassword
    } | ConvertTo-Json

    $loginResponse = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" `
        -Method Post `
        -ContentType "application/json" `
        -Body $loginPayload

    if ($loginResponse.token) {
        $adminToken = $loginResponse.token
        Write-Pass "Step 2: Successfully authenticated as '$AdminUsername' (JWT token received)."
    } else {
        Write-Fail "Step 2: Login succeeded but no token was returned."
        exit 1
    }
} catch {
    Write-Fail "Step 2: Login failed. $($_.Exception.Message)"
    exit 1
}

# -----------------------------------------------------------------------------
# STEP 3: Ingest Bank Statement & Internal Ledger (POST /api/imports/*)
# -----------------------------------------------------------------------------
Write-Step "Step 3: Ingestion (POST /api/imports/bank & /api/imports/internal)"

# Resolve sample CSV files
$bankCandidates = @(
    (Join-Path $projectRoot "src\test\resources\sample-data\bank_statement.csv"),
    (Join-Path $projectRoot "sample-data\bank_statement.csv"),
    (Join-Path $scriptDir "sample-data\bank_statement.csv")
)
$bankCsv = $bankCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1

$internalCandidates = @(
    (Join-Path $projectRoot "src\test\resources\sample-data\internal_transactions.csv"),
    (Join-Path $projectRoot "sample-data\internal_transactions.csv"),
    (Join-Path $scriptDir "sample-data\internal_transactions.csv")
)
$internalCsv = $internalCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $bankCsv -or -not (Test-Path $bankCsv)) {
    Write-Fail "Step 3: bank_statement.csv not found in expected locations."
    exit 1
}
if (-not $internalCsv -or -not (Test-Path $internalCsv)) {
    Write-Fail "Step 3: internal_transactions.csv not found in expected locations."
    exit 1
}

function Invoke-UploadCsv {
    param(
        [string]$Url,
        [string]$FilePath,
        [string]$Token
    )
    $raw = curl.exe -s -w "`n%{http_code}" -X POST $Url `
        -H "Authorization: Bearer $Token" `
        -F "file=@$FilePath"
    $lines = $raw -split "`r?`n" | Where-Object { $_ -ne "" }
    $code = [int]$lines[-1]
    $body = ($lines[0..($lines.Count - 2)]) -join "`n"
    return [PSCustomObject]@{
        StatusCode = $code
        Body = $body
    }
}

$bankImportId = $null
$bankUpload = Invoke-UploadCsv -Url "$BaseUrl/api/imports/bank" -FilePath $bankCsv -Token $adminToken
if ($bankUpload.StatusCode -in @(200, 201, 409)) {
    $json = $bankUpload.Body | ConvertFrom-Json
    $bankImportId = $json.importId
    $statusText = if ($bankUpload.StatusCode -eq 409) { "already imported (idempotent 409)" } else { "imported (200 OK)" }
    Write-Pass "Bank statement: $statusText -> Import ID: $bankImportId"
} else {
    Write-Fail "Bank statement upload failed with HTTP $($bankUpload.StatusCode): $($bankUpload.Body)"
    exit 1
}

$internalImportId = $null
$internalUpload = Invoke-UploadCsv -Url "$BaseUrl/api/imports/internal" -FilePath $internalCsv -Token $adminToken
if ($internalUpload.StatusCode -in @(200, 201, 409)) {
    $json = $internalUpload.Body | ConvertFrom-Json
    $internalImportId = $json.importId
    $statusText = if ($internalUpload.StatusCode -eq 409) { "already imported (idempotent 409)" } else { "imported (200 OK)" }
    Write-Pass "Internal transactions: $statusText -> Import ID: $internalImportId"
} else {
    Write-Fail "Internal transactions upload failed with HTTP $($internalUpload.StatusCode): $($internalUpload.Body)"
    exit 1
}

Write-Pass "Step 3: Data files ingested/verified successfully."

# -----------------------------------------------------------------------------
# STEP 4: Trigger Reconciliation Run (POST /api/reconciliation/runs)
# -----------------------------------------------------------------------------
Write-Step "Step 4: Execute Reconciliation (POST /api/reconciliation/runs)"

$runId = $null
try {
    $runPayload = @{
        bankImportId = $bankImportId
        internalImportId = $internalImportId
    } | ConvertTo-Json

    $runResponse = Invoke-RestMethod -Uri "$BaseUrl/api/reconciliation/runs" `
        -Method Post `
        -Headers @{ "Authorization" = "Bearer $adminToken" } `
        -ContentType "application/json" `
        -Body $runPayload

    $runId = $runResponse.runId
    $resultCounts = $runResponse.resultCounts

    # Calculate total results and count of distinct result types
    $totalResults = 0
    $distinctTypeCount = 0
    if ($resultCounts -is [System.Management.Automation.PSCustomObject]) {
        $props = @($resultCounts.PSObject.Properties)
        $distinctTypeCount = $props.Count
        foreach ($prop in $props) {
            $totalResults += [long]$prop.Value
        }
    }

    if ($totalResults -eq 56 -and $distinctTypeCount -eq 13) {
        Write-Pass "Step 4: Run $runId completed with exactly $totalResults results and $distinctTypeCount result types."
    } else {
        Write-Fail "Step 4: Run $runId returned $totalResults results (expected 56) and $distinctTypeCount result types (expected 13)."
        exit 1
    }
} catch {
    Write-Fail "Step 4: Reconciliation run execution failed: $($_.Exception.Message)"
    exit 1
}

# -----------------------------------------------------------------------------
# STEP 5: Run Summary & Streaming CSV (GET summary & report.csv)
# -----------------------------------------------------------------------------
Write-Step "Step 5: Run Summary & CSV Streaming (GET summary & report.csv)"

try {
    $summary = Invoke-RestMethod -Uri "$BaseUrl/api/reconciliation/runs/$runId/summary" `
        -Method Get `
        -Headers @{ "Authorization" = "Bearer $adminToken" }

    if ($summary.runId -eq $runId -and $summary.status -eq "COMPLETED") {
        Write-Pass "Run summary verified (Run ID: $($summary.runId), Status: $($summary.status), Matched Value: $($summary.matchedValue))."
    } else {
        Write-Fail "Run summary did not match expected run state: $($summary | ConvertTo-Json -Compress)"
        exit 1
    }

    $csvContent = Invoke-RestMethod -Uri "$BaseUrl/api/reconciliation/runs/$runId/report.csv" `
        -Method Get `
        -Headers @{ "Authorization" = "Bearer $adminToken" }

    $csvLines = ($csvContent -split "`r?`n") | Where-Object { $_ -ne "" }
    $dataRowCount = [Math]::Max(0, $csvLines.Count - 1)

    if ($dataRowCount -eq 37) {
        Write-Pass "Step 5: report.csv downloaded successfully with exactly 37 mismatch data rows (header + 37 data rows)."
    } else {
        Write-Fail "Step 5: report.csv returned $dataRowCount data rows (expected 37)."
        exit 1
    }
} catch {
    Write-Fail "Step 5: Summary or CSV retrieval failed: $($_.Exception.Message)"
    exit 1
}

# -----------------------------------------------------------------------------
# STEP 6: RBAC Verification with temporary VIEWER user
# -----------------------------------------------------------------------------
Write-Step "Step 6: Role-Based Access Control (VIEWER Role Verification)"

$tempViewerUsername = "smoke_viewer_" + (Get-Date -Format "yyyyMMddHHmmss")
$tempViewerPassword = "SmokeViewerPass123!"

try {
    $createUserPayload = @{
        username = $tempViewerUsername
        password = $tempViewerPassword
        role = "VIEWER"
    } | ConvertTo-Json

    $createUserResponse = Invoke-RestMethod -Uri "$BaseUrl/api/admin/users" `
        -Method Post `
        -Headers @{ "Authorization" = "Bearer $adminToken" } `
        -ContentType "application/json" `
        -Body $createUserPayload

    Write-Pass "Created temporary VIEWER user '$tempViewerUsername'."
} catch {
    Write-Fail "Failed to create temporary VIEWER user: $($_.Exception.Message)"
    exit 1
}

$viewerToken = $null
try {
    $viewerLoginPayload = @{
        username = $tempViewerUsername
        password = $tempViewerPassword
    } | ConvertTo-Json

    $viewerLoginResponse = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" `
        -Method Post `
        -ContentType "application/json" `
        -Body $viewerLoginPayload

    $viewerToken = $viewerLoginResponse.token
    Write-Pass "Authenticated as temporary VIEWER user."
} catch {
    Write-Fail "Failed to log in as temporary VIEWER: $($_.Exception.Message)"
    exit 1
}

# 6a. Expect 403 on POST /api/reconciliation/runs
$viewerRunForbidden = $false
try {
    $forbiddenRunPayload = @{
        bankImportId = $bankImportId
        internalImportId = $internalImportId
    } | ConvertTo-Json

    Invoke-RestMethod -Uri "$BaseUrl/api/reconciliation/runs" `
        -Method Post `
        -Headers @{ "Authorization" = "Bearer $viewerToken" } `
        -ContentType "application/json" `
        -Body $forbiddenRunPayload
} catch {
    if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 403) {
        $viewerRunForbidden = $true
    }
}

if ($viewerRunForbidden) {
    Write-Pass "VIEWER role properly denied on POST /api/reconciliation/runs (HTTP 403 Forbidden)."
} else {
    Write-Fail "Expected HTTP 403 Forbidden on POST /api/reconciliation/runs with VIEWER token."
    exit 1
}

# 6b. Expect 200 on GET /api/reconciliation/runs/{id}/summary
$viewerSummaryOk = $false
try {
    $vSummary = Invoke-RestMethod -Uri "$BaseUrl/api/reconciliation/runs/$runId/summary" `
        -Method Get `
        -Headers @{ "Authorization" = "Bearer $viewerToken" }

    if ($vSummary.runId -eq $runId) {
        $viewerSummaryOk = $true
    }
} catch {
    $viewerSummaryOk = $false
}

if ($viewerSummaryOk) {
    Write-Pass "VIEWER role properly allowed on GET /api/reconciliation/runs/$runId/summary (HTTP 200 OK)."
} else {
    Write-Fail "Expected HTTP 200 OK on GET /api/reconciliation/runs/$runId/summary with VIEWER token."
    exit 1
}

Write-Pass "Step 6: RBAC authorization constraints verified."

# -----------------------------------------------------------------------------
# STEP 7: Protected Route without Token must return 401
# -----------------------------------------------------------------------------
Write-Step "Step 7: Unauthenticated Default-Deny Verification (HTTP 401)"

$unauthenticatedRejected = $false
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/reconciliation/runs" -Method Get
} catch {
    if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) {
        $unauthenticatedRejected = $true
    }
}

if ($unauthenticatedRejected) {
    Write-Pass "Step 7: Unauthenticated request to protected route returned HTTP 401 Unauthorized."
} else {
    Write-Fail "Step 7: Unauthenticated request did not return HTTP 401 Unauthorized."
    exit 1
}

# -----------------------------------------------------------------------------
# FINAL SUMMARY
# -----------------------------------------------------------------------------
Write-Host "`n===========================================================" -ForegroundColor Green
Write-Host " ALL SMOKE TESTS PASSED (7/7 Steps Verified Successfully) " -ForegroundColor Green
Write-Host "===========================================================`n" -ForegroundColor Green
