# ReconcileX — System Architecture Specification

This document provides a detailed architectural breakdown of the **ReconcileX Payment Reconciliation Engine**, detailing the structural layers, database schema, security model, matching pipeline, and memory management characteristics.

---

## 1. Architectural Principles

ReconcileX is engineered around four core tenets:

1. **Determinism:** Given the same set of external bank statements and internal accounting ledgers, the engine guarantees identical reconciliation outcomes regardless of execution time or concurrency.
2. **Idempotency:** Re-submitting identical datasets will never result in duplicate records, fragmented totals, or corrupted accounting ledgers. SHA-256 content hashes guard the ingestion boundary.
3. **Auditability:** Every state transition, execution decision, and administrative event is permanently recorded in an append-only audit trail protected by database triggers.
4. **Memory Efficiency:** Large-scale dataset exports are delivered via PostgreSQL server-side cursor streaming (`fetchSize = 500`) to maintain a bounded JVM memory footprint.

---

## 2. Layered Architecture

```mermaid
flowchart TD
    subgraph PresentationLayer["Presentation Layer (Port 8082 dev / 8080 prod)"]
        SPA["React 19 + TanStack Start"]
        Router["TanStack Router (File-based Routing)"]
        AuthCtx["In-Memory Auth Context (No localStorage)"]
        ViteProxy["Vite Dev Server Proxy (/api -> :8081)"]
        SPA --> Router
        SPA --> AuthCtx
        SPA --> ViteProxy
    end

    subgraph SecurityLayer["Security & Gateway Layer"]
        ViteProxy --> SecFilterChain["Spring Security Filter Chain"]
        SecFilterChain --> JwtAuth["OAuth2 Resource Server (Nimbus HS256)"]
        SecFilterChain --> BCryptGuard["Constant-Time BCrypt Authenticator"]
        SecFilterChain --> RBACFilter["Role-Based Access Control (ADMIN / VIEWER)"]
    end

    subgraph ServiceLayer["Business Logic Layer (Spring Boot 4.1.1 - Port 8081)"]
        RBACFilter --> ImportService["FileImportService (SHA-256 Guard)"]
        RBACFilter --> ReconService["ReconciliationExecutionService"]
        RBACFilter --> ReportService["ReconciliationReportService (Cursor Streaming)"]
        RBACFilter --> AuditService["AuditEventService (REQUIRES_NEW)"]
        RBACFilter --> AdminUserService["AdminUserController (User Provisioning)"]

        subgraph Engine["Deterministic Matching Engine"]
            ReconService --> RuleEngine["ReconciliationRuleEngine"]
            RuleEngine --> Context["ReconciliationContext (Candidate Pool)"]
            RuleEngine --> RuleChain["12 Prioritized Matching Rules"]
        end
    end

    subgraph DataLayer["Persistence Layer (PostgreSQL 16 - Port 5432 native / 5433 Docker)"]
        ImportService --> TblImports[(file_imports)]
        ImportService --> TblBank[(bank_statement_rows)]
        ImportService --> TblInternal[(internal_transactions)]
        ReconService --> TblRuns[(reconciliation_runs)]
        ReconService --> TblResults[(reconciliation_results)]
        AuditService --> TblAudit[(audit_events — Append-Only Trigger)]
        AdminUserService --> TblUsers[(app_users)]
        ReportService -.->|fetchSize=500 Cursor| TblResults
    end
```

---

## 3. Database Schema & Entity Relationship Diagram (ERD)

Database schema migrations are managed via **Flyway (V1 Schema Migration)**.

