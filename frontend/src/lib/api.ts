import type {
  LoginResponse,
  ImportSummaryResponse,
  ReconciliationRunDto,
  ReconciliationRunSummaryResponse,
  RunSummaryResponse,
  ReconciliationResultDto,
  PageResponse,
  AuditEventDto,
  SystemHealthResponse,
  CreateUserRequest,
  UserResponse,
} from "./types";

const API_BASE = (import.meta.env["VITE_API_URL"] as string | undefined) || "";

export class ApiError extends Error {
  public status: number;
  public details?: string | undefined;

  constructor(status: number, message: string, details?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.details = details;
  }
}

type UnauthorizedHandler = () => void;
let unauthorizedHandler: UnauthorizedHandler | null = null;

export function registerUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  unauthorizedHandler = handler;
}

function handleAuthStatus(status: number) {
  if (status === 401 && unauthorizedHandler) {
    unauthorizedHandler();
  }
}

function getHeaders(token?: string | null, isJson = true): HeadersInit {
  const headers: Record<string, string> = {};
  if (isJson) {
    headers["Content-Type"] = "application/json";
  }
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  return headers;
}

export function formatErrorMessage(res: Response, text: string): string {
  if (res.status === 401) {
    return "Session expired or authentication required (401). Please sign in to continue.";
  }
  if (res.status === 403) {
    return "Permission denied (403). Your account requires an ADMIN role for this operation.";
  }
  try {
    const json = JSON.parse(text);
    return json.message || json.error || text;
  } catch {
    return text || `Request failed with status ${res.status}`;
  }
}

export async function loginApi(username: string, password?: string): Promise<LoginResponse> {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: "POST",
    headers: getHeaders(undefined, true),
    body: JSON.stringify({ username, password: password || "" }),
  });

  if (!res.ok) {
    const errorText = await res.text();
    throw new ApiError(res.status, formatErrorMessage(res, errorText));
  }

  return (await res.json()) as LoginResponse;
}

export async function createUserApi(
  req: CreateUserRequest,
  token?: string | null,
): Promise<UserResponse> {
  const res = await fetch(`${API_BASE}/api/admin/users`, {
    method: "POST",
    headers: getHeaders(token, true),
    body: JSON.stringify(req),
  });

  if (!res.ok) {
    handleAuthStatus(res.status);
    const errorText = await res.text();
    throw new ApiError(res.status, formatErrorMessage(res, errorText));
  }

  return (await res.json()) as UserResponse;
}

export async function getUsersApi(token?: string | null): Promise<UserResponse[]> {
  const res = await fetch(`${API_BASE}/api/admin/users`, {
    method: "GET",
    headers: getHeaders(token, true),
  });

  if (!res.ok) {
    handleAuthStatus(res.status);
    const errorText = await res.text();
    throw new ApiError(res.status, formatErrorMessage(res, errorText));
  }

  return (await res.json()) as UserResponse[];
}

export async function getHealthApi(): Promise<SystemHealthResponse> {
  const res = await fetch(`${API_BASE}/api/health`, {
    headers: getHeaders(),
  });
  if (!res.ok) {
    throw new ApiError(res.status, `Health check returned status ${res.status}`);
  }
  return (await res.json()) as SystemHealthResponse;
}

export interface FileImportResult {
  data: ImportSummaryResponse;
  isDuplicate: boolean;
}

export async function uploadBankStatementApi(
  file: File,
  token?: string | null,
): Promise<FileImportResult> {
  const formData = new FormData();
  formData.append("file", file);

  const res = await fetch(`${API_BASE}/api/imports/bank`, {
    method: "POST",
    headers: getHeaders(token, false),
    body: formData,
  });

  if (res.status === 409) {
    const data = (await res.json()) as ImportSummaryResponse;
    return { data, isDuplicate: true };
  }

  if (!res.ok) {
    handleAuthStatus(res.status);
    const errorText = await res.text();
    throw new ApiError(res.status, formatErrorMessage(res, errorText));
  }

  const data = (await res.json()) as ImportSummaryResponse;
  return { data, isDuplicate: false };
}

export async function uploadInternalTransactionsApi(
  file: File,
  token?: string | null,
): Promise<FileImportResult> {
  const formData = new FormData();
  formData.append("file", file);

  const res = await fetch(`${API_BASE}/api/imports/internal`, {
    method: "POST",
    headers: getHeaders(token, false),
    body: formData,
  });

  if (res.status === 409) {
    const data = (await res.json()) as ImportSummaryResponse;
    return { data, isDuplicate: true };
  }

  if (!res.ok) {
    handleAuthStatus(res.status);
    const errorText = await res.text();
    throw new ApiError(res.status, formatErrorMessage(res, errorText));
  }

  const data = (await res.json()) as ImportSummaryResponse;
  return { data, isDuplicate: false };
}

