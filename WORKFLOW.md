# ReconcileX — End-to-End Workflow & Execution Lifecycle

This document describes the operational workflows, data state transitions, and execution lifecycles across the **ReconcileX** system.

---

## 1. High-Level Master Workflow

The lifecycle of financial data in ReconcileX follows a 5-stage sequential workflow:

```mermaid
stateDiagram-v2
    [*] --> Unauthenticated
    Unauthenticated --> Authenticated : POST /api/auth/login (JWT in-memory)
    
    state IngestionPhase {
        Authenticated --> SHA256Check : Upload Bank / Internal CSV
        SHA256Check --> DuplicateConflict : File Hash exists in DB
        DuplicateConflict --> ReuseImportId : HTTP 409 Conflict
        SHA256Check --> ValidatedRows : New File
        ValidatedRows --> DBStaging : Batch JDBC Insert
    }

    state ReconciliationPhase {
        ReuseImportId --> CandidatePool : Start Run (bankImportId, internalImportId)
        DBStaging --> CandidatePool
        CandidatePool --> RuleEvaluation : Execute 12 Precedence Rules
        RuleEvaluation --> ResultConsumption : Consume matched candidate IDs
        ResultConsumption --> PersistResults : Batch Insert Results
    }

    state ReportingPhase {
        PersistResults --> FinancialSummary : GET /summary
        PersistResults --> CursorStreaming : GET /report.csv
        CursorStreaming --> DownloadComplete : HTTP Streamed Attachment (fetchSize=500)
    }

    state AuditPhase {
        DBStaging --> AuditLog : Event: IMPORT_COMPLETED
        PersistResults --> AuditLog : Event: RUN_COMPLETED
        DuplicateConflict --> AuditLog : Event: IMPORT_DUPLICATE_REJECTED
    }
```

---

## 2. Stage 1: Authentication & Token Lifecycle Workflow

Every request to ReconcileX (except `/api/health`, `/api/auth/login`, and OpenAPI docs) requires an active JWT bearer token.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as User (ADMIN / VIEWER)
    participant UI as React 19 Frontend
    participant Auth as AuthController
    participant Service as AuthService
    participant Token as JwtTokenService
    participant DB as PostgreSQL (app_users)

    Admin->>UI: Enters username & password in AuthModal
    UI->>Auth: POST /api/auth/login { username, password }
    Auth->>Service: authenticate(request)
    Service->>DB: Query user by username
    
    alt User exists & password matches
        Service->>Token: generateToken(username, role)
        Token-->>Auth: Signed HS256 JWT (exp: 30 min)
        Auth-->>UI: 200 OK { token, expiresIn: 1800, tokenType: "Bearer" }
        UI->>UI: Decode JWT claims (role: ADMIN / VIEWER)
        Note over UI: Store token in React useState memory ONLY (never localStorage)
    else Invalid credentials
        Service->>Service: Execute dummy BCrypt verification (timing attack defense)
        Service-->>UI: 401 Unauthorized "Invalid username or password"
    end
```

---

## 3. Stage 2: Idempotent File Ingestion Workflow

To protect financial ledgers from duplicate uploads or interrupted network transfers, files undergo SHA-256 content hashing before parsing.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Administrator
    participant UI as React Frontend
    participant Controller as FileImportController
    participant Service as FileImportService
    participant Validator as CsvRowValidator
    participant Audit as AuditEventService
    participant DB as PostgreSQL (file_imports)

    Admin->>UI: Selects bank_statement.csv or internal_transactions.csv
    UI->>Controller: POST /api/imports/{bank|internal} (Multipart File)
    Controller->>Service: processImport(file, type)
    
    Service->>Service: Compute SHA-256 digest of byte stream
    Service->>DB: Check if (file_type, file_sha256) already exists

    alt Hash Already Exists (Duplicate File)
        Service->>Audit: recordDuplicateRejected(existingImportId, fileName) [REQUIRES_NEW]
        Service-->>Controller: Return existing Import record
        Controller-->>UI: 409 Conflict { importId: 1, insertedRows: 50, duplicate: true }
        Note over UI: UI displays "Already imported as Import #1" and reuses Import ID
    else Hash Is Unique (New File)
        Service->>Audit: recordImportStarted(importId, fileName)
        Service->>DB: Insert file_imports (Status: IN_PROGRESS)
        loop For each CSV row
            Service->>Validator: Validate 8 required schema headers, date format, and amounts
            alt Valid row
                Service->>Service: Add to JDBC batch insert queue
            else Invalid row
                Service->>DB: Insert into import_row_errors
            end
        end
        Service->>DB: Execute JDBC batch insertion
        Service->>DB: Update file_imports (Status: COMPLETED, totalRows, importedRows)
        Service->>Audit: recordImportCompleted(importId, importedRows, rejectedRows)
        Controller-->>UI: 200 OK { importId: X, totalRows: N, insertedRows: N }
    end
```

