import { useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { uploadBankStatementApi, uploadInternalTransactionsApi, type FileImportResult } from "@/lib/api";
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Upload,
  FileCheck,
  AlertCircle,
  CheckCircle2,
  AlertTriangle,
  PlayCircle,
  FileSpreadsheet,
  ArrowRight,
  ShieldAlert,
} from "lucide-react";

import { AuthModal } from "./AuthModal";
import { LogIn } from "lucide-react";

interface ImportsViewProps {
  onProceedToRun: (bankImportId: number, internalImportId: number) => void;
}

export function ImportsView({ onProceedToRun }: ImportsViewProps) {
  const { token, isAdmin, isAuthenticated } = useAuth();
  const [authModalOpen, setAuthModalOpen] = useState(false);

  const [bankFile, setBankFile] = useState<File | null>(null);
  const [internalFile, setInternalFile] = useState<File | null>(null);

  const [bankLoading, setBankLoading] = useState(false);
  const [internalLoading, setInternalLoading] = useState(false);

  const [bankResult, setBankResult] = useState<FileImportResult | null>(null);
  const [internalResult, setInternalResult] = useState<FileImportResult | null>(null);

  const [bankError, setBankError] = useState<string | null>(null);
  const [internalError, setInternalError] = useState<string | null>(null);

  const handleBankUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bankFile) return;
    setBankLoading(true);
    setBankError(null);
    try {
      const res = await uploadBankStatementApi(bankFile, token);
      setBankResult(res);
    } catch (err: unknown) {
      setBankError(err instanceof Error ? err.message : "Upload failed");
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
      setInternalError(err instanceof Error ? err.message : "Upload failed");
    } finally {
      setInternalLoading(false);
    }
  };

  const canRunReconciliation =
    bankResult?.data.importId != null && internalResult?.data.importId != null;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-foreground">
          Idempotent File Ingestion
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          Upload bank statements and internal transaction CSV files. SHA-256 fingerprinting ensures duplicate files return existing import records (HTTP 409) without double counting.
        </p>
      </div>

      {!isAdmin && (
        <div className="flex items-center justify-between gap-3 p-4 rounded-xl border border-amber-500/20 bg-amber-500/10 text-amber-800 dark:text-amber-300 text-sm">
          <div className="flex items-center gap-3">
            <ShieldAlert className="h-5 w-5 shrink-0" />
            <div>
              <p className="font-semibold">Admin Authentication Required for Ingestion</p>
              <p className="text-xs text-amber-700/80 dark:text-amber-300/80 mt-0.5">
                {isAuthenticated
                  ? "Your current account has VIEWER role. Ingestion requires an ADMIN JWT token."
                  : "Your session may have expired or you are signed out. Please sign in as ADMIN to upload files."}
              </p>
            </div>
          </div>
          <Button
            size="sm"
            onClick={() => setAuthModalOpen(true)}
            className="shrink-0 gap-1.5 text-xs bg-amber-700 hover:bg-amber-800 text-white dark:bg-amber-600"
          >
            <LogIn className="h-3.5 w-3.5" />
            Sign In as Admin
          </Button>
        </div>
      )}

      {/* Upload Columns */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Bank Statement Card */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <FileSpreadsheet className="h-5 w-5 text-primary" />
                <CardTitle className="text-base">1. Bank Statement (CSV)</CardTitle>
              </div>
              <Badge variant="outline">POST /api/imports/bank</Badge>
            </div>
            <CardDescription className="text-xs">
              Expected headers: bank_reference, payment_reference, transaction_date, credit_amount, debit_amount, description
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-4">
            <form onSubmit={handleBankUpload} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="bank-file">Select Bank Statement CSV</Label>
                <Input
                  id="bank-file"
                  type="file"
                  accept=".csv"
                  disabled={!isAdmin || bankLoading}
                  onChange={(e) => setBankFile(e.target.files?.[0] || null)}
                />
              </div>

              {bankError && (
                <div className="flex items-start gap-2 p-3 text-xs rounded-md bg-destructive/10 text-destructive border border-destructive/20">
                  <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                  <span>{bankError}</span>
                </div>
              )}

              <Button
                type="submit"
                disabled={!isAdmin || !bankFile || bankLoading}
                className="w-full gap-2"
              >
                <Upload className="h-4 w-4" />
                {bankLoading ? "Ingesting & Hashing..." : "Ingest Bank Statement"}
              </Button>
            </form>

            {bankResult && (
              <div className="mt-4 p-4 rounded-lg border border-border bg-secondary/30 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    {bankResult.isDuplicate ? (
                      <AlertTriangle className="h-4 w-4 text-amber-500" />
                    ) : (
                      <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                    )}
                    <span className="text-xs font-semibold">
                      {bankResult.isDuplicate
                        ? "409 Idempotent Duplicate Match"
                        : "200 Successfully Ingested"}
                    </span>
                  </div>
                  <Badge variant={bankResult.isDuplicate ? "secondary" : "default"}>
                    Import ID: #{bankResult.data.importId}
                  </Badge>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center text-xs">
                  <div className="p-2 rounded bg-background border border-border">
                    <div className="text-muted-foreground text-[10px]">Total Rows</div>
                    <div className="font-bold text-sm">{bankResult.data.totalRows}</div>
                  </div>
                  <div className="p-2 rounded bg-background border border-border">
                    <div className="text-muted-foreground text-[10px]">Inserted</div>
                    <div className="font-bold text-sm text-emerald-600">{bankResult.data.insertedRows}</div>
                  </div>
                  <div className="p-2 rounded bg-background border border-border">
                    <div className="text-muted-foreground text-[10px]">Skipped</div>
                    <div className="font-bold text-sm text-amber-600">{bankResult.data.skippedRows}</div>
                  </div>
                  <div className="p-2 rounded bg-background border border-border">
                    <div className="text-muted-foreground text-[10px]">Invalid</div>
                    <div className="font-bold text-sm text-destructive">{bankResult.data.invalidRows}</div>
                  </div>
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Internal Transactions Card */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <FileSpreadsheet className="h-5 w-5 text-indigo-500" />
                <CardTitle className="text-base">2. Internal Transactions (CSV)</CardTitle>
              </div>
              <Badge variant="outline">POST /api/imports/internal</Badge>
            </div>
            <CardDescription className="text-xs">
              Expected headers: transaction_id, payment_reference, transaction_date, amount, currency, payment_status, description
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-4">
            <form onSubmit={handleInternalUpload} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="internal-file">Select Internal Transactions CSV</Label>
                <Input
                  id="internal-file"
                  type="file"
                  accept=".csv"
                  disabled={!isAdmin || internalLoading}
                  onChange={(e) => setInternalFile(e.target.files?.[0] || null)}
                />
              </div>

              {internalError && (
                <div className="flex items-start gap-2 p-3 text-xs rounded-md bg-destructive/10 text-destructive border border-destructive/20">
                  <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                  <span>{internalError}</span>
                </div>
              )}

              <Button
                type="submit"
                disabled={!isAdmin || !internalFile || internalLoading}
                className="w-full gap-2"
              >
                <Upload className="h-4 w-4" />
                {internalLoading ? "Ingesting & Hashing..." : "Ingest Internal Transactions"}
              </Button>
            </form>

            {internalResult && (
              <div className="mt-4 p-4 rounded-lg border border-border bg-secondary/30 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    {internalResult.isDuplicate ? (
                      <AlertTriangle className="h-4 w-4 text-amber-500" />
                    ) : (
                      <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                    )}
                    <span className="text-xs font-semibold">
                      {internalResult.isDuplicate
                        ? "409 Idempotent Duplicate Match"
                        : "200 Successfully Ingested"}
                    </span>
                  </div>
                  <Badge variant={internalResult.isDuplicate ? "secondary" : "default"}>
                    Import ID: #{internalResult.data.importId}
                  </Badge>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center text-xs">
                  <div className="p-2 rounded bg-background border border-border">
                    <div className="text-muted-foreground text-[10px]">Total Rows</div>
                    <div className="font-bold text-sm">{internalResult.data.totalRows}</div>
                  </div>
                  <div className="p-2 rounded bg-background border border-border">
                    <div className="text-muted-foreground text-[10px]">Inserted</div>
                    <div className="font-bold text-sm text-emerald-600">{internalResult.data.insertedRows}</div>
                  </div>
                  <div className="p-2 rounded bg-background border border-border">
                    <div className="text-muted-foreground text-[10px]">Skipped</div>
                    <div className="font-bold text-sm text-amber-600">{internalResult.data.skippedRows}</div>
                  </div>
                  <div className="p-2 rounded bg-background border border-border">
                    <div className="text-muted-foreground text-[10px]">Invalid</div>
                    <div className="font-bold text-sm text-destructive">{internalResult.data.invalidRows}</div>
                  </div>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Next Step Banner */}
      {canRunReconciliation && (
        <Card className="border-emerald-500/30 bg-emerald-500/5">
          <CardContent className="flex flex-col sm:flex-row items-center justify-between gap-4 p-6">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-emerald-500/20 text-emerald-600 dark:text-emerald-400">
                <FileCheck className="h-5 w-5" />
              </div>
              <div>
                <h3 className="font-semibold text-foreground">Both Datasets Ready for Matching</h3>
                <p className="text-xs text-muted-foreground">
                  Bank Import #{bankResult!.data.importId} and Internal Import #{internalResult!.data.importId} are loaded in PostgreSQL.
                </p>
              </div>
            </div>

            <Button
              className="gap-2 shrink-0 bg-emerald-600 hover:bg-emerald-700 text-white"
              onClick={() => onProceedToRun(bankResult!.data.importId, internalResult!.data.importId)}
            >
              <PlayCircle className="h-4 w-4" />
              <span>Launch Reconciliation</span>
              <ArrowRight className="h-4 w-4" />
            </Button>
          </CardContent>
        </Card>
      )}

      <AuthModal open={authModalOpen} onOpenChange={setAuthModalOpen} />
    </div>
  );
}
