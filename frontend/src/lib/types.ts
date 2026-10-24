export type AppUserRole = "ADMIN" | "VIEWER";

export interface LoginRequest {
  username: string;
  password?: string;
}

export interface LoginResponse {
  token: string;
  expiresIn: number;
  tokenType: string;
}

export interface AuthUser {
  username: string;
  role: AppUserRole;
}

export interface ImportRowErrorDto {
  rowNumber: number;
  errorMessage: string;
  rawData?: string;
}

export interface ImportSummaryResponse {
  importId: number;
  fileType: "BANK_STATEMENT" | "INTERNAL_TRANSACTIONS";
  totalRows: number;
  insertedRows: number;
  skippedRows: number;
  invalidRows: number;
  errors: ImportRowErrorDto[];
}

export type ReconciliationRunStatus = "RUNNING" | "COMPLETED" | "FAILED";

export interface ReconciliationRunDto {
  id: number;
  bankImportId: number;
  internalImportId: number;
  status: ReconciliationRunStatus;
  startedAt: string;
  completedAt?: string | null;
}

export interface ReconciliationRunRequest {
  bankImportId: number;
  internalImportId: number;
}

export interface ReconciliationRunSummaryResponse {
  runId: number;
  status: string;
  totalResults: number;
  resultsByRule: Record<string, number>;
  message?: string;
}

export interface RunSummaryResponse {
  runId: number;
  status: string;
  countPerResultType: Record<string, number>;
  totalValuePerResultType: Record<string, number>;
  matchedValue: number;
  unmatchedInternalValue: number;
  unmatchedBankCreditValue: number;
  unmatchedBankDebitValue: number;
  explainedValue: number;
  unclassifiedInternalCount: number;
  unclassifiedBankCount: number;
}

export interface ReconciliationResultDto {
  id: number;
  resultType: string;
  ruleName: string;
  ruleCode: string;
  amountDifference: number;
  explanation: string;
  internalTransactionIds?: string | null;
  bankReferences?: string | null;
  internalTransactionDate?: string | null;
  bankTransactionDate?: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface AuditEventDto {
  id: number;
  runId?: number | null;
  eventType: string;
  actor: string;
  details: string;
  occurredAt: string;
}

export interface SystemHealthResponse {
  status: "UP" | "DOWN" | string;
}