```mermaid
erDiagram
    FILE_IMPORTS ||--o{ BANK_STATEMENT_ROWS : "contains"
    FILE_IMPORTS ||--o{ INTERNAL_TRANSACTIONS : "contains"
    FILE_IMPORTS ||--o{ IMPORT_ROW_ERRORS : "has errors"
    
    FILE_IMPORTS ||--o{ RECONCILIATION_RUNS : "bank_import"
    FILE_IMPORTS ||--o{ RECONCILIATION_RUNS : "internal_import"
    
    RECONCILIATION_RUNS ||--o{ RECONCILIATION_RESULTS : "produces"

    FILE_IMPORTS {
        bigint id PK
        varchar file_type "BANK_STATEMENT | INTERNAL_TRANSACTIONS"
        varchar file_name
        varchar file_sha256 UK "64-char SHA-256 digest"
        varchar status "PENDING | IN_PROGRESS | COMPLETED | COMPLETED_WITH_ERRORS | FAILED"
        int total_rows
        int imported_rows
        int rejected_rows
        timestamp started_at
        timestamp completed_at
    }

    BANK_STATEMENT_ROWS {
        bigint id PK
        bigint file_import_id FK
        int source_row_number
        date transaction_date
        varchar bank_reference UK
        varchar payment_reference
        varchar settlement_reference
        text description
        numeric credit_amount
        numeric debit_amount
        varchar currency "e.g. INR"
    }

    INTERNAL_TRANSACTIONS {
        bigint id PK
        bigint file_import_id FK
        int source_row_number
        varchar transaction_id UK
        varchar payment_reference
        date transaction_date
        text description
        numeric amount
        varchar currency "e.g. INR"
        varchar payment_status "SUCCESS | PENDING | FAILED | REFUNDED | REFUND_INITIATED"
        varchar settlement_reference
    }

    RECONCILIATION_RUNS {
        bigint id PK
        bigint bank_import_id FK
        bigint internal_import_id FK
        varchar status "PENDING | RUNNING | COMPLETED | FAILED"
        timestamp started_at
        timestamp completed_at
    }

    RECONCILIATION_RESULTS {
        bigint id PK
        bigint run_id FK
        varchar result_type "EXACT_MATCH | AMOUNT_MISMATCH | ..."
        varchar rule_name
        numeric amount_difference
        varchar internal_transaction_ids
        varchar bank_references
        text explanation
        timestamp created_at
    }

    AUDIT_EVENTS {
        bigint id PK
        bigint run_id "nullable"
        varchar event_type "RUN_STARTED | IMPORT_COMPLETED | ..."
        varchar actor "username or system"
        jsonb details
        timestamp occurred_at
    }

    APP_USERS {
        bigint id PK
        varchar username UK
        varchar password_hash "BCrypt hash"
        varchar role "ADMIN | VIEWER"
        boolean enabled
        timestamp created_at
    }
```

### Schema Integrity & Constraints
1. **Idempotency Fingerprint:** `file_imports.file_sha256` is marked `UNIQUE (file_type, file_sha256)`. Duplicate uploads are rejected before row parsing with HTTP 409 Conflict.
2. **Natural Reference Keys:** `bank_statement_rows.bank_reference` and `internal_transactions.transaction_id` enforce database-level uniqueness.
3. **Immutable Audit Trigger:** The `audit_events` table contains a PostgreSQL trigger function (`prevent_audit_modification`) that forbids any `UPDATE` or `DELETE` query, raising an SQL exception.

---

## 4. Rule Engine Subsystem

The rule engine operates on an in-memory `ReconciliationContext`.

### 1. Ingestion into Candidate Pools
When a reconciliation run begins, candidate records from `bank_statement_rows` and `internal_transactions` are loaded into indexed lookup maps:
* `internalByReference`: Map from `paymentReference` to list of internal transactions.
* `bankByReference`: Map from `paymentReference` to list of bank rows.
* `unmatchedInternal`: Set of all active candidate internal transaction IDs.
* `unmatchedBank`: Set of all active candidate bank row IDs.

### 2. Multi-Pass Execution Loop
Each rule implements the `MatchingRule` interface:
```java
public interface MatchingRule extends Ordered {
    List<MatchOutcome> match(ReconciliationContext context);
}
```
Rules evaluate candidate records in strict numeric order (`getOrder()`):

