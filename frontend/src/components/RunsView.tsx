import { useState, useEffect } from "react";
import { useAuth } from "@/lib/auth-context";
import {
  listReconciliationRunsApi,
  createReconciliationRunApi,
  downloadReportCsvApi,
} from "@/lib/api";
import type { ReconciliationRunDto, ReconciliationRunSummaryResponse } from "@/lib/types";
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  PlayCircle,
  History,
  AlertCircle,
  CheckCircle2,
  Download,
  Eye,
  RefreshCw,
  ShieldAlert,
  ChevronLeft,
  ChevronRight,
  Layers,
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
  const { token, isAdmin, isAuthenticated } = useAuth();

  const [bankImportId, setBankImportId] = useState<string>(
    initialBankImportId ? initialBankImportId.toString() : "1"
  );
  const [internalImportId, setInternalImportId] = useState<string>(
    initialInternalImportId ? initialInternalImportId.toString() : "2"
  );

  const [launchLoading, setLaunchLoading] = useState(false);
  const [launchError, setLaunchError] = useState<string | null>(null);
  const [launchSummary, setLaunchSummary] = useState<ReconciliationRunSummaryResponse | null>(null);

  const [runs, setRuns] = useState<ReconciliationRunDto[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalRuns, setTotalRuns] = useState(0);
  const [runsLoading, setRunsLoading] = useState(false);

  const [csvDownloadingId, setCsvDownloadingId] = useState<number | null>(null);

  const loadRuns = async (pageNum = 0) => {
    setRunsLoading(true);
    try {
      const res = await listReconciliationRunsApi(pageNum, 10, token);
      setRuns(res.content);
      setPage(res.number);
      setTotalPages(res.totalPages || 1);
      setTotalRuns(res.totalElements);
    } catch {
      // Ignored if unauthenticated
    } finally {
      setRunsLoading(false);
    }
  };

  useEffect(() => {
    loadRuns(0);
  }, [token]);

  const handleLaunchRun = async (e: React.FormEvent) => {
    e.preventDefault();
    const bId = parseInt(bankImportId, 10);
    const iId = parseInt(internalImportId, 10);
    if (isNaN(bId) || isNaN(iId)) {
      setLaunchError("Both Bank and Internal Import IDs must be valid integers.");
      return;
    }

    setLaunchLoading(true);
    setLaunchError(null);
    setLaunchSummary(null);

    try {
      const summary = await createReconciliationRunApi(bId, iId, token);
      setLaunchSummary(summary);
      await loadRuns(0);
    } catch (err: unknown) {
      setLaunchError(err instanceof Error ? err.message : "Failed to execute reconciliation run");
    } finally {
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

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">
            Reconciliation Runs
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Trigger deterministic multi-pass reconciliation runs and view historical batch executions.
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => loadRuns(page)}
          disabled={runsLoading}
          className="gap-1.5 self-start sm:self-auto"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${runsLoading ? "animate-spin" : ""}`} />
          Refresh Runs
        </Button>
      </div>

      {/* Trigger Run Section */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <PlayCircle className="h-5 w-5 text-primary" />
              <CardTitle className="text-base">Trigger New Matching Run</CardTitle>
            </div>
            <Badge variant="outline">POST /api/reconciliation/runs</Badge>
          </div>
          <CardDescription className="text-xs">
            Pairs bank statement transactions against internal ledgers across 13 deterministic business rules.
          </CardDescription>
        </CardHeader>

        <CardContent className="space-y-4">
          {!isAdmin && (
            <div className="flex items-center gap-3 p-3 rounded-lg border border-amber-500/20 bg-amber-500/10 text-amber-800 dark:text-amber-300 text-xs">
              <ShieldAlert className="h-4 w-4 shrink-0" />
              <span>
                {isAuthenticated
                  ? "Your current account has VIEWER role. Starting a reconciliation run requires an ADMIN role."
                  : "Sign in with an ADMIN account to trigger reconciliation runs."}
              </span>
            </div>
          )}

          <form onSubmit={handleLaunchRun} className="grid grid-cols-1 sm:grid-cols-3 gap-4 items-end">
            <div className="space-y-1.5">
              <Label htmlFor="bank-import-id" className="text-xs">Bank Import ID</Label>
              <Input
                id="bank-import-id"
                type="number"
                min="1"
                placeholder="e.g. 1"
                value={bankImportId}
                onChange={(e) => setBankImportId(e.target.value)}
                disabled={!isAdmin || launchLoading}
                required
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="internal-import-id" className="text-xs">Internal Import ID</Label>
              <Input
                id="internal-import-id"
                type="number"
                min="1"
                placeholder="e.g. 2"
                value={internalImportId}
                onChange={(e) => setInternalImportId(e.target.value)}
                disabled={!isAdmin || launchLoading}
                required
              />
            </div>

            <Button
              type="submit"
              disabled={!isAdmin || launchLoading}
              className="gap-2 w-full"
            >
              <PlayCircle className="h-4 w-4" />
              {launchLoading ? "Running Matching Rules..." : "Execute Matching"}
            </Button>
          </form>

          {launchError && (
            <div className="flex items-start gap-2 p-3 text-xs rounded-md bg-destructive/10 text-destructive border border-destructive/20">
              <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
              <span>{launchError}</span>
            </div>
          )}

          {launchSummary && (
            <div className="p-4 rounded-xl border border-emerald-500/30 bg-emerald-500/5 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
                  <span className="font-semibold text-sm">
                    Reconciliation Run #{launchSummary.runId} Completed Successfully
                  </span>
                </div>
                <Button
                  size="sm"
                  onClick={() => onViewRunResults(launchSummary.runId)}
                  className="h-8 gap-1 text-xs"
                >
                  <Eye className="h-3.5 w-3.5" />
                  Explore Results
                </Button>
              </div>

              <div className="flex flex-wrap gap-2 text-xs">
                <Badge variant="outline" className="bg-background">
                  Total Results: {launchSummary.totalResults}
                </Badge>
                {launchSummary.resultsByRule &&
                  Object.entries(launchSummary.resultsByRule).map(([rule, count]) => (
                    <Badge key={rule} variant="secondary" className="text-[11px]">
                      {rule}: {count}
                    </Badge>
                  ))}
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Historical Runs Table */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <History className="h-5 w-5 text-muted-foreground" />
              <CardTitle className="text-base">All Reconciliation Runs ({totalRuns})</CardTitle>
            </div>
            <Badge variant="outline">GET /api/reconciliation/runs</Badge>
          </div>
          <CardDescription className="text-xs">
            Chronological record of completed and active reconciliation runs
          </CardDescription>
        </CardHeader>

        <CardContent>
          {runs.length === 0 ? (
            <div className="text-center py-10 text-sm text-muted-foreground">
              {isAuthenticated ? (
                <span>No reconciliation runs found. Trigger a run above.</span>
              ) : (
                <span>Please sign in to view the historical list of runs.</span>
              )}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-border bg-muted/40 font-semibold text-muted-foreground">
                    <th className="py-2.5 px-3">Run ID</th>
                    <th className="py-2.5 px-3">Bank Import</th>
                    <th className="py-2.5 px-3">Internal Import</th>
                    <th className="py-2.5 px-3">Status</th>
                    <th className="py-2.5 px-3">Started At</th>
                    <th className="py-2.5 px-3">Completed At</th>
                    <th className="py-2.5 px-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {runs.map((r) => (
                    <tr key={r.id} className="hover:bg-muted/30 transition-colors">
                      <td className="py-3 px-3 font-semibold">#{r.id}</td>
                      <td className="py-3 px-3">Import #{r.bankImportId}</td>
                      <td className="py-3 px-3">Import #{r.internalImportId}</td>
                      <td className="py-3 px-3">
                        <Badge
                          variant={
                            r.status === "COMPLETED"
                              ? "default"
                              : r.status === "FAILED"
                              ? "destructive"
                              : "secondary"
                          }
                          className="text-[10px]"
                        >
                          {r.status}
                        </Badge>
                      </td>
                      <td className="py-3 px-3 text-muted-foreground">
                        {new Date(r.startedAt).toLocaleString()}
                      </td>
                      <td className="py-3 px-3 text-muted-foreground">
                        {r.completedAt ? new Date(r.completedAt).toLocaleString() : "—"}
                      </td>
                      <td className="py-3 px-3 text-right space-x-2">
                        <Button
                          variant="outline"
                          size="sm"
                          className="h-7 text-xs gap-1"
                          onClick={() => onViewRunResults(r.id)}
                        >
                          <Eye className="h-3 w-3" />
                          Results
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-7 text-xs gap-1"
                          disabled={csvDownloadingId === r.id}
                          onClick={() => handleDownloadCsv(r.id)}
                          title="Download streaming report.csv"
                        >
                          <Download className="h-3 w-3" />
                          {csvDownloadingId === r.id ? "Downloading..." : "CSV"}
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {totalPages > 1 && (
            <div className="flex items-center justify-between pt-4 border-t border-border mt-4 text-xs text-muted-foreground">
              <span>
                Page {page + 1} of {totalPages}
              </span>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0 || runsLoading}
                  onClick={() => loadRuns(page - 1)}
                  className="h-8 w-8 p-0"
                >
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1 || runsLoading}
                  onClick={() => loadRuns(page + 1)}
                  className="h-8 w-8 p-0"
                >
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