---

## 4. Stage 3: Multi-Pass Deterministic Matching Workflow

When an administrator triggers a run with `bankImportId` and `internalImportId`, the engine executes all 12 rules in prioritized numeric order:

```mermaid
flowchart TD
    Start([Start Reconciliation Run]) --> LoadContext[Load Candidate Pool into In-Memory Context]
    
    subgraph Pass1["Pass 1: System Status & Ledger Duplicates"]
        LoadContext --> R10["1. FailedStatusRule (Order 10)<br/>Isolate failed/cancelled internal orders with no bank debit"]
        R10 --> R50["2. InternalDuplicateRule (Order 50)<br/>Detect duplicate payment references in internal ledger"]
    end

    subgraph Pass2["Pass 2: Direct Matches & Settlement Tolerances"]
        R50 --> R100["3. ExactMatchRule (Order 100)<br/>Reference + Amount + Date match exactly in INR"]
        R100 --> R200["4. DateToleranceRule (Order 200)<br/>Reference + Amount match; Settlement date within 1-3 days"]
    end

    subgraph Pass3["Pass 3: Financial Variances & Discrepancies"]
        R200 --> R300["5. AmountMismatchRule (Order 300)<br/>Reference matches; Amount differs (gateway fees)"]
        R300 --> R350["6. BankDuplicateRule (Order 350)<br/>Duplicate bank references detected in statement"]
        R350 --> R400["7. BatchSettlementRule (Order 400)<br/>One bank payout matches sum of internal orders"]
        R400 --> R500["8. RefundRule (Order 500)<br/>Reversals & refunds paired with debit adjustments"]
    end

    subgraph Pass4["Pass 4: Banking Overhead & Unmatched Fallbacks"]
        R500 --> R600["9. ServiceChargeRule (Order 600)<br/>Identify SMS, IMPS, maintenance, and folio fees"]
        R600 --> R900["10. UnexpectedDebitRule (Order 900)<br/>Identify unrecognized dispute/chargeback debits"]
        R900 --> R910["11. MissingInBankRule (Order 910)<br/>Remaining internal transactions missing in bank"]
        R910 --> R920["12. MissingInInternalRule (Order 920)<br/>Remaining bank deposits missing in internal ledger"]
    end

    Pass4 --> BatchPersist[Batch Write Results to reconciliation_results]
    BatchPersist --> AuditRun[Record RUN_COMPLETED in audit_events]
    AuditRun --> End([Run Finished - Status: COMPLETED])
```

### Candidate Consumption Mechanism
To guarantee deterministic execution and prevent duplicate counting:
* Every candidate has a unique tracking identifier.
* In every matching pass:
  ```java
  // When a match is classified:
  context.consumeInternal(matchedInternal.getId());
  context.consumeBank(matchedBank.getId());
  ```
* Subsequent rules are invoked only with the remaining candidate set.

---

## 5. Stage 4: Financial Discrepancy Reporting Workflow

ReconcileX calculates balance sheet discrepancies into clear economic categories:

