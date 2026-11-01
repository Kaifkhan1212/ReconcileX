/**
 * Single source of truth for matching rules, ledger schemas, and audit event types.
 * Strictly mirrors the backend engine implementation and README rule hierarchy.
 */

export const BANK_REQUIRED_HEADERS = [
  "transaction_date",
  "bank_reference",
  "payment_reference",
  "settlement_reference",
  "description",
  "credit_amount",
  "debit_amount",
  "currency",
] as const;

export const INTERNAL_REQUIRED_HEADERS = [
  "transaction_id",
  "payment_reference",
  "transaction_date",
  "description",
  "amount",
  "currency",
  "payment_status",
  "settlement_reference",
] as const;

export interface MatchingRuleDefinition {
  order: number;
  ruleClass: string;
  resultType: string;
  label: string;
  description: string;
  category: "exact" | "tolerance" | "exception" | "system";
}

export const MATCHING_RULES: readonly MatchingRuleDefinition[] = [
  {
    order: 10,
    ruleClass: "FailedStatusRule",
    resultType: "IGNORED_FAILED_STATUS",
    label: "Failed or Cancelled Status",
    description: "Internal ledger rows with FAILED or CANCELLED status with no bank debit are excluded from discrepancy alarms.",
    category: "system",
  },
  {
    order: 50,
    ruleClass: "InternalDuplicateRule",
    resultType: "INTERNAL_DUPLICATE_DETECTED",
    label: "Internal Duplicate Detection",
    description: "Multiple internal ledger records sharing the exact same payment reference.",
    category: "exception",
  },
  {
    order: 100,
    ruleClass: "ExactMatchRule",
    resultType: "EXACT_MATCH",
    label: "Exact 1-to-1 Match",
    description: "Identical payment reference, exact transaction amount, and matching transaction date in INR.",
    category: "exact",
  },
  {
    order: 200,
    ruleClass: "DateToleranceRule",
    resultType: "DATE_MISMATCH",
    label: "Date Window Settlement",
    description: "Identical reference and amount, but bank settlement date differs by 1–3 days (configurable window).",
    category: "tolerance",
  },
  {
    order: 300,
    ruleClass: "AmountMismatchRule",
    resultType: "AMOUNT_MISMATCH",
    label: "Amount Variance / Commission",
    description: "Matching payment reference and date, but amount differs (e.g., payment gateway commission deductions).",
    category: "tolerance",
  },
  {
    order: 350,
    ruleClass: "BankDuplicateRule",
    resultType: "BANK_DUPLICATE_DETECTED",
    label: "Bank Duplicate Statement Entry",
    description: "Duplicate bank statement entries sharing the same bank reference identifier.",
    category: "exception",
  },
  {
    order: 400,
    ruleClass: "BatchSettlementRule",
    resultType: "BATCH_SETTLEMENT",
    label: "Batch Settlement (N-to-1)",
    description: "One aggregate bank credit payout matching multiple internal ledger transactions sharing settlement reference.",
    category: "tolerance",
  },
  {
    order: 500,
    ruleClass: "RefundRule",
    resultType: "REFUND_MATCH",
    label: "Refund / Reversal Pairing",
    description: "Reversal or refund transactions matched against corresponding credit or debit adjustments.",
    category: "tolerance",
  },
  {
    order: 600,
    ruleClass: "ServiceChargeRule",
    resultType: "BANK_SERVICE_CHARGE",
    label: "Bank Service Charge",
    description: "Automatic identification of bank maintenance fees, IMPS charges, SMS alerts, and ledger folio costs.",
    category: "system",
  },
  {
    order: 900,
    ruleClass: "UnexpectedDebitRule",
    resultType: "UNEXPECTED_BANK_DEBIT",
    label: "Unexpected Bank Debit",
    description: "Unrecognized bank account debits, disputes, or chargebacks with no internal record.",
    category: "exception",
  },
  {
    order: 910,
    ruleClass: "MissingInBankRule",
    resultType: "MISSING_IN_BANK",
    label: "Missing in Bank Statement",
    description: "Internal ledger order recorded and marked successful, but never received or credited by the banking partner.",
    category: "exception",
  },
  {
    order: 920,
    ruleClass: "MissingInInternalRule",
    resultType: "MISSING_IN_INTERNAL",
    label: "Missing in Internal Ledger",
    description: "Bank statement deposit or credit with no corresponding internal order or invoice record.",
    category: "exception",
  },
] as const;

export const AUDIT_EVENT_TYPES = [
  "RUN_STARTED",
  "RUN_COMPLETED",
  "RUN_FAILED",
  "IMPORT_STARTED",
  "IMPORT_COMPLETED",
  "IMPORT_FAILED",
  "IMPORT_DUPLICATE_REJECTED",
  "MATCH_DECISION",
  "USER_CREATED",
  "LOGIN_FAILED",
] as const;

export type AuditEventType = (typeof AUDIT_EVENT_TYPES)[number];

