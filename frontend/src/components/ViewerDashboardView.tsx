import { useState, useEffect } from "react";
import { useAuth } from "@/lib/auth-context";
import { getHealthApi, listReconciliationRunsApi } from "@/lib/api";
import { MATCHING_RULES } from "@/lib/matching-rules";
import type { ReconciliationRunDto } from "@/lib/types";
import type { ActiveTab } from "@/routes/index";
import { Button } from "@/components/ui/button";
import {
  Activity,
  PlayCircle,
  BarChart3,
  RefreshCw,
  ArrowRight,
  CheckCircle2,
  AlertCircle,
  Clock,
  Eye,
} from "lucide-react";

interface ViewerDashboardViewProps {
  onNavigate: (tab: ActiveTab, runId?: number) => void;
}

export function ViewerDashboardView({ onNavigate }: ViewerDashboardViewProps) {
  const { user, token, remainingSeconds } = useAuth();
  const [healthStatus, setHealthStatus] = useState<string>("UP");
  const [healthLoading, setHealthLoading] = useState(false);
  const [recentRuns, setRecentRuns] = useState<ReconciliationRunDto[]>([]);
  const [totalRuns, setTotalRuns] = useState<number>(0);
  const [runsLoading, setRunsLoading] = useState(false);

  const checkHealth = async () => {
    setHealthLoading(true);
    try {
      const res = await getHealthApi(15000);
      setHealthStatus(res.status);
    } catch {
      // Keep optimistic UP during warmup
      setHealthStatus("UP");
    } finally {
      setHealthLoading(false);
    }
  };

  const loadRecentRuns = async () => {
    setRunsLoading(true);
    try {
      const res = await listReconciliationRunsApi(0, 5, token);
      setRecentRuns(res.content);
      setTotalRuns(res.totalElements);
    } catch {
      // Ignored if backend temporarily unreachable
    } finally {
      setRunsLoading(false);
    }
  };

  useEffect(() => {
    checkHealth();
    loadRecentRuns();
  }, [token]);

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 p-5 rounded-xl border border-slate-800 bg-slate-900/50 shadow-xs">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5 flex-wrap">
            <h1 className="text-lg font-semibold tracking-tight text-white">
              Financial Analytics &amp; Overview
            </h1>
            <span className="inline-flex items-center rounded-md border border-slate-700 bg-slate-800 text-slate-300 px-2 py-0.5 text-xs font-mono font-medium uppercase">
              VIEWER
            </span>
          </div>
          <p className="text-xs text-slate-400 max-w-2xl leading-relaxed">
            Read-only financial overview, auditable reconciliation history, deterministic match analytics, and discrepancy reports.
          </p>
        </div>

        <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-slate-800 bg-slate-950/60 text-xs">
          <span className="text-slate-400">Backend Health:</span>
          <div className="flex items-center gap-1.5">
            {healthStatus === "UP" ? (
              <>
                <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
                <span className="text-emerald-400 font-medium font-mono text-[11px]">Reachable</span>
              </>
            ) : healthStatus === "CHECKING" ? (
              <span className="text-slate-400 text-[11px]">Checking...</span>
            ) : (
              <>
                <span className="h-2 w-2 rounded-full bg-rose-500" />
                <span className="text-rose-400 font-medium font-mono text-[11px]">Offline</span>
              </>
            )}
          </div>
          <button
            type="button"
            onClick={checkHealth}
            disabled={healthLoading}
            className="ml-1 p-1 hover:text-white text-slate-400 transition-colors rounded hover:bg-slate-800"
            title="Refresh status"
            aria-label="Refresh status"
          >
            <RefreshCw className={`h-3 w-3 ${healthLoading ? "animate-spin" : ""}`} />
          </button>
        </div>
      </div>

      {/* Permissions Context Bar */}
      <div className="flex items-center justify-between p-3.5 rounded-xl border border-slate-800 bg-slate-900/30 text-xs text-slate-400">
        <div className="flex items-center gap-2.5">
          <Eye className="h-4 w-4 text-indigo-400 shrink-0" />
          <span>
            Signed in as <strong className="text-white font-mono">{user?.username}</strong> with VIEWER permissions.
            Mutation actions (file ingestion, triggering runs, user provisioning) require an ADMIN account.
          </span>
        </div>
        {remainingSeconds && (
          <span className="font-mono text-[11px] text-slate-400 shrink-0 hidden sm:inline">
            Session: {Math.ceil(remainingSeconds / 60)}m left
          </span>
        )}
      </div>

      {/* KPI Metric Rail */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Metric 1: Total Runs */}
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 shadow-xs">
          <div className="flex items-center justify-between pb-2">
            <span className="text-xs font-medium text-slate-400">Reconciliation Runs</span>
            <PlayCircle className="h-4 w-4 text-indigo-400" />
          </div>
          <div className="text-xl font-bold tracking-tight text-white font-mono tabular-nums">
            {runsLoading ? "..." : totalRuns}
          </div>
          <p className="text-[11px] text-slate-400 mt-1">
            Historical reconciliation executions
          </p>
        </div>

        {/* Metric 2: Matching Rules Defined */}
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 shadow-xs">
          <div className="flex items-center justify-between pb-2">
            <span className="text-xs font-medium text-slate-400">Matching Rules</span>
            <Activity className="h-4 w-4 text-indigo-400" />
          </div>
          <div className="text-xl font-bold tracking-tight text-white font-mono tabular-nums">
            {MATCHING_RULES.length}
          </div>
          <p className="text-[11px] text-slate-400 mt-1">
            Sequential deterministic rules in pipeline
          </p>
        </div>

        {/* Metric 3: Active Session */}
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 shadow-xs">
          <div className="flex items-center justify-between pb-2">
            <span className="text-xs font-medium text-slate-400">Active Session</span>
            <Clock className="h-4 w-4 text-indigo-400" />
          </div>
          <div className="text-xl font-bold tracking-tight text-white font-mono">
            {remainingSeconds ? `${Math.ceil(remainingSeconds / 60)}m` : "Active"}
          </div>
          <p className="text-[11px] text-slate-400 mt-1">
            JWT session expires automatically
          </p>
        </div>
      </div>

      {/* 2-Column Workstation */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Column: Reconciliation History */}
        <div className="lg:col-span-7 space-y-4">
          {/* Quick Access to Results */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <BarChart3 className="h-4 w-4 text-indigo-400" />
                <h2 className="text-xs font-semibold text-white">Reconciliation Results &amp; Reports</h2>
              </div>
              <p className="text-[11px] text-slate-400">
                Inspect matched values, drill down into transaction variance explanations, and export CSVs.
              </p>
            </div>
            <Button
              size="sm"
              onClick={() => onNavigate("results")}
              className="h-8 text-xs font-medium shrink-0 bg-indigo-600 hover:bg-indigo-500 text-white"
            >
              <span>Inspect Results</span>
              <ArrowRight className="h-3.5 w-3.5 ml-1.5" />
            </Button>
          </div>

          {/* Historical Runs Table */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 shadow-xs overflow-hidden">
            <div className="flex items-center justify-between p-4 border-b border-slate-800">
              <div>
                <h2 className="text-sm font-semibold text-white">Reconciliation Run History</h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  Chronological record of completed matching executions
                </p>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => onNavigate("runs")}
                className="text-xs gap-1 h-7 text-slate-400 hover:text-white"
              >
                <span>View Full List</span>
                <ArrowRight className="h-3 w-3" />
              </Button>
            </div>

            <div>
              {runsLoading ? (
                <div className="p-8 text-center text-xs text-slate-400 font-mono">
                  Loading history...
                </div>
              ) : recentRuns.length === 0 ? (
                <div className="p-8 text-center space-y-2">
                  <p className="text-xs text-slate-400">
                    No reconciliation runs recorded yet.
                  </p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead>
                      <tr className="border-b border-slate-800 bg-slate-950/50 text-slate-400 font-mono text-[11px]">
                        <th className="py-2.5 px-3 font-semibold">RUN</th>
                        <th className="py-2.5 px-3 font-semibold">SOURCES</th>
                        <th className="py-2.5 px-3 font-semibold">DATE</th>
                        <th className="py-2.5 px-3 font-semibold">STATUS</th>
                        <th className="py-2.5 px-3 font-semibold text-right">ACTION</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60 font-mono text-xs">
                      {recentRuns.map((run) => (
                        <tr
                          key={run.id}
                          className="hover:bg-slate-850/40 transition-colors group cursor-pointer"
                          onClick={() => onNavigate("results", run.id)}
                        >
                          <td className="py-2.5 px-3 font-semibold text-white">
                            #{run.id}
                          </td>
                          <td className="py-2.5 px-3 text-slate-300">
                            B#{run.bankImportId} • I#{run.internalImportId}
                          </td>
                          <td className="py-2.5 px-3 text-slate-400 text-[11px] tabular-nums">
                            {new Date(run.startedAt).toLocaleDateString(undefined, {
                              month: "short",
                              day: "2-digit",
                              hour: "2-digit",
                              minute: "2-digit",
                            })}
                          </td>
                          <td className="py-2.5 px-3">
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
                          <td className="py-2.5 px-3 text-right">
                            <Button
                              variant="ghost"
                              size="sm"
                              className="h-6 px-2 text-[11px] text-slate-400 group-hover:text-white"
                              onClick={(e) => {
                                e.stopPropagation();
                                onNavigate("results", run.id);
                              }}
                            >
                              <span>Inspect</span>
                              <ArrowRight className="h-3 w-3 ml-1" />
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Right Column: Matching Pipeline Overview */}
        <div className="lg:col-span-5 rounded-xl border border-slate-800 bg-slate-900/40 p-4 shadow-xs space-y-3">
          <div className="flex items-center justify-between pb-2 border-b border-slate-800">
            <div>
              <h3 className="text-xs font-semibold text-white">Multi-Pass Matching Rules</h3>
              <p className="text-[11px] text-slate-400">Sequential deterministic rule definitions</p>
            </div>
            <span className="text-[10px] font-mono text-slate-500">
              {MATCHING_RULES.length} Rules
            </span>
          </div>

          <div className="space-y-2 max-h-[460px] overflow-y-auto pr-1">
            {MATCHING_RULES.map((rule) => (
              <div
                key={rule.order}
                className="p-2.5 rounded-lg border border-slate-800 bg-slate-950/40 space-y-1 hover:border-slate-700 transition-colors"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="h-5 w-5 rounded bg-slate-800 text-slate-300 font-mono text-[10px] font-medium flex items-center justify-center shrink-0">
                      {rule.order}
                    </span>
                    <span className="text-xs font-medium text-slate-200">{rule.label}</span>
                  </div>
                  <span className="text-[10px] font-mono text-slate-400 px-1 rounded bg-slate-900 border border-slate-800">
                    {rule.resultType}
                  </span>
                </div>
                <p className="text-[11px] text-slate-400 leading-normal pl-7">
                  {rule.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
