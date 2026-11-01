# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Primary operators: **Financial Operations & Treasury Teams** responsible for closing daily payment batches, identifying settlement discrepancies, and reconciling 50,000+ daily internal orders against external bank statements.

Secondary operators: **Compliance & Audit Officers** conducting read-only inspection of immutable audit events, tracking discrepancy resolutions, and downloading streaming CSV reconciliation reports for regulatory filing.

## Product Purpose

ReconcileX provides an automated, deterministic financial transaction reconciliation engine. It exists to replace slow, error-prone manual spreadsheet matching with a high-throughput, 13-pass deterministic matching pipeline that classifies exact matches, tolerances, missing transactions, and fees while maintaining an append-only audit trail.

Success means zero unclassified discrepancies, instantaneous batch reconciliation (<60s for 100k records), zero memory spikes during CSV streaming, and complete operational transparency for finance teams.

## Positioning

Unlike generic ETL scripts or black-box heuristic matching tools, ReconcileX guarantees deterministic, reproducible classification through a strict 13-pass rule precedence hierarchy with SHA-256 idempotency guards and an immutable, trigger-enforced PostgreSQL audit ledger.

## Operating Context

- **Fintech & Payment Gateway Operations:** Teams ingest statements daily from multiple banking partners against internal order ledgers.
- **High Data Volume:** Datasets range from 1,000 to 100,000+ transaction rows per ingestion batch.
- **Two Distinct Permission Tiers:**
  - `ADMIN`: File ingestion, triggering reconciliation runs, user management, and viewing system audit logs.
  - `VIEWER`: Read-only access to run metrics, financial breakdown summaries, and streaming CSV downloads.
- **Workflow Loop:** Upload Bank CSV + Internal CSV -> Trigger Reconciliation Run -> Review Discrepancy Breakdown (Exact Match, Date Tolerance, Amount Mismatch, Service Charges, Missing in Bank/Internal) -> Export Streamed CSV Report.

## Capabilities and Constraints

- **Multi-Pass Deterministic Matching:** 13 prioritized rules (e.g., `ExactMatchRule`, `DateToleranceRule` ±3 days, `AmountMismatchRule`, `ServiceChargeRule`, `MissingInBankRule`, `MissingInInternalRule`).
- **Strict In-Memory JWT Authentication:** Tokens are never persisted in `localStorage` or `sessionStorage` (strict financial compliance requirement).
- **Cursor Streaming:** Low-memory-overhead streaming CSV export with cursor fetch batches.
- **High-Density Data Presentation:** Financial numbers must strictly align (tabular figures), with explicit currency indicators and high-contrast status pills.

## Brand Commitments

- **Name:** ReconcileX
- **Voice:** Precise, mission-critical, unadorned, dependable, and audit-ready.
- **Visual Stance:** Mission-Critical Fintech Workspace — high density, precise tabular alignment, dark/slate-forward palette, unambiguous status badges (Green = Exact Match, Amber = Tolerance/Mismatch, Red = Missing/Unexpected, Slate = Ignored/Failed).

## Evidence on Hand

- **50k-Row Realistic Datasets:** `bank_statement_50k.csv` and `internal_50k.csv`.
- **Core Engine API & Specs:** OpenAPI 3.0 specification at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.
- **Verified Benchmark Suite:** Sub-50s 13-pass matching throughput on 100k rows; 500ms streaming export.

## Product Principles

1. **Determinism Over Guesswork:** Every classification must be traceable to a specific, explainable matching rule; transactions are never heuristically guessed.
2. **Tabular Clarity & High Density:** Financial operators need maximum data density with zero visual noise, perfect decimal alignment, and immediate visual hierarchy.
3. **Audit Immutability:** Financial reconciliation is legal and regulatory evidence; audit trails cannot be altered or deleted.
4. **Safety by Default:** Security tokens stay strictly in-memory, sensitive operations require ADMIN verification, and idempotency guards prevent double-processing.

## Accessibility & Inclusion

- Compliance with WCAG 2.1 AA standards for tabular data and financial forms.
- High-contrast ratio (>4.5:1) for all status tags, discrepancy badges, and table rows.
- Full keyboard navigability across tabular row selection, modal dialogs, and pagination controls.