```mermaid
graph TD
    RunSummary["GET /api/reconciliation/runs/{id}/summary"]
    
    subgraph Balanced["Balanced Category"]
        Matched["Matched Value (INR)<br/>(EXACT_MATCH, AMOUNT_MISMATCH, DATE_MISMATCH, BATCH_SETTLEMENT, REFUND_MATCH)"]
    end

    subgraph InternalExposure["Internal Ledger Exposure"]
        UnmatchedInternal["Unmatched Internal Value (INR)<br/>(MISSING_IN_BANK, REFUND_PENDING_BANK, INTERNAL_DUPLICATE)"]
    end

    subgraph BankExposure["Bank Statement Exposure"]
        UnmatchedCredit["Unmatched Bank Credit Value (INR)<br/>(MISSING_IN_INTERNAL deposits)"]
        UnmatchedDebit["Unmatched Bank Debit Value (INR)<br/>(UNEXPECTED_BANK_DEBIT withdrawals)"]
    end

    RunSummary --> Balanced
    RunSummary --> InternalExposure
    RunSummary --> BankExposure
```

---

## 6. Stage 5: Streaming CSV Export Workflow

To allow exporting 50,000+ row reconciliation reports without running out of JVM heap memory, ReconcileX streams database rows directly over HTTP:

```mermaid
sequenceDiagram
    autonumber
    actor Client as Browser / API Client
    participant Controller as ReconciliationController
    participant Service as ReconciliationReportService
    participant JDBC as PostgreSQL Driver (Cursor)
    participant DB as PostgreSQL Database

    Client->>Controller: GET /api/reconciliation/runs/{id}/report.csv
    Controller->>Service: streamCsvReport(runId)
    
    Service->>JDBC: Open read-only transaction (@Transactional(readOnly = true))
    Service->>JDBC: Statement.setFetchSize(500) (Enables server-side cursor)
    Service->>DB: Execute SELECT query for run results
    
    Controller-->>Client: HTTP 200 OK (Content-Type: text/csv, Transfer-Encoding: chunked)
    
    loop Stream in 500-Row Chunks
        DB-->>JDBC: Fetch next 500 rows
        JDBC-->>Service: Process ResultSet rows
        Service->>Service: Escape CSV formula injection characters (=, +, -, @)
        Service-->>Client: Flush CSV text chunk to HTTP OutputStream
    end

    Service->>JDBC: Close ResultSet and Transaction
    Note over Client: CSV file download complete
```

---

## 7. Stage 6: Immutable Audit Trail Workflow

Compliance and audit requirements mandate that decision logs cannot be tampered with, even by administrators.

1. **Write Isolation (`Propagation.REQUIRES_NEW`):**
   * Critical audit events (`RUN_FAILED`, `IMPORT_FAILED`, `IMPORT_DUPLICATE_REJECTED`) are committed in separate database transactions.
   * If a reconciliation run encounters an error and rolls back, the failure record remains safely committed.
2. **PostgreSQL Trigger Security:**
   * An SQL trigger (`prevent_audit_modification`) prevents any `UPDATE` or `DELETE` statements on the `audit_events` table:
   ```sql
   CREATE TRIGGER trg_prevent_audit_modification
   BEFORE UPDATE OR DELETE ON audit_events
   FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();
   ```
3. **Traceability:**
   * Every audit log includes `runId`, `actor` (username or `system`), `eventType`, `details` (JSONB), and an authoritative server timestamp (`occurred_at`).

### Audit Event Types Recorded

| Event Type | Trigger | Transaction Scope |
| :--- | :--- | :--- |
| `RUN_STARTED` | Reconciliation run begins execution | Outer transaction |
| `RUN_COMPLETED` | Reconciliation run completes successfully | Outer transaction |
| `RUN_FAILED` | Reconciliation run encounters an error | `REQUIRES_NEW` (survives rollback) |
| `IMPORT_STARTED` | File import begins parsing | Outer transaction |
| `IMPORT_COMPLETED` | File import successfully finishes | Outer transaction |
| `IMPORT_FAILED` | File import fails validation | `REQUIRES_NEW` (survives rollback) |
| `IMPORT_DUPLICATE_REJECTED` | SHA-256 duplicate file is rejected (HTTP 409) | `REQUIRES_NEW` (survives rollback) |
| `MATCH_DECISION` | Match decision recorded | Outer transaction |
| `USER_CREATED` | New application user created | Outer transaction |
| `LOGIN_FAILED` | Failed login attempt (username only) | Independent log write |
