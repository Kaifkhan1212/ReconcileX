import { useState, useEffect } from "react";
import { useAuth } from "@/lib/auth-context";
import { getHealthApi, listReconciliationRunsApi } from "@/lib/api";
import type { ReconciliationRunDto } from "@/lib/types";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Activity,
  Shield,
  FileSpreadsheet,
  PlayCircle,
  BarChart3,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  ArrowRight,
  Database,
  Cpu,
} from "lucide-react";

interface DashboardViewProps {
  onNavigate: (tab: "imports" | "runs" | "results" | "audit", runId?: number) => void;
}

export function DashboardView({ onNavigate }: DashboardViewProps) {
  const { user, isAuthenticated, isAdmin } = useAuth();
  const [healthStatus, setHealthStatus] = useState<string>("CHECKING");
  const [healthLoading, setHealthLoading] = useState(false);
  const [recentRuns, setRecentRuns] = useState<ReconciliationRunDto[]>([]);
  const [totalRuns, setTotalRuns] = useState<number>(0);
  const [runsLoading, setRunsLoading] = useState(false);

  const checkHealth = async () => {
    setHealthLoading(true);
    try {
      const res = await getHealthApi();
      setHealthStatus(res.status);
    } catch {
      setHealthStatus("DOWN");
    } finally {
      setHealthLoading(false);
    }
  };

  const loadRecentRuns = async () => {
    setRunsLoading(true);
    try {
      const res = await listReconciliationRunsApi(0, 5);
      setRecentRuns(res.content);
      setTotalRuns(res.totalElements);
    } catch {
      // Runs API requires auth
    } finally {
      setRunsLoading(false);
    }
  };

  useEffect(() => {
    checkHealth();
    loadRecentRuns();
  }, [isAuthenticated]);

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 p-6 rounded-xl border border-border bg-card shadow-sm">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              ReconcileX Payment Studio
            </h1>
            <Badge variant="outline" className="text-xs">
              v1.0.0
            </Badge>
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            Deterministic multi-pass matching, idempotent ingestion, auditable decision logging, and role-based security.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-border bg-secondary/40 text-xs">
            <span className="text-muted-foreground">Backend Health:</span>
            <div className="flex items-center gap-1.5 font-medium">
              {healthStatus === "UP" ? (
                <>
                  <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
                  <span className="text-emerald-700 dark:text-emerald-400 font-semibold">UP (Port 8081)</span>
                </>
              ) : healthStatus === "CHECKING" ? (
                <span className="text-muted-foreground">Checking...</span>
              ) : (
                <>
                  <span className="h-2 w-2 rounded-full bg-destructive" />
                  <span className="text-destructive font-semibold">Offline</span>
                </>
              )}
            </div>
            <button
              onClick={checkHealth}
              disabled={healthLoading}
              className="ml-1 p-0.5 hover:text-foreground text-muted-foreground transition-colors"
              title="Refresh health"
            >
              <RefreshCw className={`h-3 w-3 ${healthLoading ? "animate-spin" : ""}`} />
            </button>
          </div>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium text-muted-foreground uppercase">
              Engine Status
            </CardTitle>
            <Activity className="h-4 w-4 text-emerald-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-foreground">
              {healthStatus === "UP" ? "Operational" : "Unavailable"}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              Deterministic matching active
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium text-muted-foreground uppercase">
              Reconciliation Runs
            </CardTitle>
            <PlayCircle className="h-4 w-4 text-primary" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-foreground">
              {isAuthenticated ? totalRuns : "—"}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {isAuthenticated ? "Recorded in PostgreSQL" : "Sign in to view total"}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium text-muted-foreground uppercase">
              Active Security
            </CardTitle>
            <Shield className="h-4 w-4 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-foreground">
              {user ? user.role : "Guest"}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {user ? `User: ${user.username} (In-Memory)` : "Read-only access"}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-medium text-muted-foreground uppercase">
              Database Engine
            </CardTitle>
            <Database className="h-4 w-4 text-indigo-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-foreground">PostgreSQL 16</div>
            <p className="text-xs text-muted-foreground mt-1">
              Port 5433 • Flyway V1-V5
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Workflow Navigation Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card className="hover:border-primary/50 transition-colors">
          <CardHeader>
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary mb-2">
              <FileSpreadsheet className="h-5 w-5" />
            </div>
            <CardTitle className="text-base font-semibold">1. Ingest Data Files</CardTitle>
            <CardDescription className="text-xs">
              Upload bank statements and internal transaction CSVs. Idempotency guarantees safe re-runs.
            </CardDescription>
          </CardHeader>
          <CardContent className="pt-0">
            <Button
              variant="outline"
              size="sm"
              className="w-full justify-between"
              onClick={() => onNavigate("imports")}
            >
              <span>Go to File Imports</span>
              <ArrowRight className="h-4 w-4" />
            </Button>
          </CardContent>
        </Card>

        <Card className="hover:border-primary/50 transition-colors">
          <CardHeader>
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary mb-2">
              <PlayCircle className="h-5 w-5" />
            </div>
            <CardTitle className="text-base font-semibold">2. Multi-Pass Matching</CardTitle>
            <CardDescription className="text-xs">
              Execute deterministic multi-pass rules: exact, amount tolerance, batch settlement & refund.
            </CardDescription>
          </CardHeader>
          <CardContent className="pt-0">
            <Button
              variant="outline"
              size="sm"
              className="w-full justify-between"
              onClick={() => onNavigate("runs")}
            >
              <span>View & Launch Runs</span>
              <ArrowRight className="h-4 w-4" />
            </Button>
          </CardContent>
        </Card>

        <Card className="hover:border-primary/50 transition-colors">
          <CardHeader>
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary mb-2">
              <BarChart3 className="h-5 w-5" />
            </div>
            <CardTitle className="text-base font-semibold">3. Results & Reports</CardTitle>
            <CardDescription className="text-xs">
              Review financial summary, inspect discrepancy breakdowns, and stream high-volume CSV reports.
            </CardDescription>
          </CardHeader>
          <CardContent className="pt-0">
            <Button
              variant="outline"
              size="sm"
              className="w-full justify-between"
              onClick={() => onNavigate("results")}
            >
              <span>Inspect Run Results</span>
              <ArrowRight className="h-4 w-4" />
            </Button>
          </CardContent>
        </Card>
      </div>

      {/* Recent Runs Table */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle className="text-base font-semibold">Recent Reconciliation Runs</CardTitle>
            <CardDescription className="text-xs">
              Audit-logged matching executions
            </CardDescription>
          </div>
          <Button variant="ghost" size="sm" onClick={() => onNavigate("runs")} className="text-xs gap-1">
            <span>View All</span>
            <ArrowRight className="h-3.5 w-3.5" />
          </Button>
        </CardHeader>
        <CardContent>
          {recentRuns.length === 0 ? (
            <div className="text-center py-8 text-sm text-muted-foreground">
              {isAuthenticated ? (
                <span>No reconciliation runs found. Ingest files to trigger your first run.</span>
              ) : (
                <span>Please sign in with credentials to view recorded reconciliation runs.</span>
              )}
            </div>
          ) : (
            <div className="divide-y divide-border">
              {recentRuns.map((run) => (
                <div
                  key={run.id}
                  className="flex items-center justify-between py-3 hover:bg-secondary/30 px-2 rounded-md transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <Badge variant={run.status === "COMPLETED" ? "default" : "secondary"}>
                      Run #{run.id}
                    </Badge>
                    <div className="text-xs text-muted-foreground">
                      Bank #{run.bankImportId} • Internal #{run.internalImportId}
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-muted-foreground hidden sm:inline">
                      {new Date(run.startedAt).toLocaleString()}
                    </span>
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 text-xs"
                      onClick={() => onNavigate("results", run.id)}
                    >
                      View Results
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