export async function listReconciliationRunsApi(
  page = 0,
  size = 20,
  token?: string | null,
): Promise<PageResponse<ReconciliationRunDto>> {
  const res = await fetch(`${API_BASE}/api/reconciliation/runs?page=${page}&size=${size}`, {
    headers: getHeaders(token),
  });

  if (!res.ok) {
    handleAuthStatus(res.status);
    const errorText = await res.text();
    throw new ApiError(res.status, formatErrorMessage(res, errorText));
  }

  return (await res.json()) as PageResponse<ReconciliationRunDto>;
}

export async function getReconciliationRunApi(
  id: number,
  token?: string | null,
): Promise<ReconciliationRunDto> {
  const res = await fetch(`${API_BASE}/api/reconciliation/runs/${id}`, {
    headers: getHeaders(token),
  });

  if (!res.ok) {
    handleAuthStatus(res.status);
    const errorText = await res.text();
    throw new ApiError(res.status, formatErrorMessage(res, errorText));
  }

  return (await res.json()) as ReconciliationRunDto;
}

export async function createReconciliationRunApi(
  bankImportId: number,
  internalImportId: number,
  token?: string | null,
): Promise<ReconciliationRunSummaryResponse> {
  const res = await fetch(`${API_BASE}/api/reconciliation/runs`, {
    method: "POST",
    headers: getHeaders(token, true),
    body: JSON.stringify({ bankImportId, internalImportId }),
  });

  if (!res.ok) {
    handleAuthStatus(res.status);
    const errorText = await res.text();
    throw new ApiError(res.status, formatErrorMessage(res, errorText));
  }

  return (await res.json()) as ReconciliationRunSummaryResponse;
}

export async function getRunSummaryApi(
  id: number,
  token?: string | null,
): Promise<RunSummaryResponse> {
  const res = await fetch(`${API_BASE}/api/reconciliation/runs/${id}/summary`, {
    headers: getHeaders(token),
  });

  if (!res.ok) {
    handleAuthStatus(res.status);
    const errorText = await res.text();
    throw new ApiError(res.status, formatErrorMessage(res, errorText));
  }

  return (await res.json()) as RunSummaryResponse;
}

export interface ResultsFilterParams {
  resultType?: string;
  ruleName?: string;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export async function getRunResultsApi(
  id: number,
  params: ResultsFilterParams = {},
  token?: string | null,
): Promise<PageResponse<ReconciliationResultDto>> {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set("page", params.page.toString());
  if (params.size !== undefined) searchParams.set("size", params.size.toString());
  if (params.resultType) searchParams.set("resultType", params.resultType);
  if (params.ruleName) searchParams.set("ruleName", params.ruleName);
  if (params.fromDate) searchParams.set("fromDate", params.fromDate);
  if (params.toDate) searchParams.set("toDate", params.toDate);

  const query = searchParams.toString();
  const res = await fetch(
    `${API_BASE}/api/reconciliation/runs/${id}/results${query ? `?${query}` : ""}`,
    {
      headers: getHeaders(token),
    },
  );

  if (!res.ok) {
    handleAuthStatus(res.status);
    const errorText = await res.text();
    throw new ApiError(res.status, formatErrorMessage(res, errorText));
  }

  return (await res.json()) as PageResponse<ReconciliationResultDto>;
}

export async function downloadReportCsvApi(id: number, token?: string | null): Promise<Blob> {
  const res = await fetch(`${API_BASE}/api/reconciliation/runs/${id}/report.csv`, {
    headers: getHeaders(token, false),
  });

  if (!res.ok) {
    handleAuthStatus(res.status);
    const errorText = await res.text();
    throw new ApiError(res.status, formatErrorMessage(res, errorText));
  }

  return await res.blob();
}

export interface AuditFilterParams {
  runId?: number;
  eventType?: string;
  page?: number;
  size?: number;
}

export async function getAuditEventsApi(
  params: AuditFilterParams = {},
  token?: string | null,
): Promise<PageResponse<AuditEventDto>> {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set("page", params.page.toString());
  if (params.size !== undefined) searchParams.set("size", params.size.toString());
  if (params.runId !== undefined) searchParams.set("runId", params.runId.toString());
  if (params.eventType) searchParams.set("eventType", params.eventType);

  const query = searchParams.toString();
  const res = await fetch(`${API_BASE}/api/audit-events${query ? `?${query}` : ""}`, {
    headers: getHeaders(token),
  });

  if (!res.ok) {
    handleAuthStatus(res.status);
    const errorText = await res.text();
    throw new ApiError(res.status, formatErrorMessage(res, errorText));
  }

  return (await res.json()) as PageResponse<AuditEventDto>;
}
