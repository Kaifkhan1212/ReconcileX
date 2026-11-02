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
  Loader2,
  Hash,
  Database,
  Check,
} from "lucide-react";
import { AuthModal } from "./AuthModal";

export type UploadStage =
  | "IDLE"
  | "FILE_SELECTED"
  | "CALCULATING_SHA256"
  | "UPLOADING"
  | "VALIDATING"
  | "PERSISTING"
  | "COMPLETED";

interface ImportsViewProps {
  onProceedToRun: (bankImportId: number, internalImportId: number) => void;
}

export function ImportsView({ onProceedToRun }: ImportsViewProps) {
  const { token, isAdmin, isAuthenticated } = useAuth();
  const [authModalOpen, setAuthModalOpen] = useState(false);

  // Bank Statement state
  const [bankFile, setBankFile] = useState<File | null>(null);
  const [bankStage, setBankStage] = useState<UploadStage>("IDLE");
  const [bankSha256, setBankSha256] = useState<string | null>(null);
  const [bankResult, setBankResult] = useState<FileImportResult | null>(null);
  const [bankError, setBankError] = useState<string | null>(null);
  const [isBankDragOver, setIsBankDragOver] = useState(false);

  // Internal Transactions state
  const [internalFile, setInternalFile] = useState<File | null>(null);
  const [internalStage, setInternalStage] = useState<UploadStage>("IDLE");
  const [internalSha256, setInternalSha256] = useState<string | null>(null);
  const [internalResult, setInternalResult] = useState<FileImportResult | null>(null);
  const [internalError, setInternalError] = useState<string | null>(null);
  const [isInternalDragOver, setIsInternalDragOver] = useState(false);

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  const calculateClientSha256 = async (file: File): Promise<string> => {
    const buffer = await file.arrayBuffer();
    const hashBuffer = await crypto.subtle.digest("SHA-256", buffer);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
  };

  const handleBankUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bankFile) return;
    setBankError(null);
    setBankResult(null);

    try {
      // Stage 2: Calculating SHA-256
      setBankStage("CALCULATING_SHA256");
      const hash = await calculateClientSha256(bankFile);
      setBankSha256(hash);

      // Stage 3: Uploading
      setBankStage("UPLOADING");
      
      // Simulate micro transition to server validation & persisting indicator
      const progressTimer = setTimeout(() => {
        setBankStage("VALIDATING");
        setTimeout(() => {
          setBankStage("PERSISTING");
        }, 1200);
      }, 1000);

      const res = await uploadBankStatementApi(bankFile, token);
      clearTimeout(progressTimer);

      // Stage 6: Completed
      setBankResult(res);
      setBankStage("COMPLETED");
    } catch (err: unknown) {
      setBankError(err instanceof Error ? err.message : "Bank statement ingestion failed");
      setBankStage("FILE_SELECTED");
    }
  };

  const handleInternalUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!internalFile) return;
    setInternalError(null);
    setInternalResult(null);

    try {
      // Stage 2: Calculating SHA-256
      setInternalStage("CALCULATING_SHA256");
      const hash = await calculateClientSha256(internalFile);
      setInternalSha256(hash);

      // Stage 3: Uploading
      setInternalStage("UPLOADING");

      // Progress animation
      const progressTimer = setTimeout(() => {
        setInternalStage("VALIDATING");
        setTimeout(() => {
          setInternalStage("PERSISTING");
        }, 1200);
      }, 1000);

      const res = await uploadInternalTransactionsApi(internalFile, token);
      clearTimeout(progressTimer);

      // Stage 6: Completed
      setInternalResult(res);
      setInternalStage("COMPLETED");
    } catch (err: unknown) {
      setInternalError(err instanceof Error ? err.message : "Internal transactions ingestion failed");
      setInternalStage("FILE_SELECTED");
    }
  };

  // An import is ready when it has a valid importId AND insertedRows > 0.
  // Duplicate imports where insertedRows > 0 are valid — the original import had real data.
  // Poisoned duplicates (isDuplicate=true, insertedRows=0) block the CTA and show a re-upload callout.
  const bankReady =
    bankResult != null &&
    bankResult.data.importId != null &&
    bankResult.data.insertedRows > 0;

  const internalReady =
    internalResult != null &&
    internalResult.data.importId != null &&
    internalResult.data.insertedRows > 0;

  const canRunReconciliation = bankReady && internalReady;

  // Poisoned import: duplicate detected but 0 rows actually stored — re-upload required
  const bankPoisoned =
    bankResult != null &&
    bankResult.isDuplicate &&
    bankResult.data.insertedRows === 0;

  const internalPoisoned =
    internalResult != null &&
    internalResult.isDuplicate &&
    internalResult.data.insertedRows === 0;

  const renderStageStepper = (currentStage: UploadStage, hash: string | null) => {
    const stages: { stage: UploadStage; label: string; desc: string }[] = [
      { stage: "FILE_SELECTED", label: "1. File Selected", desc: "Local CSV chosen" },
      { stage: "CALCULATING_SHA256", label: "2. SHA-256 Fingerprint", desc: "Idempotency hash" },
      { stage: "UPLOADING", label: "3. Uploading", desc: "Streaming payload" },
      { stage: "VALIDATING", label: "4. Validating CSV", desc: "RFC 4180 & schema" },
      { stage: "PERSISTING", label: "5. Batch Persisting", desc: "PostgreSQL multi-row" },
      { stage: "COMPLETED", label: "6. Ingested", desc: "Ready for match engine" },
    ];

    const getStageIndex = (st: UploadStage): number => {
      switch (st) {
        case "FILE_SELECTED": return 0;
        case "CALCULATING_SHA256": return 1;
        case "UPLOADING": return 2;
        case "VALIDATING": return 3;
        case "PERSISTING": return 4;
        case "COMPLETED": return 5;
        default: return -1;
      }
    };

    const currentIndex = getStageIndex(currentStage);
    if (currentIndex < 0) return null;

    return (
      <div className="p-3 rounded-lg border border-slate-800 bg-slate-950/60 space-y-2">
        <div className="flex items-center justify-between text-[11px] font-mono text-slate-400">
          <span className="font-semibold text-slate-300">Ingestion Pipeline Progress</span>
          <span>Stage {currentIndex + 1} of 6</span>
        </div>
        <div className="grid grid-cols-6 gap-1">
          {stages.map((s, idx) => {
            const isDone = idx < currentIndex || currentStage === "COMPLETED";
            const isActive = idx === currentIndex && currentStage !== "COMPLETED";
            return (
              <div
                key={s.stage}
                className={`h-1.5 rounded-full transition-all duration-300 ${
                  isDone
                    ? "bg-emerald-500"
                    : isActive
                    ? "bg-indigo-500 animate-pulse"
                    : "bg-slate-800"
                }`}
              />
            );
          })}
        </div>
        <div className="flex items-center justify-between text-[10px] font-mono">
          <span className="text-slate-300 flex items-center gap-1.5">
            {currentStage === "COMPLETED" ? (
              <Check className="h-3 w-3 text-emerald-400" />
            ) : (
              <Loader2 className="h-3 w-3 animate-spin text-indigo-400" />
            )}
            {stages[currentIndex]?.label}: {stages[currentIndex]?.desc}
          </span>
          {hash && (
            <span
              className="text-slate-500 font-mono text-[9px] truncate max-w-[120px]"
              title={hash}
            >
              #{hash.substring(0, 8)}...
            </span>
          )}
        </div>
      </div>
    );
  };

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
            <span className="inline-flex items-center rounded-md border border-indigo-500/20 bg-indigo-500/10 px-2 py-0.5 text-xs font-mono font-medium text-indigo-400">
              PostgreSQL Batch Rewriting
            </span>
            <span className="inline-flex items-center rounded-md border border-slate-700 bg-slate-800 text-slate-300 px-2 py-0.5 text-xs font-mono font-medium uppercase">
              ADMIN
            </span>
          </div>
          <p className="text-xs text-slate-400 max-w-2xl leading-relaxed">
            Upload external bank statements and internal accounting CSV ledgers. Streamlined SHA-256 deduplication and batch inserts guarantee atomic, non-blocking ingestion for datasets exceeding 50,000 rows.
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

      {/* Poisoned Import Re-upload Warning Banner */}
      {(bankPoisoned || internalPoisoned) && (
        <div className="flex flex-col sm:flex-row sm:items-start gap-3 p-4 rounded-xl border border-rose-500/30 bg-rose-500/8 text-rose-300 text-xs">
          <AlertTriangle className="h-4 w-4 shrink-0 text-rose-400 mt-0.5" />
          <div className="space-y-1.5">
            <span className="font-semibold text-white block">Re-upload Required — Poisoned Import Detected</span>
            <p className="text-rose-300/90 leading-relaxed">
              {bankPoisoned && internalPoisoned
                ? `Import #${bankResult!.data.importId} (Bank) and Import #${internalResult!.data.importId} (Internal) both have 0 rows stored from a failed previous attempt.`
                : bankPoisoned
                ? `Import #${bankResult!.data.importId} (Bank Statement) has 0 rows stored from a failed previous attempt.`
                : `Import #${internalResult!.data.importId} (Internal Ledger) has 0 rows stored from a failed previous attempt.`}
              {" "}Re-upload the same CSV file(s) — the backend will automatically clean up the partial state and re-ingest all rows.
            </p>
          </div>
        </div>
      )}

      {/* Reconciliation Pipeline Ready Banner */}
      {canRunReconciliation && (
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 rounded-xl border border-emerald-500/30 bg-emerald-500/10 text-emerald-300 text-xs">
          <div className="flex items-center gap-2.5">
            <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-400" />
            <div>
              <span className="font-semibold text-white">Both ledgers ingested successfully!</span>{" "}
              Bank Statement #{bankResult?.data.importId} ({bankResult?.data.insertedRows.toLocaleString()} rows) and Internal Ledger #{internalResult?.data.importId} ({internalResult?.data.insertedRows.toLocaleString()} rows) are ready to be matched.
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
                    setBankStage("FILE_SELECTED");
                    setBankResult(null);
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
                  disabled={!isAdmin || (bankStage !== "IDLE" && bankStage !== "FILE_SELECTED" && bankStage !== "COMPLETED")}
                  className="absolute inset-0 w-full h-full opacity-0 cursor-pointer disabled:cursor-not-allowed"
                  onChange={(e) => {
                    const f = e.target.files?.[0] || null;
                    setBankFile(f);
                    setBankStage(f ? "FILE_SELECTED" : "IDLE");
                    setBankResult(null);
                  }}
                />
                <UploadCloud className="h-7 w-7 text-slate-500 mb-2" />
                <span className="text-xs font-medium text-slate-200">
                  {bankFile ? bankFile.name : "Choose CSV or drag & drop"}
                </span>
                <span className="text-[11px] text-slate-500 mt-0.5 font-mono">
                  {bankFile ? `${formatFileSize(bankFile.size)} • Click to replace` : "Comma-separated statement format (up to 50MB)"}
                </span>
              </div>

              {/* Progress Stepper */}
              {renderStageStepper(bankStage, bankSha256)}

              {bankError && (
                <div className="flex items-start gap-2 p-3 text-xs rounded-lg bg-rose-500/10 text-rose-400 border border-rose-500/20">
                  <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                  <span>{bankError}</span>
                </div>
              )}

              <Button
                type="submit"
                disabled={
                  !isAdmin ||
                  !bankFile ||
                  (bankStage !== "IDLE" && bankStage !== "FILE_SELECTED" && bankStage !== "COMPLETED")
                }
                className="w-full h-9 text-xs font-semibold gap-1.5 bg-indigo-600 hover:bg-indigo-500 text-white"
              >
                {bankStage === "CALCULATING_SHA256" ? (
                  <>
                    <Hash className="h-4 w-4 animate-spin" />
                    Calculating SHA-256 Fingerprint...
                  </>
                ) : bankStage === "UPLOADING" || bankStage === "VALIDATING" || bankStage === "PERSISTING" ? (
                  <>
                    <Database className="h-4 w-4 animate-pulse" />
                    Ingesting Bank Statement...
                  </>
                ) : (
                  <>
                    <UploadCloud className="h-4 w-4" />
                    Ingest Bank Statement
                  </>
                )}
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
                      Duplicate: Import #{bankResult.data.importId}
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

              {bankResult.isDuplicate && (
                <div className={`text-[11px] p-2 rounded leading-relaxed border ${
                  bankResult.data.insertedRows === 0
                    ? "text-rose-300/90 bg-rose-500/5 border-rose-500/15"
                    : "text-amber-300/90 bg-amber-500/5 border-amber-500/15"
                }`}>
                  {bankResult.data.insertedRows === 0 ? (
                    <span>
                      <strong>Action Required:</strong> This import has 0 rows stored (a previous upload failed mid-batch). Re-upload this same CSV file to trigger automatic cleanup and full re-ingestion.
                    </span>
                  ) : (
                    <span>
                      This identical CSV file (matched via SHA-256) was already successfully imported with {bankResult.data.insertedRows.toLocaleString()} rows. Idempotent deduplication prevents double-counting — Import #{bankResult.data.importId} is ready.
                    </span>
                  )}
                </div>
              )}

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center text-xs font-mono">
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">Total</div>
                  <div className="font-semibold text-white mt-0.5">{bankResult.data.totalRows.toLocaleString()}</div>
                </div>
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">
                    {bankResult.isDuplicate ? "Original Inserts" : "Inserted"}
                  </div>
                  <div className={`font-semibold mt-0.5 ${bankResult.data.insertedRows > 0 ? "text-emerald-400" : "text-amber-400"}`}>
                    {bankResult.data.insertedRows.toLocaleString()}
                  </div>
                </div>
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">Skipped</div>
                  <div className="font-semibold text-amber-400 mt-0.5">
                    {bankResult.data.skippedRows.toLocaleString()}
                  </div>
                </div>
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">Invalid</div>
                  <div className="font-semibold text-rose-400 mt-0.5">
                    {bankResult.data.invalidRows.toLocaleString()}
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
                    setInternalStage("FILE_SELECTED");
                    setInternalResult(null);
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
                  disabled={!isAdmin || (internalStage !== "IDLE" && internalStage !== "FILE_SELECTED" && internalStage !== "COMPLETED")}
                  className="absolute inset-0 w-full h-full opacity-0 cursor-pointer disabled:cursor-not-allowed"
                  onChange={(e) => {
                    const f = e.target.files?.[0] || null;
                    setInternalFile(f);
                    setInternalStage(f ? "FILE_SELECTED" : "IDLE");
                    setInternalResult(null);
                  }}
                />
                <UploadCloud className="h-7 w-7 text-slate-500 mb-2" />
                <span className="text-xs font-medium text-slate-200">
                  {internalFile ? internalFile.name : "Choose CSV or drag & drop"}
                </span>
                <span className="text-[11px] text-slate-500 mt-0.5 font-mono">
                  {internalFile ? `${formatFileSize(internalFile.size)} • Click to replace` : "Comma-separated ledger format (up to 50MB)"}
                </span>
              </div>

              {/* Progress Stepper */}
              {renderStageStepper(internalStage, internalSha256)}

              {internalError && (
                <div className="flex items-start gap-2 p-3 text-xs rounded-lg bg-rose-500/10 text-rose-400 border border-rose-500/20">
                  <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                  <span>{internalError}</span>
                </div>
              )}

              <Button
                type="submit"
                disabled={
                  !isAdmin ||
                  !internalFile ||
                  (internalStage !== "IDLE" && internalStage !== "FILE_SELECTED" && internalStage !== "COMPLETED")
                }
                className="w-full h-9 text-xs font-semibold gap-1.5 bg-indigo-600 hover:bg-indigo-500 text-white"
              >
                {internalStage === "CALCULATING_SHA256" ? (
                  <>
                    <Hash className="h-4 w-4 animate-spin" />
                    Calculating SHA-256 Fingerprint...
                  </>
                ) : internalStage === "UPLOADING" || internalStage === "VALIDATING" || internalStage === "PERSISTING" ? (
                  <>
                    <Database className="h-4 w-4 animate-pulse" />
                    Ingesting Internal Transactions...
                  </>
                ) : (
                  <>
                    <UploadCloud className="h-4 w-4" />
                    Ingest Internal Transactions
                  </>
                )}
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
                      Duplicate: Import #{internalResult.data.importId}
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

              {internalResult.isDuplicate && (
                <div className={`text-[11px] p-2 rounded leading-relaxed border ${
                  internalResult.data.insertedRows === 0
                    ? "text-rose-300/90 bg-rose-500/5 border-rose-500/15"
                    : "text-amber-300/90 bg-amber-500/5 border-amber-500/15"
                }`}>
                  {internalResult.data.insertedRows === 0 ? (
                    <span>
                      <strong>Action Required:</strong> This import has 0 rows stored (a previous upload failed mid-batch). Re-upload this same CSV file to trigger automatic cleanup and full re-ingestion.
                    </span>
                  ) : (
                    <span>
                      This identical CSV file (matched via SHA-256) was already successfully imported with {internalResult.data.insertedRows.toLocaleString()} rows. Idempotent deduplication prevents double-counting — Import #{internalResult.data.importId} is ready.
                    </span>
                  )}
                </div>
              )}

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center text-xs font-mono">
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">Total</div>
                  <div className="font-semibold text-white mt-0.5">{internalResult.data.totalRows.toLocaleString()}</div>
                </div>
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">
                    {internalResult.isDuplicate ? "Original Inserts" : "Inserted"}
                  </div>
                  <div className={`font-semibold mt-0.5 ${internalResult.data.insertedRows > 0 ? "text-emerald-400" : "text-amber-400"}`}>
                    {internalResult.data.insertedRows.toLocaleString()}
                  </div>
                </div>
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">Skipped</div>
                  <div className="font-semibold text-amber-400 mt-0.5">
                    {internalResult.data.skippedRows.toLocaleString()}
                  </div>
                </div>
                <div className="p-2 rounded bg-slate-900 border border-slate-800">
                  <div className="text-[10px] text-slate-500 uppercase font-sans">Invalid</div>
                  <div className="font-semibold text-rose-400 mt-0.5">
                    {internalResult.data.invalidRows.toLocaleString()}
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