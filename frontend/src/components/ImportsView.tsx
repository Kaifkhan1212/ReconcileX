import { useState } from "react";
import { useAuth } from "@/lib/auth-context";
import {
  uploadBankStatementApi,
  uploadInternalTransactionsApi,
  type FileImportResult,
} from "@/lib/api";
import {
  BANK_REQUIRED_HEADERS,
  INTERNAL_REQUIRED_HEADERS,
} from "@/lib/matching-rules";
import { Button } from "@/components/ui/button";
import {
  UploadCloud,
  FileSpreadsheet,
  CheckCircle2,
  AlertTriangle,
  PlayCircle,
  FileCode,
  AlertCircle,
  ShieldAlert,
} from "lucide-react";
import { AuthModal } from "./AuthModal";

interface ImportsViewProps {
  onProceedToRun: (bankImportId: number, internalImportId: number) => void;
}

export function ImportsView({ onProceedToRun }: ImportsViewProps) {
  const { token, isAdmin, isAuthenticated } = useAuth();
  const [authModalOpen, setAuthModalOpen] = useState(false);

  // Bank Statement state
  const [bankFile, setBankFile] = useState<File | null>(null);
  const [bankLoading, setBankLoading] = useState(false);
  const [bankResult, setBankResult] = useState<FileImportResult | null>(null);
  const [bankError, setBankError] = useState<string | null>(null);
  const [isBankDragOver, setIsBankDragOver] = useState(false);

  // Internal Transactions state
  const [internalFile, setInternalFile] = useState<File | null>(null);
  const [internalLoading, setInternalLoading] = useState(false);
  const [internalResult, setInternalResult] = useState<FileImportResult | null>(null);
  const [internalError, setInternalError] = useState<string | null>(null);
  const [isInternalDragOver, setIsInternalDragOver] = useState(false);

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  const handleBankUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bankFile) return;
    setBankLoading(true);
    setBankError(null);
    try {
      const res = await uploadBankStatementApi(bankFile, token);
      setBankResult(res);
    } catch (err: unknown) {
      setBankError(err instanceof Error ? err.message : "Bank statement ingestion failed");
    } finally {
      setBankLoading(false);
    }
  };

  const handleInternalUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!internalFile) return;
    setInternalLoading(true);
    setInternalError(null);
    try {
      const res = await uploadInternalTransactionsApi(internalFile, token);
      setInternalResult(res);
    } catch (err: unknown) {
      setInternalError(err instanceof Error ? err.message : "Internal transactions ingestion failed");
    } finally {
      setInternalLoading(false);
    }
  };

  const canRunReconciliation =
    bankResult?.data.importId != null && internalResult?.data.importId != null;

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 p-5 rounded-xl border border-slate-800 bg-slate-900/50 shadow-xs">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5 flex-wrap">
            <h1 className="text-lg font-semibold tracking-tight text-white">
              Ledger Ingestion &amp; Idempotency
            </h1>
            <span className="inline-flex items-center rounded-md border border-emerald-500/20 bg-emerald-500/10 px-2 py-0.5 text-xs font-mono font-medium text-emerald-400">
              SHA-256 Idempotent
            </span>
            <span className="inline-flex items-center rounded-md border border-slate-700 bg-slate-800 text-slate-300 px-2 py-0.5 text-xs font-mono font-medium uppercase">
              ADMIN
            </span>
          </div>
          <p className="text-xs text-slate-400 max-w-2xl leading-relaxed">
            Upload external bank statements and internal accounting CSV ledgers. Duplicate file detection guarantees safe idempotent re-runs without double counting.
          </p>
        </div>

        <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-slate-800 bg-slate-950/60 text-xs text-slate-400 font-mono">
          <FileCode className="h-3.5 w-3.5 text-slate-300" />
          <span>RFC 4180 CSV Compliant</span>
        </div>
      </div>

      {/* Permission Restriction Callout */}
      {!isAdmin && (
        <div className="flex items-center justify-between gap-3 p-4 rounded-xl border border-amber-500/25 bg-amber-500/10 text-amber-300 text-xs">
          <div className="flex items-center gap-2.5">
            <ShieldAlert className="h-4 w-4 shrink-0 text-amber-400" />
            <div>
              <span className="font-semibold text-white">Admin Authentication Required:</span>{" "}
              {isAuthenticated
                ? "Your current account has VIEWER permissions. File ingestion requires an ADMIN role."
                : "Sign in with an ADMIN account to ingest transaction datasets."}
            </div>
          </div>
          <Button
            size="sm"
            variant="outline"
            onClick={() => setAuthModalOpen(true)}
            className="h-7 text-xs border-amber-500/30 text-amber-200 hover:bg-amber-500/20 bg-transparent"
          >
            Sign In
          </Button>
        </div>
      )}

      {/* Reconciliation Pipeline Ready Banner */}
      {canRunReconciliation && (
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 rounded-xl border border-emerald-500/30 bg-emerald-500/10 text-emerald-300 text-xs">
          <div className="flex items-center gap-2.5">
            <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-400" />
            <div>
              <span className="font-semibold text-white">Both ledgers ingested successfully!</span>{" "}
              Bank Statement #{bankResult?.data.importId} and Internal Ledger #{internalResult?.data.importId} are ready to be matched.
            </div>
          </div>
          <Button
            size="sm"
            onClick={() =>
              onProceedToRun(bankResult!.data.importId, internalResult!.data.importId)
            }
            className="h-8 text-xs font-semibold gap-1.5 bg-emerald-600 hover:bg-emerald-500 text-white shrink-0 shadow-sm"
          >
            <PlayCircle className="h-3.5 w-3.5" />
            <span>Launch Reconciliation Run</span>
          </Button>
        </div>
      )}

      {/* Ingestion Columns Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Column 1: Bank Statement */}
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 shadow-xs space-y-4 flex flex-col justify-between">
          <div className="space-y-3">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <div className="h-7 w-7 rounded-lg bg-slate-800 flex items-center justify-center text-slate-300">
                  <FileSpreadsheet className="h-4 w-4" />
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-white">1. Bank Statement Ledger</h2>
                  <span className="text-[11px] text-slate-400">External Banking Partner Statement</span>
                </div>
              </div>
              <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded border border-slate-800 bg-slate-950/60 text-slate-400">
                External Feed
              </span>
            </div>

            <div className="space-y-1.5">
              <span className="text-xs font-medium text-slate-300">Required Schema Headers:</span>
              <div className="flex flex-wrap gap-1 text-[11px] font-mono text-slate-400">
                {BANK_REQUIRED_HEADERS.map((h) => (
                  <span key={h} className="px-1.5 py-0.5 rounded border border-slate-800 bg-slate-950/50 text-slate-300">
                    {h}
                  </span>
                ))}
              </div>
            </div>

            <form onSubmit={handleBankUpload} className="space-y-3 pt-1">
              <div
                onDragOver={(e) => {
                  e.preventDefault();
                  if (isAdmin) setIsBankDragOver(true);
                }}
                onDragLeave={() => setIsBankDragOver(false)}
                onDrop={(e) => {
                  e.preventDefault();
                  setIsBankDragOver(false);
                  if (!isAdmin) return;
                  const files = e.dataTransfer.files;
                  if (files && files[0] && files[0].name.endsWith(".csv")) {
                    setBankFile(files[0]);
                  }
                }}
                className={`relative flex flex-col items-center justify-center p-6 border-2 border-dashed rounded-xl transition-all text-center ${
                  isBankDragOver
                    ? "border-indigo-500 bg-indigo-500/5"
                    : "border-slate-800 hover:border-slate-700 bg-slate-950/30"
                } ${!isAdmin ? "opacity-60 cursor-not-allowed" : "cursor-pointer"}`}
              >
                <input
                  type="file"
                  id="bank-file-input"
                  accept=".csv"
                  disabled={!isAdmin || bankLoading}
                  className="absolute inset-0 w-full h-full opacity-0 cursor-pointer disabled:cursor-not-allowed"
                  onChange={(e) => setBankFile(e.target.files?.[0] || null)}
                />
                <UploadCloud className="h-7 w-7 text-slate-500 mb-2" />
                <span className="text-xs font-medium text-slate-200">
                  {bankFile ? bankFile.name : "Choose CSV or drag & drop"}
                </span>
                <span className="text-[11px] text-slate-500 mt-0.5 font-mono">
                  {bankFile ? `${formatFileSize(bankFile.size)} • Click to replace` : "Comma-separated statement format"}
                </span>
              </div>

              {bankError && (
                <div className="flex items-start gap-2 p-3 text-xs rounded-lg bg-rose-500/10 text-rose-400 border border-rose-500/20">
                  <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                  <span>{bankError}</span>
                </div>
              )}

              <Button
                type="submit"
                disabled={!isAdmin || !bankFile || bankLoading}
                className="w-full h-9 text-xs font-semibold gap-1.5 bg-indigo-600 hover:bg-indigo-500 text-white"
              >
                <UploadCloud className="h-4 w-4" />
                {bankLoading ? "Ingesting & Verifying SHA-256..." : "Ingest Bank Statement"}
              </Button>
            </form>
          </div>

          {/* Ingestion Telemetry Output */}
          {bankResult && (
            <div className="mt-4 p-3.5 rounded-lg border border-slate-800 bg-slate-950/50 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5">
                  {bankResult.isDuplicate ? (
                    <span className="inline-flex items-center gap-1 text-[11px] font-mono font-medium text-amber-400 bg-amber-500/10 border border-amber-500/20 px-2 py-0.5 rounded">
                      <AlertTriangle className="h-3 w-3" />
                      Already imported as Import #{bankResult.data.importId}
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 text-[11px] font-mono font-medium text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-2 py-0.5 rounded">
                      <CheckCircle2 className="h-3 w-3" />
                      Ingestion Complete
                    </span>
                  )}
                </div>
                <span className="font-mono text-xs font-semibold text-white">
                  Import #{bankResult.data.importId}
                </span>
              </div>

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center text-xs font-mono">
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">Total</div>
                  <div className="font-semibold text-white mt-0.5">{bankResult.data.totalRows}</div>
                </div>
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">
                    {bankResult.isDuplicate ? "Original Inserts" : "Inserted"}
                  </div>
                  <div className="font-semibold text-emerald-400 mt-0.5">
                    {bankResult.data.insertedRows}
                  </div>
                </div>
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">Skipped</div>
                  <div className="font-semibold text-amber-400 mt-0.5">
                    {bankResult.data.skippedRows}
                  </div>
                </div>
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">Invalid</div>
                  <div className="font-semibold text-rose-400 mt-0.5">
                    {bankResult.data.invalidRows}
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Column 2: Internal Accounting Ledger */}
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 shadow-xs space-y-4 flex flex-col justify-between">
          <div className="space-y-3">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <div className="h-7 w-7 rounded-lg bg-slate-800 flex items-center justify-center text-slate-300">
                  <FileSpreadsheet className="h-4 w-4" />
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-white">2. Internal Accounting Ledger</h2>
                  <span className="text-[11px] text-slate-400">Order System &amp; Internal Payments</span>
                </div>
              </div>
              <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded border border-slate-800 bg-slate-950/60 text-slate-400">
                System Ledger
              </span>
            </div>

            <div className="space-y-1.5">
              <span className="text-xs font-medium text-slate-300">Required Schema Headers:</span>
              <div className="flex flex-wrap gap-1 text-[11px] font-mono text-slate-400">
                {INTERNAL_REQUIRED_HEADERS.map((h) => (
                  <span key={h} className="px-1.5 py-0.5 rounded border border-slate-800 bg-slate-950/50 text-slate-300">
                    {h}
                  </span>
                ))}
              </div>
            </div>

            <form onSubmit={handleInternalUpload} className="space-y-3 pt-1">
              <div
                onDragOver={(e) => {
                  e.preventDefault();
                  if (isAdmin) setIsInternalDragOver(true);
                }}
                onDragLeave={() => setIsInternalDragOver(false)}
                onDrop={(e) => {
                  e.preventDefault();
                  setIsInternalDragOver(false);
                  if (!isAdmin) return;
                  const files = e.dataTransfer.files;
                  if (files && files[0] && files[0].name.endsWith(".csv")) {
                    setInternalFile(files[0]);
                  }
                }}
                className={`relative flex flex-col items-center justify-center p-6 border-2 border-dashed rounded-xl transition-all text-center ${
                  isInternalDragOver
                    ? "border-indigo-500 bg-indigo-500/5"
                    : "border-slate-800 hover:border-slate-700 bg-slate-950/30"
                } ${!isAdmin ? "opacity-60 cursor-not-allowed" : "cursor-pointer"}`}
              >
                <input
                  type="file"
                  id="internal-file-input"
                  accept=".csv"
                  disabled={!isAdmin || internalLoading}
                  className="absolute inset-0 w-full h-full opacity-0 cursor-pointer disabled:cursor-not-allowed"
                  onChange={(e) => setInternalFile(e.target.files?.[0] || null)}
                />
                <UploadCloud className="h-7 w-7 text-slate-500 mb-2" />
                <span className="text-xs font-medium text-slate-200">
                  {internalFile ? internalFile.name : "Choose CSV or drag & drop"}
                </span>
                <span className="text-[11px] text-slate-500 mt-0.5 font-mono">
                  {internalFile ? `${formatFileSize(internalFile.size)} • Click to replace` : "Comma-separated ledger format"}
                </span>
              </div>

              {internalError && (
                <div className="flex items-start gap-2 p-3 text-xs rounded-lg bg-rose-500/10 text-rose-400 border border-rose-500/20">
                  <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                  <span>{internalError}</span>
                </div>
              )}

              <Button
                type="submit"
                disabled={!isAdmin || !internalFile || internalLoading}
                className="w-full h-9 text-xs font-semibold gap-1.5 bg-indigo-600 hover:bg-indigo-500 text-white"
              >
                <UploadCloud className="h-4 w-4" />
                {internalLoading ? "Ingesting & Verifying SHA-256..." : "Ingest Internal Transactions"}
              </Button>
            </form>
          </div>

          {/* Ingestion Telemetry Output */}
          {internalResult && (
            <div className="mt-4 p-3.5 rounded-lg border border-slate-800 bg-slate-950/50 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5">
                  {internalResult.isDuplicate ? (
                    <span className="inline-flex items-center gap-1 text-[11px] font-mono font-medium text-amber-400 bg-amber-500/10 border border-amber-500/20 px-2 py-0.5 rounded">
                      <AlertTriangle className="h-3 w-3" />
                      Already imported as Import #{internalResult.data.importId}
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 text-[11px] font-mono font-medium text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-2 py-0.5 rounded">
                      <CheckCircle2 className="h-3 w-3" />
                      Ingestion Complete
                    </span>
                  )}
                </div>
                <span className="font-mono text-xs font-semibold text-white">
                  Import #{internalResult.data.importId}
                </span>
              </div>

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center text-xs font-mono">
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">Total</div>
                  <div className="font-semibold text-white mt-0.5">{internalResult.data.totalRows}</div>
                </div>
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">
                    {internalResult.isDuplicate ? "Original Inserts" : "Inserted"}
                  </div>
                  <div className="font-semibold text-emerald-400 mt-0.5">
                    {internalResult.data.insertedRows}
                  </div>
                </div>
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">Skipped</div>
                  <div className="font-semibold text-amber-400 mt-0.5">
                    {internalResult.data.skippedRows}
                  </div>
                </div>
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">Invalid</div>
                  <div className="font-semibold text-rose-400 mt-0.5">
                    {internalResult.data.invalidRows}
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      <AuthModal open={authModalOpen} onOpenChange={setAuthModalOpen} />
    </div>
  );
}