| Order | Rule Class | Result Type | Purpose |
| :---: | :--- | :--- | :--- |
| **10** | `FailedStatusRule` | `IGNORED_FAILED_STATUS` | Isolate failed/cancelled internal orders with no bank debit |
| **50** | `InternalDuplicateRule` | `INTERNAL_DUPLICATE_DETECTED` | Detect duplicate payment references in internal ledger |
| **100** | `ExactMatchRule` | `EXACT_MATCH` | Reference + Amount + Date match exactly in INR |
| **200** | `DateToleranceRule` | `DATE_MISMATCH` | Reference + Amount match; Date within 1–3 days window |
| **300** | `AmountMismatchRule` | `AMOUNT_MISMATCH` | Reference matches; Amount differs (e.g. gateway commission) |
| **350** | `BankDuplicateRule` | `BANK_DUPLICATE_DETECTED` | Duplicate bank references detected in statement |
| **400** | `BatchSettlementRule` | `BATCH_SETTLEMENT` | One aggregate bank payout matches sum of internal orders |
| **500** | `RefundRule` | `REFUND_MATCH` | Reversals & refunds paired with debit records |
| **600** | `ServiceChargeRule` | `BANK_SERVICE_CHARGE` | Automatic detection of SMS, IMPS, folio, and maintenance fees |
| **900** | `UnexpectedDebitRule` | `UNEXPECTED_BANK_DEBIT` | Unrecognized bank account debits or chargebacks |
| **910** | `MissingInBankRule` | `MISSING_IN_BANK` | Internal transactions never credited by banking partner |
| **920** | `MissingInInternalRule` | `MISSING_IN_INTERNAL` | Bank deposits with no internal ledger record |

### 3. Candidate Consumption Mechanism
When a match is found:
1. A `MatchOutcome` record is generated.
2. The participating internal IDs and bank IDs are **removed** from `context.getUnmatchedInternal()` and `context.getUnmatchedBank()`.
3. Subsequent passes only evaluate the remaining unconsumed candidates.

### 4. Batch Persistence
Results are persisted using JDBC batch updates (`rewriteBatchedStatements=true`), minimizing database round-trip latency.

---

## 5. Security & Authentication Architecture

ReconcileX enforces an **authenticated-by-default (Default Deny)** posture.

```mermaid
sequenceDiagram
    autonumber
    actor User as Client (Browser)
    participant Sec as Spring Security
    participant Auth as AuthController
    participant Token as JwtTokenService
    participant DB as AppUserRepository

    User->>Auth: POST /api/auth/login { username, password }
    Auth->>DB: findByUsername(username)
    alt User Found & Enabled
        Auth->>Auth: passwordEncoder.matches(password, hash)
    else User Not Found
        Auth->>Auth: Dummy BCrypt verification (timing attack defense)
    end
    Auth->>Token: generateToken(username, role)
    Token-->>User: HTTP 200 { token, expiresIn: 1800, tokenType: "Bearer" }

    Note over User: Token held in React useState memory ONLY (never localStorage)

    User->>Sec: GET /api/reconciliation/runs (Header: Bearer <token>)
    Sec->>Sec: Validate signature (HS256) & expiry (30 min)
    Sec->>Sec: Check role (ADMIN or VIEWER)
    Sec-->>User: HTTP 200 OK
```

### Security Defenses
* **Timing-Attack Countermeasure:** `AuthService` computes a dummy BCrypt hash when an invalid username is queried, ensuring authentication failures take uniform execution time regardless of whether the username exists.
* **In-Memory JWT Storage:** In the React frontend, JWTs are held exclusively in React state. They are never written to `localStorage` or `sessionStorage`, mitigating Cross-Site Scripting (XSS) credential theft.
* **CSV Formula Injection Shield:** Output cells beginning with `=, +, -, @, \t, \r` are escaped with a leading single quote (`'`) to prevent formula execution in spreadsheet applications.

---

## 6. Performance & Memory Management

### Cursor-Based Streaming Architecture
Generating large CSV reports can easily exhaust JVM heap memory if thousands of entities are loaded into memory simultaneously.

ReconcileX overcomes this using **PostgreSQL server-side cursors**:
1. `ReconciliationReportService` opens a read-only Spring transaction (`@Transactional(readOnly = true)`).
2. The JDBC `Statement` is configured with `fetchSize = 500`.
3. Rows are streamed directly into Spring WebMVC's `StreamingResponseBody`.
4. As chunks are written to the HTTP socket, memory is immediately eligible for garbage collection.

### Numerical Precision Guarantee
Binary floating-point arithmetic (`double`, `float`) is susceptible to IEEE-754 precision issues (e.g. `0.1 + 0.2 != 0.3`).
* Every financial value in ReconcileX is strictly represented as `java.math.BigDecimal`.
* Currencies enforce scale `2` and `RoundingMode.HALF_UP`.
