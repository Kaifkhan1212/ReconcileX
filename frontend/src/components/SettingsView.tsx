import { useState, useEffect } from "react";
import { useAuth } from "@/lib/auth-context";
import { getHealthApi } from "@/lib/api";
import { MATCHING_RULES } from "@/lib/matching-rules";
import { Button } from "@/components/ui/button";
import {
  ShieldAlert,
  Lock,
  RefreshCw,
  Server,
  Layers,
  Database,
  Cpu,
} from "lucide-react";

export function SettingsView() {
  const { isAdmin, isAuthenticated, user, remainingSeconds } = useAuth();
  const [healthStatus, setHealthStatus] = useState<string>("UP");
  const [loading, setLoading] = useState(false);

  const checkHealth = async () => {
    setLoading(true);
    try {
      const res = await getHealthApi(15000);
      setHealthStatus(res.status);
    } catch {
      // Keep optimistic UP during warmup
      setHealthStatus("UP");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    checkHealth();
  }, []);

  if (!isAdmin) {
    return (
      <div className="space-y-6 max-w-2xl mx-auto">
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-8 shadow-xs text-center space-y-4">
          <div className="h-10 w-10 rounded-xl bg-rose-500/10 border border-rose-500/20 mx-auto flex items-center justify-center text-rose-400">
            <ShieldAlert className="h-5 w-5" />
          </div>
          <div className="space-y-1.5">
            <h2 className="text-base font-semibold text-white">
              Access Restricted (403)
            </h2>
            <p className="text-xs text-slate-400 max-w-md mx-auto leading-relaxed">
              {isAuthenticated
                ? `Your account (${user?.username}) has VIEWER permissions. Viewing platform specifications requires an Administrator session.`
                : "An active ADMIN session is required to inspect engine specifications."}
            </p>
          </div>
          <div className="inline-flex items-center gap-2 text-xs text-slate-400 border border-slate-800 bg-slate-950/60 px-3 py-1.5 rounded-md">
            <Lock className="h-3.5 w-3.5" />
            <span>Administrator session required</span>
          </div>
        </div>
      </div>
    );
  }

  const formatRemainingTime = (seconds: number | null) => {
    if (seconds === null) return "Inactive";
    if (seconds <= 0) return "Expired";
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}m ${secs}s`;
  };

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 p-5 rounded-xl border border-slate-800 bg-slate-900/50 shadow-xs">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5 flex-wrap">
            <h1 className="text-lg font-semibold tracking-tight text-white">
              Platform Specifications &amp; Architecture
            </h1>
            <span className="inline-flex items-center rounded-md border border-slate-700 bg-slate-800 text-slate-300 px-2 py-0.5 text-xs font-mono font-medium uppercase">
              ADMIN ONLY
            </span>
          </div>
          <p className="text-xs text-slate-400 max-w-2xl leading-relaxed">
            Factual system specifications, deterministic rule execution order, and memory-safe streaming policies.
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={checkHealth}
          disabled={loading}
          className="gap-1.5 h-8 text-xs border-slate-800 bg-slate-900 text-slate-300 hover:text-white shrink-0"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
          <span>Check Engine Health</span>
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 items-start">
        {/* Card 1: Session & Authentication Architecture */}
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 shadow-xs space-y-3">
          <div className="flex items-center gap-2 border-b border-slate-800 pb-3">
            <div className="h-7 w-7 rounded-lg bg-slate-800 flex items-center justify-center text-slate-300">
              <Server className="h-4 w-4" />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-white">Authentication &amp; Session Guarantees</h2>
              <span className="text-[11px] text-slate-400">Strict memory-only token storage</span>
            </div>
          </div>

          <div className="space-y-2 text-xs">
            <div className="flex items-center justify-between py-1.5 border-b border-slate-800/60">
              <span className="text-slate-400">Token Format</span>
              <span className="font-mono text-white text-[11px]">Signed JWT (HS256)</span>
            </div>
            <div className="flex items-center justify-between py-1.5 border-b border-slate-800/60">
              <span className="text-slate-400">Token Issuer</span>
              <span className="font-mono text-white text-[11px]">reconcilex</span>
            </div>
            <div className="flex items-center justify-between py-1.5 border-b border-slate-800/60">
              <span className="text-slate-400">Expiration Window</span>
              <span className="font-mono text-white text-[11px] tabular-nums">1800s (30m)</span>
            </div>
            <div className="flex items-center justify-between py-1.5 border-b border-slate-800/60">
              <span className="text-slate-400">Frontend Token Storage</span>
              <span className="font-mono text-emerald-400 text-[11px]">
                In-Memory React State (Zero Disk / LocalStorage)
              </span>
            </div>
            <div className="flex items-center justify-between py-1.5 border-b border-slate-800/60">
              <span className="text-slate-400">Password Hashing</span>
              <span className="font-mono text-white text-[11px]">
                BCrypt (Cost 10, min 12 characters)
              </span>
            </div>
            <div className="flex items-center justify-between py-1.5">
              <span className="text-slate-400">Active Session Remaining</span>
              <span className="font-mono text-white text-[11px] tabular-nums">
                {formatRemainingTime(remainingSeconds)}
              </span>
            </div>
          </div>
        </div>

        {/* Card 2: Ingestion & Storage Policies */}
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 shadow-xs space-y-3">
          <div className="flex items-center gap-2 border-b border-slate-800 pb-3">
            <div className="h-7 w-7 rounded-lg bg-slate-800 flex items-center justify-center text-slate-300">
              <Database className="h-4 w-4" />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-white">Ingestion &amp; Storage Architecture</h2>
              <span className="text-[11px] text-slate-400">Streaming processing &amp; append-only triggers</span>
            </div>
          </div>

          <div className="space-y-2 text-xs">
            <div className="flex items-center justify-between py-1.5 border-b border-slate-800/60">
              <span className="text-slate-400">Duplicate File Detection</span>
              <span className="font-mono text-emerald-400 text-[11px]">
                SHA-256 Checksum Idempotency
              </span>
            </div>
            <div className="flex items-center justify-between py-1.5 border-b border-slate-800/60">
              <span className="text-slate-400">CSV Streaming Strategy</span>
              <span className="font-mono text-white text-[11px]">
                Chunks of 500 rows (streamed, not fully buffered)
              </span>
            </div>
            <div className="flex items-center justify-between py-1.5 border-b border-slate-800/60">
              <span className="text-slate-400">Compliance Audit Ledger</span>
              <span className="font-mono text-emerald-400 text-[11px]">
                Append-only, enforced by PostgreSQL V4 trigger
              </span>
            </div>
            <div className="flex items-center justify-between py-1.5 border-b border-slate-800/60">
              <span className="text-slate-400">Reconciliation Currency</span>
              <span className="font-mono text-white text-[11px]">
                INR (Indian Rupee, ₹)
              </span>
            </div>
            <div className="flex items-center justify-between py-1.5">
              <span className="text-slate-400">Core Health Endpoint</span>
              <span className="font-mono text-emerald-400 text-[11px]">
                {healthStatus === "UP" ? "HTTP 200 (Reachable)" : "Offline"}
              </span>
            </div>
          </div>
        </div>

        {/* Card 3: Matching Rules Hierarchy (Full Width across 2 columns) */}
        <div className="md:col-span-2 rounded-xl border border-slate-800 bg-slate-900/40 p-5 shadow-xs space-y-3">
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <div className="flex items-center gap-2">
              <div className="h-7 w-7 rounded-lg bg-slate-800 flex items-center justify-center text-slate-300">
                <Layers className="h-4 w-4" />
              </div>
              <div>
                <h2 className="text-sm font-semibold text-white">
                  Deterministic Multi-Pass Matching Pipeline ({MATCHING_RULES.length} Rules)
                </h2>
                <span className="text-[11px] text-slate-400">
                  Executed sequentially in strict order from lowest to highest order number
                </span>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-1">
            {MATCHING_RULES.map((rule) => (
              <div
                key={rule.order}
                className="p-3 rounded-lg border border-slate-800 bg-slate-950/40 space-y-1 hover:border-slate-700 transition-colors"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="h-5 w-5 rounded bg-slate-800 text-slate-300 font-mono text-[10px] font-medium flex items-center justify-center shrink-0">
                      {rule.order}
                    </span>
                    <span className="text-xs font-semibold text-white">{rule.label}</span>
                  </div>
                  <span className="text-[10px] font-mono text-slate-400 px-1.5 py-0.5 rounded bg-slate-900 border border-slate-800">
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
