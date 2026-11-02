import { useState, useEffect, useRef, useCallback } from "react";
import { useAuth } from "@/lib/auth-context";
import {
  listReconciliationRunsApi,
  createReconciliationRunApi,
  getReconciliationRunApi,
  downloadReportCsvApi,
} from "@/lib/api";
import type { ReconciliationRunDto } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  PlayCircle,
  AlertCircle,
  CheckCircle2,
  Download,
  Eye,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  LogIn,
  Loader2,
} from "lucide-react";

interface RunsViewProps {
  initialBankImportId?: number | undefined;
  initialInternalImportId?: number | undefined;
  onViewRunResults: (runId: number) => void;
}

export function RunsView({
  initialBankImportId,
  initialInternalImportId,
  onViewRunResults,
}: RunsViewProps) {
  const { token, isAdmin, isAuthenticated, openLoginModal } = useAuth();

  const [bankImportId, setBankImportId] = useState<string>(
    initialBankImportId ? initialBankImportId.toString() : "",
  );
  const [internalImportId, setInternalImportId] = useState<string>(
    initialInternalImportId ? initialInternalImportId.toString() : "",
  );

  const [launchLoading, setLaunchLoading] = useState(false);
  const [launchError, setLaunchError] = useState<string | null>(null);
  // The dispatched run (starts RUNNING, updates to COMPLETED/FAILED via poll)
  const [activeRun, setActiveRun] = useState<ReconciliationRunDto | null>(null);

  const [runs, setRuns] = useState<ReconciliationRunDto[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalRuns, setTotalRuns] = useState(0);
  const [runsLoading, setRunsLoading] = useState(false);

  const [csvDownloadingId, setCsvDownloadingId] = useState<number | null>(null);

  // Ref for polling interval so we can clear it on unmount or completion
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (pollRef.current !== null) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  // Cleanup on unmount
  useEffect(() => () => stopPolling(), [stopPolling]);

  const loadRuns = useCallback(async (pageNum = 0) => {
    if (!token) return;
    setRunsLoading(true);
    try {
      const res = await listReconciliationRunsApi(pageNum, 10, token);
      setRuns(res.content);
      setPage(res.number);
      setTotalPages(res.totalPages || 1);
      setTotalRuns(res.totalElements);
    } catch {
      setRuns([]);
    } finally {
      setRunsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    if (token) {
      void loadRuns(0);
    }
  }, [token, loadRuns]);

  // Sync if initial ids change from parent
  useEffect(() => {
    if (initialBankImportId) setBankImportId(initialBankImportId.toString());
    if (initialInternalImportId) setInternalImportId(initialInternalImportId.toString());
  }, [initialBankImportId, initialInternalImportId]);

  /**
   * Poll the active run's status every 4 seconds until COMPLETED or FAILED,
   * then reload the full runs list.
   */
  const startPollingRun = useCallback((runId: number) => {
    stopPolling();
    pollRef.current = setInterval(async () => {
      if (!token) return;
      try {
        const updated = await getReconciliationRunApi(runId, token);
        setActiveRun(updated);
        if (updated.status === "COMPLETED" || updated.status === "FAILED") {
          stopPolling();
          // Reload full table so the new run shows at the top with final status
          await loadRuns(0);
        }
      } catch {
        // Ignore transient network errors during polling
      }
    }, 4000);
  }, [token, stopPolling, loadRuns]);

  const handleLaunchRun = async (e: React.FormEvent) => {
    e.preventDefault();
    const bId = parseInt(bankImportId, 10);
    const iId = parseInt(internalImportId, 10);
    if (isNaN(bId) || isNaN(iId) || bId <= 0 || iId <= 0) {
      setLaunchError("Please enter valid positive Bank and Internal Import IDs.");
      return;
    }

    setLaunchLoading(true);
    setLaunchError(null);
    setActiveRun(null);
    stopPolling();

    try {
      // Returns 202 immediately with run DTO (status = RUNNING)
      const run = await createReconciliationRunApi(bId, iId, token);
      setActiveRun(run);
      setLaunchLoading(false);
      // Refresh table immediately so the new RUNNING row is visible
      await loadRuns(0);
      // Begin polling for completion
      startPollingRun(run.id);
    } catch (err: unknown) {
      setLaunchError(err instanceof Error ? err.message : "Failed to execute reconciliation run");
      setLaunchLoading(false);
    }
  };

  const handleDownloadCsv = async (runId: number) => {
    setCsvDownloadingId(runId);
    try {
      const blob = await downloadReportCsvApi(runId, token);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `reconciliation-run-${runId}-report.csv`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Failed to download report");
    } finally {
      setCsvDownloadingId(null);
    }
  };

  if (!isAuthenticated) {
    return (
      <div className="space-y-6 max-w-3xl mx-auto">
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-8 shadow-xs text-center space-y-4">
          <div className="h-10 w-10 rounded-xl bg-slate-800 border border-slate-700 mx-auto flex items-center justify-center text-slate-300">
            <PlayCircle className="h-5 w-5" />
          </div>
          <div className="space-y-1.5">
            <h2 className="text-base font-semibold text-white">
              Sign In to View Reconciliation Runs
            </h2>
            <p className="text-xs text-slate-400 max-w-md mx-auto leading-relaxed">
              Execution history and multi-pass matching runs require an authenticated session.
              Sign in with your credentials to view historical runs or execute a new match.
            </p>
          </div>
          <Button
            onClick={openLoginModal}
            className="h-9 px-6 text-xs font-semibold gap-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-md shadow-sm"
          >
            <LogIn className="h-3.5 w-3.5" />
            <span>Sign In to ReconcileX</span>
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 p-5 rounded-xl border border-slate-800 bg-slate-900/50 shadow-xs">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5 flex-wrap">
            <h1 className="text-lg font-semibold tracking-tight text-white">
              Reconciliation Matching Runs
            </h1>
            <span
              className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-mono font-medium uppercase ${
                isAdmin
                  ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/20"
                  : "bg-slate-800 text-slate-300 border-slate-700"
              }`}
            >
              {isAdmin ? "ADMIN" : "VIEWER"}
            </span>
          </div>
          <p className="text-xs text-slate-400 max-w-2xl leading-relaxed">
            Execute deterministic multi-pass reconciliation runs and inspect historical matching records across financial periods.
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => void loadRuns(page)}
          disabled={runsLoading}
          className="h-8 text-xs font-medium gap-1.5 border-slate-800 bg-slate-900 text-slate-300 hover:text-white"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${runsLoading ? "animate-spin" : ""}`} />
          <span>Refresh Runs</span>
        </Button>
      </div>

      {/* Trigger Run Section (ADMIN ONLY) */}
      {isAdmin && (
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <div className="flex items-center gap-2">
              <div className="h-7 w-7 rounded-lg bg-slate-800 flex items-center justify-center text-slate-300">
                <PlayCircle className="h-4 w-4" />
              </div>
              <div>
                <h2 className="text-sm font-semibold text-white">Trigger Multi-Pass Matching Run</h2>
                <span className="text-[11px] text-slate-400">Sequential deterministic rules pipeline</span>
              </div>
            </div>
            <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded border border-slate-800 bg-slate-950/60 text-slate-400">
              ADMIN Action
            </span>
          </div>

          <form onSubmit={(e) => void handleLaunchRun(e)} className="grid grid-cols-1 sm:grid-cols-12 gap-3 items-end">
            <div className="sm:col-span-4 space-y-1.5">
              <Label htmlFor="bank-import-id" className="text-xs font-medium text-slate-300">
                Bank Import ID
              </Label>
              <Input
                id="bank-import-id"
                type="number"
                min="1"
                placeholder="e.g. 1"
                value={bankImportId}
                onChange={(e) => setBankImportId(e.target.value)}
                disabled={launchLoading || activeRun?.status === "RUNNING"}
                className="h-8 font-mono text-xs bg-slate-950/60 border-slate-800 text-white"
                required
              />
            </div>

            <div className="sm:col-span-4 space-y-1.5">
              <Label htmlFor="internal-import-id" className="text-xs font-medium text-slate-300">
                Internal Import ID
              </Label>
              <Input
                id="internal-import-id"
                type="number"
                min="1"
                placeholder="e.g. 2"
                value={internalImportId}
                onChange={(e) => setInternalImportId(e.target.value)}
                disabled={launchLoading || activeRun?.status === "RUNNING"}
                className="h-8 font-mono text-xs bg-slate-950/60 border-slate-800 text-white"
                required
              />
            </div>

            <div className="sm:col-span-4">
              <Button
                type="submit"
                disabled={launchLoading || activeRun?.status === "RUNNING"}
                className="w-full h-8 text-xs font-semibold gap-1.5 bg-indigo-600 hover:bg-indigo-500 text-white shadow-xs"
              >
                {launchLoading ? (
                  <>
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    <span>Dispatching...</span>
                  </>
                ) : activeRun?.status === "RUNNING" ? (
                  <>
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    <span>Matching #{activeRun.id}…</span>
                  </>
                ) : (
                  <>
                    <PlayCircle className="h-3.5 w-3.5" />
                    <span>Execute Matching</span>
                  </>
                )}
              </Button>
            </div>
          </form>

          {launchError && (
            <div className="flex items-center gap-2 p-3 text-xs rounded-lg bg-rose-500/10 text-rose-400 border border-rose-500/20">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>{launchError}</span>
            </div>
          )}

          {/* Active run status banner */}
          {activeRun && (
            <div
              className={`flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-3.5 rounded-lg border text-xs ${
                activeRun.status === "COMPLETED"
                  ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                  : activeRun.status === "FAILED"
                    ? "border-rose-500/30 bg-rose-500/10 text-rose-300"
                    : "border-indigo-500/30 bg-indigo-500/10 text-indigo-300"
              }`}
            >
              <div className="flex items-center gap-2">
                {activeRun.status === "COMPLETED" ? (
                  <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
                ) : activeRun.status === "FAILED" ? (
                  <AlertCircle className="h-4 w-4 text-rose-400 shrink-0" />
                ) : (
                  <Loader2 className="h-4 w-4 text-indigo-400 shrink-0 animate-spin" />
                )}
                <span>
                  {activeRun.status === "RUNNING"
                    ? `Run #${activeRun.id} is matching ${bankImportId ? `Bank #${bankImportId}` : ""} + ${internalImportId ? `Internal #${internalImportId}` : ""}… Results update automatically.`
                    : activeRun.status === "COMPLETED"
                      ? `Run #${activeRun.id} completed successfully.`
                      : `Run #${activeRun.id} failed. Check logs or try again.`}
                </span>
              </div>
              {activeRun.status === "COMPLETED" && (
                <Button
                  size="sm"
                  onClick={() => onViewRunResults(activeRun.id)}
                  className="h-7 text-xs bg-emerald-600 hover:bg-emerald-500 text-white gap-1 shrink-0"
                >
                  <Eye className="h-3 w-3" />
                  <span>Inspect Results</span>
                </Button>
              )}
            </div>
          )}
        </div>
      )}

      {/* Runs Table */}
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 shadow-xs overflow-hidden">
        <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/40">
          <div>
            <h2 className="text-sm font-semibold text-white">
              All Reconciliation Runs ({totalRuns})
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Chronological ledger of executed batch runs
            </p>
          </div>
        </div>

        <div>
          {runsLoading ? (
            <div className="p-8 text-center text-xs text-slate-400 font-mono">
              Loading reconciliation runs...
            </div>
          ) : runs.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-400">
              No reconciliation runs found.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-800 bg-slate-950/50 text-slate-400 font-mono text-[11px]">
                    <th className="py-2.5 px-3 font-semibold">RUN ID</th>
                    <th className="py-2.5 px-3 font-semibold">DATA SOURCES</th>
                    <th className="py-2.5 px-3 font-semibold">STATUS</th>
                    <th className="py-2.5 px-3 font-semibold">STARTED AT</th>
                    <th className="py-2.5 px-3 font-semibold">COMPLETED AT</th>
                    <th className="py-2.5 px-3 font-semibold text-right">ACTIONS</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-mono text-xs">
                  {runs.map((run) => (
                    <tr key={run.id} className="hover:bg-slate-850/40 transition-colors">
                      <td className="py-3 px-3 font-semibold text-white">
                        #{run.id}
                      </td>
                      <td className="py-3 px-3 text-slate-300">
                        Bank <span className="text-white font-medium">#{run.bankImportId}</span> + Internal <span className="text-white font-medium">#{run.internalImportId}</span>
                      </td>
                      <td className="py-3 px-3">
                        {run.status === "COMPLETED" ? (
                          <span className="inline-flex items-center gap-1 text-[10px] font-medium text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-1.5 py-0.5 rounded">
                            <CheckCircle2 className="h-3 w-3" />
                            COMPLETED
                          </span>
                        ) : run.status === "RUNNING" ? (
                          <span className="inline-flex items-center gap-1 text-[10px] font-medium text-indigo-400 bg-indigo-500/10 border border-indigo-500/20 px-1.5 py-0.5 rounded">
                            <RefreshCw className="h-3 w-3 animate-spin" />
                            RUNNING
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-[10px] font-medium text-rose-400 bg-rose-500/10 border border-rose-500/20 px-1.5 py-0.5 rounded">
                            <AlertCircle className="h-3 w-3" />
                            FAILED
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-3 text-slate-400 tabular-nums text-[11px]">
                        {new Date(run.startedAt).toLocaleString(undefined, {
                          month: "short",
                          day: "2-digit",
                          hour: "2-digit",
                          minute: "2-digit",
                        })}
                      </td>
                      <td className="py-3 px-3 text-slate-400 tabular-nums text-[11px]">
                        {run.completedAt
                          ? new Date(run.completedAt).toLocaleString(undefined, {
                              month: "short",
                              day: "2-digit",
                              hour: "2-digit",
                              minute: "2-digit",
                            })
                          : "—"}
                      </td>
                      <td className="py-3 px-3 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-7 px-2 text-xs text-indigo-400 hover:text-indigo-300 gap-1"
                            onClick={() => onViewRunResults(run.id)}
                          >
                            <Eye className="h-3.5 w-3.5" />
                            <span>Results</span>
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={csvDownloadingId === run.id}
                            className="h-7 px-2 text-xs text-slate-400 hover:text-white gap-1"
                            onClick={() => void handleDownloadCsv(run.id)}
                            title="Download official CSV report"
                          >
                            <Download
                              className={`h-3.5 w-3.5 ${
                                csvDownloadingId === run.id ? "animate-bounce" : ""
                              }`}
                            />
                            <span>CSV</span>
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between p-3 border-t border-slate-800 text-xs text-slate-400 font-mono bg-slate-950/40">
              <span>
                Page {page + 1} of {totalPages} ({totalRuns} runs)
              </span>
              <div className="flex items-center gap-1.5">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0 || runsLoading}
                  onClick={() => void loadRuns(page - 1)}
                  className="h-7 w-7 p-0 bg-slate-900 border-slate-800 text-white"
                >
                  <ChevronLeft className="h-3.5 w-3.5" />
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1 || runsLoading}
                  onClick={() => void loadRuns(page + 1)}
                  className="h-7 w-7 p-0 bg-slate-900 border-slate-800 text-white"
                >
                  <ChevronRight className="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
